package com.closeties.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.closeties.app.data.local.AppDatabase
import com.closeties.app.data.repository.ContactRepository
import com.closeties.app.domain.usecase.CalculateCooldownUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PASS = "com.closeties.app.ACTION_PASS"
        const val ACTION_CALLED = "com.closeties.app.ACTION_CALLED"
        const val EXTRA_LOOKUP_KEY = "extra_lookup_key"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }

        val lookupKey = intent.getStringExtra(EXTRA_LOOKUP_KEY) ?: return

        when (intent.action) {
            ACTION_PASS -> {
                // Pass/Snooze: dismisses notification without triggering full cooldown
            }
            ACTION_CALLED -> {
                // Manually marked as called: apply cooldown immediately
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val repository = ContactRepository(db.contactDao())
                        val contacts = repository.getAllContacts()
                        val contact = contacts.find { it.lookupKey == lookupKey }
                        if (contact != null) {
                            val totalStakes = contacts.sumOf { it.stakes }
                            val calculator = CalculateCooldownUseCase()
                            val now = System.currentTimeMillis()
                            val cooldownUntil = calculator.calculateCooldownUntilTimestamp(
                                currentTimestamp = now,
                                totalStakes = totalStakes,
                                contactStakes = contact.stakes
                            )
                            repository.setCooldown(lookupKey, cooldownUntil, now)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
