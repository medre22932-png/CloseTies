package com.closeties.app.domain.usecase

import android.telephony.PhoneNumberUtils
import com.closeties.app.data.repository.CallLogRepository
import com.closeties.app.data.repository.ContactRepository
import com.closeties.app.domain.model.TrackedContact

data class SyncResult(
    val matchedContacts: List<TrackedContact>,
    val dailyTargetSatisfied: Boolean
)

class SyncCallLogsUseCase(
    private val contactRepository: ContactRepository,
    private val callLogRepository: CallLogRepository,
    private val calculateCooldownUseCase: CalculateCooldownUseCase,
    private val context: android.content.Context? = null
) {

    /**
     * Checks if any tracked contacts were called in the last 24 hours (duration >= 45s),
     * updates their cooldown and lastContactedTimestamp, and determines if daily target is satisfied.
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
        val matched = mutableListOf<TrackedContact>()

        for (call in recentCalls) {
            val matchingContact = allContacts.find { contact ->
                numbersMatch(contact.phoneNumber, call.number)
            }

            if (matchingContact != null && !matched.any { it.lookupKey == matchingContact.lookupKey }) {
                val callTime = call.timestamp
                val cooldownUntil = calculateCooldownUseCase.calculateCooldownUntilTimestamp(
                    currentTimestamp = callTime,
                    totalStakes = totalStakes,
                    contactStakes = matchingContact.stakes
                )

                contactRepository.setCooldown(
                    lookupKey = matchingContact.lookupKey,
                    cooldownUntil = cooldownUntil,
                    lastContacted = callTime
                )

                matched.add(
                    matchingContact.copy(
                        lastContactedTimestamp = callTime,
                        cooldownUntilTimestamp = cooldownUntil
                    )
                )
            }
        }

        val dailyTargetSatisfied = matched.isNotEmpty()
        return SyncResult(matchedContacts = matched, dailyTargetSatisfied = dailyTargetSatisfied)
    }

    private fun numbersMatch(num1: String, num2: String): Boolean {
        if (num1.isBlank() || num2.isBlank()) return false
        if (context != null) {
            if (PhoneNumberUtils.compare(context, num1, num2)) return true
        } else {
            @Suppress("DEPRECATION")
            if (PhoneNumberUtils.compare(num1, num2)) return true
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
