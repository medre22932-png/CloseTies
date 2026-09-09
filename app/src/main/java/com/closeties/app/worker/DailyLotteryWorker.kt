package com.closeties.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.closeties.app.R
import com.closeties.app.data.local.AppDatabase
import com.closeties.app.data.repository.CallLogRepository
import com.closeties.app.data.repository.ContactRepository
import com.closeties.app.domain.model.TrackedContact
import com.closeties.app.domain.usecase.CalculateCooldownUseCase
import com.closeties.app.domain.usecase.RunLotteryDrawUseCase
import com.closeties.app.domain.usecase.SyncCallLogsUseCase
import com.closeties.app.receiver.NotificationActionReceiver
import com.closeties.app.ui.MainActivity
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyLotteryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "daily_lottery_worker"
        const val CHANNEL_ID = "luckyring_daily_nudge"
        const val NOTIFICATION_ID = 1001

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.channel_name)
                val descriptionText = context.getString(R.string.channel_description)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun scheduleDaily(context: Context, targetHour: Int = 10, targetMinute: Int = 0) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val initialDelayMs = target.timeInMillis - now.timeInMillis

            val workRequest = PeriodicWorkRequestBuilder<DailyLotteryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.NONE)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(context)
        val contactRepo = ContactRepository(db.contactDao())
        val callLogRepo = CallLogRepository(context)
        val cooldownUseCase = CalculateCooldownUseCase()
        val syncUseCase = SyncCallLogsUseCase(contactRepo, callLogRepo, cooldownUseCase)
        val lotteryUseCase = RunLotteryDrawUseCase()

        val now = System.currentTimeMillis()

        // 1. Sync Call Logs to check if tracked contacts were called in last 24h
        val syncResult = syncUseCase.syncRecentCalls(now = now)
        if (syncResult.dailyTargetSatisfied) {
            // Already connected today! Silent completion.
            return Result.success()
        }

        // 2. Query all contacts and perform weighted lottery draw among eligible contacts
        val allContacts = contactRepo.getAllContacts()
        if (allContacts.isEmpty()) {
            return Result.success()
        }

        val chosen = lotteryUseCase.selectContact(allContacts, now = now) ?: return Result.success()

        // 3. Post rich notification
        showNudgeNotification(chosen)

        return Result.success()
    }

    private fun showNudgeNotification(contact: TrackedContact) {
        ensureNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 1: Call Now (ACTION_DIAL with tel: uri - no CALL_PHONE permission needed!)
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${contact.phoneNumber.trim()}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dialPendingIntent = PendingIntent.getActivity(
            context,
            1,
            dialIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 2: Pass / Snooze
        val passIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PASS
            putExtra(NotificationActionReceiver.EXTRA_LOOKUP_KEY, contact.lookupKey)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        }
        val passPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            passIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = context.getString(R.string.notification_title)
        val body = context.getString(R.string.notification_body, contact.name)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_call,
                context.getString(R.string.action_call_now),
                dialPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_pass),
                passPendingIntent
            )
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
