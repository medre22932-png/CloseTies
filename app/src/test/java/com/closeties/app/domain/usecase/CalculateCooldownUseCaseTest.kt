package com.closeties.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateCooldownUseCaseTest {

    private lateinit var useCase: CalculateCooldownUseCase

    @Before
    fun setUp() {
        useCase = CalculateCooldownUseCase()
    }

    @Test
    fun `single contact in pool gets minimum 1 day cooldown`() {
        // S_total = 5, S_i = 5 -> floor(5 / 20) = 0 -> max(1, 0) = 1
        val days = useCase.calculateCooldownDays(totalStakes = 5, contactStakes = 5)
        assertEquals(1, days)
    }

    @Test
    fun `two contacts with high pool get correct cooldown days`() {
        // S_total = 100, S_i = 5 -> floor(100 / (4 * 5)) = floor(100 / 20) = 5
        val innerCircleDays = useCase.calculateCooldownDays(totalStakes = 100, contactStakes = 5)
        assertEquals(5, innerCircleDays)

        // S_total = 100, S_i = 1 -> floor(100 / (4 * 1)) = floor(100 / 4) = 25
        val acquaintanceDays = useCase.calculateCooldownDays(totalStakes = 100, contactStakes = 1)
        assertEquals(25, acquaintanceDays)
    }

    @Test
    fun `fractional values are floored properly`() {
        // S_total = 23, S_i = 3 -> floor(23 / 12) = floor(1.916) = 1
        val days = useCase.calculateCooldownDays(totalStakes = 23, contactStakes = 3)
        assertEquals(1, days)

        // S_total = 36, S_i = 3 -> floor(36 / 12) = 3
        val exactDays = useCase.calculateCooldownDays(totalStakes = 36, contactStakes = 3)
        assertEquals(3, exactDays)
    }

    @Test
    fun `expected frequency calculates round of S_total divided by S_i`() {
        // S_total = 15, S_i = 5 -> round(15 / 5) = 3
        val freq5 = useCase.calculateExpectedFrequencyDays(totalStakes = 15, contactStakes = 5)
        assertEquals(3, freq5)

        // S_total = 15, S_i = 2 -> round(15 / 2) = round(7.5) = 8
        val freq2 = useCase.calculateExpectedFrequencyDays(totalStakes = 15, contactStakes = 2)
        assertEquals(8, freq2)

        // S_total = 15, S_i = 1 -> 15
        val freq1 = useCase.calculateExpectedFrequencyDays(totalStakes = 15, contactStakes = 1)
        assertEquals(15, freq1)
    }

    @Test
    fun `cooldown timestamp calculation adds days in milliseconds`() {
        val now = 1_700_000_000_000L
        val totalStakes = 40
        val contactStakes = 2 // floor(40 / 8) = 5 days
        val timestamp = useCase.calculateCooldownUntilTimestamp(now, totalStakes, contactStakes)

        val expected = now + (5L * 86_400_000L)
        assertEquals(expected, timestamp)
    }
}
