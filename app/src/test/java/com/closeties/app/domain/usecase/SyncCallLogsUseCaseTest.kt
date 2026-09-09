package com.closeties.app.domain.usecase

import com.closeties.app.data.local.ContactDao
import com.closeties.app.data.local.ContactEntity
import com.closeties.app.data.repository.CallLogRepository
import com.closeties.app.data.repository.CallRecord
import com.closeties.app.data.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncCallLogsUseCaseTest {

    private lateinit var fakeContactDao: FakeContactDao
    private lateinit var contactRepository: ContactRepository
    private lateinit var fakeCallLogRepository: FakeCallLogRepository
    private lateinit var cooldownUseCase: CalculateCooldownUseCase
    private lateinit var syncCallLogsUseCase: SyncCallLogsUseCase

    @Before
    fun setUp() {
        fakeContactDao = FakeContactDao()
        contactRepository = ContactRepository(fakeContactDao)
        fakeCallLogRepository = FakeCallLogRepository()
        cooldownUseCase = CalculateCooldownUseCase()
        syncCallLogsUseCase = SyncCallLogsUseCase(
            contactRepository = contactRepository,
            callLogRepository = fakeCallLogRepository,
            calculateCooldownUseCase = cooldownUseCase,
            context = null
        )
    }

    @Test
    fun `call to eligible contact satisfies daily target and sets cooldown`() = runTest {
        val now = 1_000_000_000L
        val callTime = now - 3600_000L // 1 hour ago

        fakeContactDao.contacts["1"] = ContactEntity(
            lookupKey = "1",
            name = "Alice",
            phoneNumber = "+1234567890",
            photoUri = null,
            shelfLevel = 5,
            lastContactedTimestamp = 0L,
            cooldownUntilTimestamp = 0L,
            lastCallWasEligible = false
        )

        fakeCallLogRepository.calls = listOf(
            CallRecord(
                number = "+1234567890",
                timestamp = callTime,
                durationSeconds = 60L,
                type = 2
            )
        )

        val result = syncCallLogsUseCase.syncRecentCalls(now = now)

        assertTrue(result.dailyTargetSatisfied)
        assertEquals(1, result.eligibleContactsCount)
        assertEquals(1, result.matchedContacts.size)

        val updatedContact = fakeContactDao.contacts["1"]!!
        assertEquals(callTime, updatedContact.lastContactedTimestamp)
        assertTrue(updatedContact.cooldownUntilTimestamp > callTime)
        assertTrue(updatedContact.lastCallWasEligible)
    }

    @Test
    fun `call to cooldowned contact refreshes cooldown but does NOT satisfy daily target`() = runTest {
        val now = 1_000_000_000L
        val callTime = now - 3600_000L // 1 hour ago
        val existingCooldown = callTime + 86_400_000L // Cooldown was active at callTime!

        fakeContactDao.contacts["1"] = ContactEntity(
            lookupKey = "1",
            name = "Alice",
            phoneNumber = "+1234567890",
            photoUri = null,
            shelfLevel = 5,
            lastContactedTimestamp = callTime - 86_400_000L,
            cooldownUntilTimestamp = existingCooldown,
            lastCallWasEligible = true
        )

        fakeCallLogRepository.calls = listOf(
            CallRecord(
                number = "+1234567890",
                timestamp = callTime,
                durationSeconds = 90L,
                type = 2
            )
        )

        val result = syncCallLogsUseCase.syncRecentCalls(now = now)

        assertFalse("Calling a contact in cooldown should NOT satisfy today's connection", result.dailyTargetSatisfied)
        assertEquals(0, result.eligibleContactsCount)
        assertEquals(1, result.matchedContacts.size)

        val updatedContact = fakeContactDao.contacts["1"]!!
        assertEquals(callTime, updatedContact.lastContactedTimestamp)
        // Cooldown was refreshed starting from new callTime
        assertTrue(updatedContact.cooldownUntilTimestamp > callTime)
        assertFalse(updatedContact.lastCallWasEligible)
    }

    @Test
    fun `calling both cooldowned and eligible contacts satisfies daily target`() = runTest {
        val now = 1_000_000_000L
        val callTime1 = now - 7200_000L // 2 hours ago
        val callTime2 = now - 3600_000L // 1 hour ago

        // Contact 1: in cooldown
        fakeContactDao.contacts["1"] = ContactEntity(
            lookupKey = "1",
            name = "Alice",
            phoneNumber = "+1111111111",
            photoUri = null,
            shelfLevel = 5,
            cooldownUntilTimestamp = now + 100000L,
            lastCallWasEligible = false
        )

        // Contact 2: eligible
        fakeContactDao.contacts["2"] = ContactEntity(
            lookupKey = "2",
            name = "Bob",
            phoneNumber = "+2222222222",
            photoUri = null,
            shelfLevel = 3,
            cooldownUntilTimestamp = 0L,
            lastCallWasEligible = false
        )

        fakeCallLogRepository.calls = listOf(
            CallRecord("+1111111111", callTime1, 60L, 2),
            CallRecord("+2222222222", callTime2, 60L, 2)
        )

        val result = syncCallLogsUseCase.syncRecentCalls(now = now)

        assertTrue(result.dailyTargetSatisfied)
        assertEquals(1, result.eligibleContactsCount)
        assertEquals(2, result.matchedContacts.size)
    }

    @Test
    fun `subsequent sync preserves target satisfaction state for already synced calls`() = runTest {
        val now = 1_000_000_000L
        val callTime = now - 3600_000L

        fakeContactDao.contacts["1"] = ContactEntity(
            lookupKey = "1",
            name = "Alice",
            phoneNumber = "+1234567890",
            photoUri = null,
            shelfLevel = 5,
            cooldownUntilTimestamp = 0L,
            lastCallWasEligible = false
        )

        fakeCallLogRepository.calls = listOf(
            CallRecord("+1234567890", callTime, 60L, 2)
        )

        // First sync
        val result1 = syncCallLogsUseCase.syncRecentCalls(now = now)
        assertTrue(result1.dailyTargetSatisfied)

        // Second sync (e.g. daily lottery worker running later in the same day)
        val result2 = syncCallLogsUseCase.syncRecentCalls(now = now + 1800_000L)
        assertTrue("Subsequent sync must recognize that today's connection target was met", result2.dailyTargetSatisfied)
    }

    @Test
    fun `subsequent sync preserves unsatisfied state when only cooldowned contacts were called`() = runTest {
        val now = 1_000_000_000L
        val callTime = now - 3600_000L

        // Contact was in cooldown at callTime
        fakeContactDao.contacts["1"] = ContactEntity(
            lookupKey = "1",
            name = "Alice",
            phoneNumber = "+1234567890",
            photoUri = null,
            shelfLevel = 5,
            cooldownUntilTimestamp = callTime + 100_000_000L,
            lastCallWasEligible = false
        )

        fakeCallLogRepository.calls = listOf(
            CallRecord("+1234567890", callTime, 60L, 2)
        )

        // First sync: cooldowned call
        val result1 = syncCallLogsUseCase.syncRecentCalls(now = now)
        assertFalse(result1.dailyTargetSatisfied)

        // Second sync
        val result2 = syncCallLogsUseCase.syncRecentCalls(now = now + 1800_000L)
        assertFalse("Subsequent sync must NOT mark target satisfied if only cooldowned contact was called", result2.dailyTargetSatisfied)
    }

    // In-memory fake ContactDao
    private class FakeContactDao : ContactDao {
        val contacts = mutableMapOf<String, ContactEntity>()

        override fun getAllContactsFlow(): Flow<List<ContactEntity>> =
            flowOf(contacts.values.toList())

        override suspend fun getAllContacts(): List<ContactEntity> =
            contacts.values.toList()

        override suspend fun getEligibleContacts(now: Long): List<ContactEntity> =
            contacts.values.filter { it.cooldownUntilTimestamp <= now }

        override suspend fun getContactByLookupKey(lookupKey: String): ContactEntity? =
            contacts[lookupKey]

        override suspend fun insertContact(contact: ContactEntity) {
            contacts[contact.lookupKey] = contact
        }

        override suspend fun insertContacts(contacts: List<ContactEntity>) {
            contacts.forEach { insertContact(it) }
        }

        override suspend fun updateContact(contact: ContactEntity) {
            contacts[contact.lookupKey] = contact
        }

        override suspend fun deleteContact(contact: ContactEntity) {
            contacts.remove(contact.lookupKey)
        }

        override suspend fun deleteContactByLookupKey(lookupKey: String) {
            contacts.remove(lookupKey)
        }

        override suspend fun updateShelf(lookupKey: String, newShelf: Int) {
            contacts[lookupKey]?.let {
                contacts[lookupKey] = it.copy(shelfLevel = newShelf)
            }
        }

        override suspend fun updateCooldown(
            lookupKey: String,
            cooldownUntil: Long,
            lastContacted: Long,
            lastCallWasEligible: Boolean
        ) {
            contacts[lookupKey]?.let {
                contacts[lookupKey] = it.copy(
                    cooldownUntilTimestamp = cooldownUntil,
                    lastContactedTimestamp = lastContacted,
                    lastCallWasEligible = lastCallWasEligible
                )
            }
        }

        override suspend fun updateLastContacted(lookupKey: String, timestamp: Long) {
            contacts[lookupKey]?.let {
                contacts[lookupKey] = it.copy(lastContactedTimestamp = timestamp)
            }
        }

        override suspend fun clearCooldown(lookupKey: String) {
            contacts[lookupKey]?.let {
                contacts[lookupKey] = it.copy(cooldownUntilTimestamp = 0L)
            }
        }
    }

    // In-memory fake CallLogRepository
    private class FakeCallLogRepository : CallLogRepository(null) {
        var hasPermission: Boolean = true
        var calls: List<CallRecord> = emptyList()

        override fun hasCallLogPermission(): Boolean = hasPermission

        override fun getRecentCalls(sinceTimestamp: Long, minDurationSeconds: Long): List<CallRecord> {
            if (!hasPermission) return emptyList()
            return calls.filter { it.timestamp >= sinceTimestamp && it.durationSeconds >= minDurationSeconds }
        }
    }
}
