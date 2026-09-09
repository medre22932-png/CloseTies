package com.closeties.app.ui.picker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.closeties.app.data.local.AppDatabase
import com.closeties.app.data.repository.ContactRepository
import com.closeties.app.domain.model.TrackedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceContact(
    val lookupKey: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String?
)

data class ContactPickerUiState(
    val contacts: List<DeviceContact> = emptyList(),
    val filteredContacts: List<DeviceContact> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedShelf: Int = 3,
    val isContactAdded: Boolean = false,
    val hasPermission: Boolean = false
)

class ContactPickerViewModel(
    private val context: Context,
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactPickerUiState())
    val uiState: StateFlow<ContactPickerUiState> = _uiState.asStateFlow()

    init {
        checkPermissionAndLoad()
    }

    fun checkPermissionAndLoad() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) {
            loadDeviceContacts()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.contacts
            } else {
                state.contacts.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.phoneNumber.contains(query, ignoreCase = true)
                }
            }
            state.copy(searchQuery = query, filteredContacts = filtered)
        }
    }

    fun selectShelf(shelf: Int) {
        _uiState.update { it.copy(selectedShelf = shelf.coerceIn(1, 5)) }
    }

    fun addContact(deviceContact: DeviceContact, shelfLevel: Int = _uiState.value.selectedShelf) {
        viewModelScope.launch {
            val contact = TrackedContact(
                lookupKey = deviceContact.lookupKey,
                name = deviceContact.name,
                phoneNumber = deviceContact.phoneNumber,
                photoUri = deviceContact.photoUri,
                shelfLevel = shelfLevel
            )
            contactRepository.addOrUpdateContact(contact)
            _uiState.update { it.copy(isContactAdded = true) }
        }
    }

    fun resetAddedState() {
        _uiState.update { it.copy(isContactAdded = false) }
    }

    private fun loadDeviceContacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val loaded = withContext(Dispatchers.IO) {
                queryContacts()
            }
            _uiState.update { state ->
                val filtered = if (state.searchQuery.isBlank()) loaded else {
                    loaded.filter {
                        it.name.contains(state.searchQuery, ignoreCase = true) ||
                        it.phoneNumber.contains(state.searchQuery, ignoreCase = true)
                    }
                }
                state.copy(
                    contacts = loaded,
                    filteredContacts = filtered,
                    isLoading = false
                )
            }
        }
    }

    private fun queryContacts(): List<DeviceContact> {
        val list = mutableListOf<DeviceContact>()
        val seenKeys = mutableSetOf<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val keyIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val key = if (keyIdx != -1) cursor.getString(keyIdx).orEmpty() else ""
                    val name = if (nameIdx != -1) cursor.getString(nameIdx).orEmpty() else ""
                    val number = if (numIdx != -1) cursor.getString(numIdx).orEmpty() else ""
                    val photo = if (photoIdx != -1) cursor.getString(photoIdx) else null

                    if (name.isNotBlank() && number.isNotBlank() && seenKeys.add(key.ifBlank { number })) {
                        list.add(
                            DeviceContact(
                                lookupKey = key.ifBlank { number },
                                name = name,
                                phoneNumber = number,
                                photoUri = photo
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Permission or resolver error
        }

        return list
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getInstance(context)
                    val contactRepo = ContactRepository(db.contactDao())
                    return ContactPickerViewModel(context.applicationContext, contactRepo) as T
                }
            }
    }
}
