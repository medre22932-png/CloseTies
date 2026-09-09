package com.closeties.app.domain.model

import com.closeties.app.data.local.ContactEntity

data class TrackedContact(
    val lookupKey: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String?,
    val shelfLevel: Int, // 1 to 5
    val lastContactedTimestamp: Long = 0L,
    val cooldownUntilTimestamp: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastCallWasEligible: Boolean = false
) {
    val stakes: Int
        get() = shelfLevel.coerceIn(1, 5)

    fun isCoolingDown(now: Long = System.currentTimeMillis()): Boolean {
        return cooldownUntilTimestamp > now
    }

    fun remainingCooldownDays(now: Long = System.currentTimeMillis()): Int {
        if (!isCoolingDown(now)) return 0
        val diffMs = cooldownUntilTimestamp - now
        val days = (diffMs + 86399999L) / 86400000L
        return maxOf(1, days.toInt())
    }
}

fun ContactEntity.toDomain(): TrackedContact = TrackedContact(
    lookupKey = lookupKey,
    name = name,
    phoneNumber = phoneNumber,
    photoUri = photoUri,
    shelfLevel = shelfLevel,
    lastContactedTimestamp = lastContactedTimestamp,
    cooldownUntilTimestamp = cooldownUntilTimestamp,
    dateAdded = dateAdded,
    lastCallWasEligible = lastCallWasEligible
)

fun TrackedContact.toEntity(): ContactEntity = ContactEntity(
    lookupKey = lookupKey,
    name = name,
    phoneNumber = phoneNumber,
    photoUri = photoUri,
    shelfLevel = shelfLevel,
    lastContactedTimestamp = lastContactedTimestamp,
    cooldownUntilTimestamp = cooldownUntilTimestamp,
    dateAdded = dateAdded,
    lastCallWasEligible = lastCallWasEligible
)
