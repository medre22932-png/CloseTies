package com.closeties.app

import android.app.Application
import com.closeties.app.data.local.AppDatabase
import com.closeties.app.worker.DailyLotteryWorker

class CloseTiesApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Room DB eagerly
        AppDatabase.getInstance(this)

        // Ensure notification channel exists
        DailyLotteryWorker.ensureNotificationChannel(this)

        // Schedule daily background lottery (default 10:00 AM)
        DailyLotteryWorker.scheduleDaily(this, targetHour = 10, targetMinute = 0)
    }
}
