package com.closeties.app.domain.usecase

import com.closeties.app.domain.model.TrackedContact
import kotlin.random.Random

class RunLotteryDrawUseCase {

    /**
     * Executes the weighted random lottery selection among eligible contacts.
     *
     * @param contacts List of all contacts in the database.
     * @param now Current timestamp in milliseconds.
     * @param random Random generator instance (default Random.Default).
     * @return Selected TrackedContact or null if no eligible contacts exist.
     */
    fun selectContact(
        contacts: List<TrackedContact>,
        now: Long = System.currentTimeMillis(),
        random: Random = Random.Default
    ): TrackedContact? {
        val eligible = contacts.filter { it.cooldownUntilTimestamp <= now }
        if (eligible.isEmpty()) {
            return null
        }

        val totalStakes = eligible.sumOf { it.stakes }
        if (totalStakes <= 0) {
            return eligible.firstOrNull()
        }

        val randomThreshold = random.nextDouble(0.0, totalStakes.toDouble())
        var cumulativeSum = 0.0

        for (contact in eligible) {
            cumulativeSum += contact.stakes
            if (cumulativeSum > randomThreshold) {
                return contact
            }
        }

        return eligible.lastOrNull()
    }
}
