package com.example.habittracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) {
            return
        }

        thread {
            val database = HabitDatabase.getInstance(context)
            val reminderHabits = database.habitDao().getHabitsWithRemindersEnabled()
            HabitReminderScheduler.createNotificationChannel(context)
            reminderHabits.forEach { habit ->
                HabitReminderScheduler.scheduleHabitReminder(context, habit)
            }
        }
    }
}
