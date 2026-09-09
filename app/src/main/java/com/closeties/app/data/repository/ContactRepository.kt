package com.closeties.app.data.repository

import com.closeties.app.data.local.ContactDao
import com.closeties.app.domain.model.TrackedContact
import com.closeties.app.domain.model.toDomain
import com.closeties.app.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContactRepository(private val contactDao: ContactDao) {

    fun getAllContactsFlow(): Flow<List<TrackedContact>> {
        return contactDao.getAllContactsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getAllContacts(): List<TrackedContact> {
        return contactDao.getAllContacts().map { it.toDomain() }
    }

    suspend fun getEligibleContacts(now: Long = System.currentTimeMillis()): List<TrackedContact> {
        return contactDao.getEligibleContacts(now).map { it.toDomain() }
    }

    suspend fun getContact(lookupKey: String): TrackedContact? {
        return contactDao.getContactByLookupKey(lookupKey)?.toDomain()
    }

    suspend fun addOrUpdateContact(contact: TrackedContact) {
        contactDao.insertContact(contact.toEntity())
    }

    suspend fun deleteContact(lookupKey: String) {
        contactDao.deleteContactByLookupKey(lookupKey)
    }

    suspend fun updateShelf(lookupKey: String, newShelf: Int) {
        contactDao.updateShelf(lookupKey, newShelf.coerceIn(1, 5))
    }

    suspend fun setCooldown(
        lookupKey: String,
        cooldownUntil: Long,
        lastContacted: Long,
        lastCallWasEligible: Boolean = true
    ) {
        contactDao.updateCooldown(lookupKey, cooldownUntil, lastContacted, lastCallWasEligible)
    }

    suspend fun updateLastContacted(lookupKey: String, timestamp: Long) {
        contactDao.updateLastContacted(lookupKey, timestamp)
    }

    suspend fun clearCooldown(lookupKey: String) {
        contactDao.clearCooldown(lookupKey)
    }
}
