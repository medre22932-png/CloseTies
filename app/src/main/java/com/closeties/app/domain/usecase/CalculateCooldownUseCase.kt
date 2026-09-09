package com.closeties.app.domain.usecase

import kotlin.math.floor
import kotlin.math.roundToInt

class CalculateCooldownUseCase {

    companion object {
        const val MS_PER_DAY = 86_400_000L
    }

    /**
     * Calculates the cooldown duration in days according to the specification:
     * Cooldown Days = max(1, floor(S_total / (4 * S_i)))
     *
     * @param totalStakes Total sum of stakes (S_total). Must be >= 1.
     * @param contactStakes Stakes of the contact (S_i). Must be >= 1.
     * @return Cooldown in whole days (at least 1).
     */
    fun calculateCooldownDays(totalStakes: Int, contactStakes: Int): Int {
        val safeTotal = maxOf(1, totalStakes)
        val safeContactStakes = maxOf(1, contactStakes)
        val rawFraction = safeTotal.toDouble() / (4.0 * safeContactStakes.toDouble())
        val floored = floor(rawFraction).toInt()
        return maxOf(1, floored)
    }

    /**
     * Computes the timestamp until which the contact is in cooldown.
     */
    fun calculateCooldownUntilTimestamp(
        currentTimestamp: Long,
        totalStakes: Int,
        contactStakes: Int
    ): Long {
        val days = calculateCooldownDays(totalStakes, contactStakes)
        return currentTimestamp + (days * MS_PER_DAY)
    }

    /**
     * Computes the expected frequency in days:
     * Expected Frequency = round(S_total / S_i)
     */
    fun calculateExpectedFrequencyDays(totalStakes: Int, contactStakes: Int): Int {
        val safeTotal = maxOf(1, totalStakes)
        val safeContactStakes = maxOf(1, contactStakes)
        val raw = safeTotal.toDouble() / safeContactStakes.toDouble()
        val rounded = raw.roundToInt()
        return maxOf(1, rounded)
    }

    /**
     * Returns a human-friendly expected frequency string for the contact card.
     */
    fun formatExpectedFrequency(totalStakes: Int, contactStakes: Int): String {
        val days = calculateExpectedFrequencyDays(totalStakes, contactStakes)
        return if (days == 1) {
            "Expected: ~1 call every day"
        } else {
            "Expected: ~1 call every $days days"
        }
    }
}
