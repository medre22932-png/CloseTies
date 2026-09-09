package com.closeties.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM tracked_contacts ORDER BY shelfLevel DESC, name ASC")
    fun getAllContactsFlow(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM tracked_contacts ORDER BY shelfLevel DESC, name ASC")
    suspend fun getAllContacts(): List<ContactEntity>

    @Query("SELECT * FROM tracked_contacts WHERE cooldownUntilTimestamp <= :now ORDER BY shelfLevel DESC")
    suspend fun getEligibleContacts(now: Long): List<ContactEntity>

    @Query("SELECT * FROM tracked_contacts WHERE lookupKey = :lookupKey LIMIT 1")
    suspend fun getContactByLookupKey(lookupKey: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM tracked_contacts WHERE lookupKey = :lookupKey")
    suspend fun deleteContactByLookupKey(lookupKey: String)

    @Query("UPDATE tracked_contacts SET shelfLevel = :newShelf WHERE lookupKey = :lookupKey")
    suspend fun updateShelf(lookupKey: String, newShelf: Int)

    @Query("UPDATE tracked_contacts SET cooldownUntilTimestamp = :cooldownUntil, lastContactedTimestamp = :lastContacted WHERE lookupKey = :lookupKey")
    suspend fun updateCooldown(lookupKey: String, cooldownUntil: Long, lastContacted: Long)

    @Query("UPDATE tracked_contacts SET lastContactedTimestamp = :timestamp WHERE lookupKey = :lookupKey")
    suspend fun updateLastContacted(lookupKey: String, timestamp: Long)

    @Query("UPDATE tracked_contacts SET cooldownUntilTimestamp = 0 WHERE lookupKey = :lookupKey")
    suspend fun clearCooldown(lookupKey: String)
}
