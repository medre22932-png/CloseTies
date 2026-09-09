package com.closeties.app.domain.usecase

import com.closeties.app.domain.model.TrackedContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class RunLotteryDrawUseCaseTest {

    private lateinit var useCase: RunLotteryDrawUseCase

    @Before
    fun setUp() {
        useCase = RunLotteryDrawUseCase()
    }

    @Test
    fun `empty contact list returns null`() {
        val result = useCase.selectContact(emptyList())
        assertNull(result)
    }

    @Test
    fun `contacts in cooldown are excluded`() {
        val now = 1000L
        val contact1 = TrackedContact(
            lookupKey = "1",
            name = "Alice",
            phoneNumber = "12345",
            photoUri = null,
            shelfLevel = 5,
            cooldownUntilTimestamp = 2000L // In cooldown!
        )
        val contact2 = TrackedContact(
            lookupKey = "2",
            name = "Bob",
            phoneNumber = "67890",
            photoUri = null,
            shelfLevel = 1,
            cooldownUntilTimestamp = 500L // Eligible
        )

        val result = useCase.selectContact(listOf(contact1, contact2), now = now)
        assertNotNull(result)
        assertEquals("Bob", result?.name)
    }

    @Test
    fun `all contacts in cooldown returns null`() {
        val now = 1000L
        val contact1 = TrackedContact(
            lookupKey = "1",
            name = "Alice",
            phoneNumber = "12345",
            photoUri = null,
            shelfLevel = 5,
            cooldownUntilTimestamp = 2000L
        )
        val contact2 = TrackedContact(
            lookupKey = "2",
            name = "Bob",
            phoneNumber = "67890",
            photoUri = null,
            shelfLevel = 4,
            cooldownUntilTimestamp = 3000L
        )

        val result = useCase.selectContact(listOf(contact1, contact2), now = now)
        assertNull(result)
    }

    @Test
    fun `weighted selection favors higher stakes contacts over large sample`() {
        val now = 1000L
        val alice = TrackedContact("1", "Alice", "111", null, shelfLevel = 5, cooldownUntilTimestamp = 0L)
        val bob = TrackedContact("2", "Bob", "222", null, shelfLevel = 1, cooldownUntilTimestamp = 0L)
        val contacts = listOf(alice, bob)

        var aliceWins = 0
        var bobWins = 0
        val iterations = 5000

        for (i in 0 until iterations) {
            val winner = useCase.selectContact(contacts, now = now, random = Random(i))
            if (winner?.lookupKey == "1") aliceWins++
            else if (winner?.lookupKey == "2") bobWins++
        }

        // Alice (5 stakes) vs Bob (1 stake): Alice should win ~83.3% of the time, Bob ~16.7%
        val aliceRatio = aliceWins.toDouble() / iterations.toDouble()
        assertTrue("Alice ratio was $aliceRatio, expected around 0.83", aliceRatio in 0.78..0.88)
    }
}
