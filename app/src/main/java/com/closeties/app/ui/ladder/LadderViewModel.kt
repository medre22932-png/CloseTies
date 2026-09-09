package com.closeties.app.ui.ladder

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.closeties.app.data.local.AppDatabase
import com.closeties.app.data.repository.CallLogRepository
import com.closeties.app.data.repository.ContactRepository
import com.closeties.app.domain.model.TrackedContact
import com.closeties.app.domain.usecase.CalculateCooldownUseCase
import com.closeties.app.domain.usecase.RunLotteryDrawUseCase
import com.closeties.app.domain.usecase.SyncCallLogsUseCase
import com.closeties.app.worker.DailyLotteryWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LadderViewModel(
    private val contactRepository: ContactRepository,
    private val callLogRepository: CallLogRepository,
    private val calculateCooldownUseCase: CalculateCooldownUseCase,
    private val runLotteryDrawUseCase: RunLotteryDrawUseCase,
    private val syncCallLogsUseCase: SyncCallLogsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LadderUiState())
    val uiState: StateFlow<LadderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            contactRepository.getAllContactsFlow().collectLatest { contacts ->
                val now = System.currentTimeMillis()
                val totalStakes = contacts.sumOf { it.stakes }
                val eligibleCount = contacts.count { !it.isCoolingDown(now) }
                val cooldownCount = contacts.count { it.isCoolingDown(now) }

                _uiState.update { current ->
                    current.copy(
                        contacts = contacts,
                        totalStakes = totalStakes,
                        eligibleCount = eligibleCount,
                        cooldownCount = cooldownCount
                    )
                }
            }
        }
    }

    fun selectContactForMove(contact: TrackedContact?) {
        _uiState.update { it.copy(selectedContactForMove = contact) }
    }

    fun updateShelf(lookupKey: String, newShelf: Int) {
        viewModelScope.launch {
            contactRepository.updateShelf(lookupKey, newShelf)
            _uiState.update { it.copy(selectedContactForMove = null) }
        }
    }

    fun deleteContact(lookupKey: String) {
        viewModelScope.launch {
            contactRepository.deleteContact(lookupKey)
            _uiState.update { it.copy(selectedContactForMove = null) }
        }
    }

    fun clearCooldown(lookupKey: String) {
        viewModelScope.launch {
            contactRepository.clearCooldown(lookupKey)
        }
    }

    fun runTestDraw() {
        val contacts = _uiState.value.contacts
        val now = System.currentTimeMillis()
        val chosen = runLotteryDrawUseCase.selectContact(contacts, now = now)

        _uiState.update {
            it.copy(
                testDrawWinner = chosen,
                statusMessage = if (chosen == null) "No contacts are eligible for draw right now." else null
            )
        }
    }

    fun dismissTestDraw() {
        _uiState.update { it.copy(testDrawWinner = null) }
    }

    fun syncCallLogs() {
        viewModelScope.launch {
            val result = syncCallLogsUseCase.syncRecentCalls()
            val msg = if (result.matchedContacts.isNotEmpty()) {
                if (result.dailyTargetSatisfied) {
                    "Synced ${result.matchedContacts.size} call(s) and satisfied daily connection target!"
                } else {
                    "Synced ${result.matchedContacts.size} call(s) to contacts in cooldown. Daily target still open!"
                }
            } else {
                "No recent calls (>=45s) matched with tracked contacts."
            }
            _uiState.update { it.copy(statusMessage = msg) }
        }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun openSettings(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun updateNotificationTime(context: Context, hour: Int, minute: Int) {
        DailyLotteryWorker.scheduleDaily(context, hour, minute)
        _uiState.update {
            it.copy(
                notificationHour = hour,
                notificationMinute = minute,
                showSettingsDialog = false,
                statusMessage = "Daily nudge scheduled for %02d:%02d".format(hour, minute)
            )
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getInstance(context)
                    val contactRepo = ContactRepository(db.contactDao())
                    val callLogRepo = CallLogRepository(context.applicationContext)
                    val cooldownUseCase = CalculateCooldownUseCase()
                    val lotteryUseCase = RunLotteryDrawUseCase()
                    val syncUseCase = SyncCallLogsUseCase(contactRepo, callLogRepo, cooldownUseCase)
                    return LadderViewModel(
                        contactRepo,
                        callLogRepo,
                        cooldownUseCase,
                        lotteryUseCase,
                        syncUseCase
                    ) as T
                }
            }
    }
}
