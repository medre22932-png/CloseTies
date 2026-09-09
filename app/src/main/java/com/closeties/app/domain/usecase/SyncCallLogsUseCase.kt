package com.closeties.app.domain.usecase

import android.telephony.PhoneNumberUtils
import com.closeties.app.data.repository.CallLogRepository
import com.closeties.app.data.repository.ContactRepository
import com.closeties.app.domain.model.TrackedContact

data class SyncResult(
    val matchedContacts: List<TrackedContact>,
    val dailyTargetSatisfied: Boolean,
    val eligibleContactsCount: Int = 0
)

class SyncCallLogsUseCase(
    private val contactRepository: ContactRepository,
    private val callLogRepository: CallLogRepository,
    private val calculateCooldownUseCase: CalculateCooldownUseCase,
    private val context: android.content.Context? = null
) {

    private data class ContactSyncState(
        val contact: TrackedContact,
        var currentCooldownUntil: Long,
        var currentLastContacted: Long,
        var lastCallWasEligible: Boolean,
        var isUpdated: Boolean = false
    )

    /**
     * Checks if any tracked contacts were called in the last 24 hours (duration >= 45s),
     * updates their cooldown and lastContactedTimestamp, and determines if daily target is satisfied.
     *
     * IMPORTANT: Calls to contacts who were already in cooldown at the time of the call do NOT
     * satisfy the daily target (to encourage calling other relatives). However, their cooldown
     * is refreshed to reflect the new call.
     */
    suspend fun syncRecentCalls(
        now: Long = System.currentTimeMillis(),
        lookbackMs: Long = 86_400_000L, // 24 hours
        minDurationSeconds: Long = 45L
    ): SyncResult {
        if (!callLogRepository.hasCallLogPermission()) {
            return SyncResult(matchedContacts = emptyList(), dailyTargetSatisfied = false)
        }

        val sinceTimestamp = now - lookbackMs
        val recentCalls = callLogRepository.getRecentCalls(
            sinceTimestamp = sinceTimestamp,
            minDurationSeconds = minDurationSeconds
        )

        if (recentCalls.isEmpty()) {
            return SyncResult(matchedContacts = emptyList(), dailyTargetSatisfied = false)
        }

        val allContacts = contactRepository.getAllContacts()
        if (allContacts.isEmpty()) {
            return SyncResult(matchedContacts = emptyList(), dailyTargetSatisfied = false)
        }

        val totalStakes = allContacts.sumOf { it.stakes }

        // Track state for each contact during this sync pass
        val contactStates = allContacts.associate { contact ->
            contact.lookupKey to ContactSyncState(
                contact = contact,
                currentCooldownUntil = contact.cooldownUntilTimestamp,
                currentLastContacted = contact.lastContactedTimestamp,
                lastCallWasEligible = contact.lastCallWasEligible
            )
        }.toMutableMap()

        // Sort chronologically so we evaluate each call against the contact's cooldown at that exact time
        val chronologicalCalls = recentCalls.sortedBy { it.timestamp }
        var hasNewEligibleCall = false

        for (call in chronologicalCalls) {
            val matchingContact = allContacts.find { contact ->
                numbersMatch(contact.phoneNumber, call.number)
            } ?: continue

            val state = contactStates[matchingContact.lookupKey] ?: continue

            if (call.timestamp <= state.currentLastContacted) {
                // This call was already recorded in a previous sync run
                continue
            }

            val callTime = call.timestamp
            // Was the contact already in cooldown when this call occurred?
            val wasInCooldown = state.currentCooldownUntil > callTime

            if (!wasInCooldown) {
                hasNewEligibleCall = true
                state.lastCallWasEligible = true
            } else {
                // Cooldown call: refresh cooldown, but only mark eligible if an earlier call today was already eligible
                if (!state.isUpdated) {
                    state.lastCallWasEligible = false
                }
            }

            val newCooldownUntil = calculateCooldownUseCase.calculateCooldownUntilTimestamp(
                currentTimestamp = callTime,
                totalStakes = totalStakes,
                contactStakes = matchingContact.stakes
            )

            state.currentCooldownUntil = newCooldownUntil
            state.currentLastContacted = callTime
            state.isUpdated = true
        }

        val matched = mutableListOf<TrackedContact>()
        var eligibleContactsCount = 0

        for ((_, state) in contactStates) {
            if (state.isUpdated) {
                contactRepository.setCooldown(
                    lookupKey = state.contact.lookupKey,
                    cooldownUntil = state.currentCooldownUntil,
                    lastContacted = state.currentLastContacted,
                    lastCallWasEligible = state.lastCallWasEligible
                )
            }

            // Include in matched if contacted in the lookback window
            if (state.currentLastContacted >= sinceTimestamp && state.currentLastContacted > 0L) {
                val updatedContact = state.contact.copy(
                    lastContactedTimestamp = state.currentLastContacted,
                    cooldownUntilTimestamp = state.currentCooldownUntil,
                    lastCallWasEligible = state.lastCallWasEligible
                )
                matched.add(updatedContact)
                if (state.lastCallWasEligible) {
                    eligibleContactsCount++
                }
            }
        }

        val dailyTargetSatisfied = hasNewEligibleCall || eligibleContactsCount > 0
        return SyncResult(
            matchedContacts = matched,
            dailyTargetSatisfied = dailyTargetSatisfied,
            eligibleContactsCount = eligibleContactsCount
        )
    }

    private fun numbersMatch(num1: String, num2: String): Boolean {
        if (num1.isBlank() || num2.isBlank()) return false
        try {
            if (context != null) {
                if (PhoneNumberUtils.compare(context, num1, num2)) return true
            } else {
                @Suppress("DEPRECATION")
                if (PhoneNumberUtils.compare(num1, num2)) return true
            }
        } catch (_: Exception) {
            // Fallback for JVM unit tests where android.jar is not mocked
        }

        // Fallback: match by last 7-9 digits (stripping spaces, dashes, parentheses)
        val clean1 = num1.filter { it.isDigit() }
        val clean2 = num2.filter { it.isDigit() }
        if (clean1 == clean2) return true

        val minLen = minOf(clean1.length, clean2.length)
        if (minLen >= 7) {
            val suffix1 = clean1.takeLast(7)
            val suffix2 = clean2.takeLast(7)
            return suffix1 == suffix2
        }

        return false
    }
}
