package com.closeties.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

data class CallRecord(
    val number: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val type: Int
)

open class CallLogRepository(private val context: Context?) {

    open fun hasCallLogPermission(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries calls since [sinceTimestamp] with duration >= [minDurationSeconds] (default 45s).
     */
    open fun getRecentCalls(
        sinceTimestamp: Long,
        minDurationSeconds: Long = 45L
    ): List<CallRecord> {
        val ctx = context ?: return emptyList()
        if (!hasCallLogPermission()) {
            return emptyList()
        }

        val records = mutableListOf<CallRecord>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        val selection = "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DURATION} >= ?"
        val selectionArgs = arrayOf(
            sinceTimestamp.toString(),
            minDurationSeconds.toString()
        )
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)

                while (cursor.moveToNext()) {
                    val number = if (numberIndex != -1) cursor.getString(numberIndex).orEmpty() else ""
                    val date = if (dateIndex != -1) cursor.getLong(dateIndex) else 0L
                    val duration = if (durationIndex != -1) cursor.getLong(durationIndex) else 0L
                    val type = if (typeIndex != -1) cursor.getInt(typeIndex) else 0

                    if (number.isNotBlank()) {
                        records.add(CallRecord(number, date, duration, type))
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked concurrently
            return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }

        return records
    }
}
