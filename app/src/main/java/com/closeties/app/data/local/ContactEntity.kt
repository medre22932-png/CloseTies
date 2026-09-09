package com.closeties.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_contacts")
data class ContactEntity(
    @PrimaryKey val lookupKey: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String?,
    val shelfLevel: Int, // 1 to 5 (serves as stakes)
    val lastContactedTimestamp: Long = 0L,
    val cooldownUntilTimestamp: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis()
)
