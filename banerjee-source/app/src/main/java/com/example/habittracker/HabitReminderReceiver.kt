package com.example.habittracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import kotlin.concurrent.thread

class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val habitId = intent.getLongExtra(HabitReminderScheduler.reminderExtraHabitId, -1L)
        if (habitId <= 0L) {
            pendingResult.finish()
            return
        }

        thread {
            try {
                val database = HabitDatabase.getInstance(context)
                val habit = database.habitDao().getHabitById(habitId)
                if (habit == null) {
                    return@thread
                }

                if (!habit.remindersEnabled) {
                    return@thread
                }

                HabitReminderScheduler.scheduleHabitReminder(context, habit)

                val today = LocalDate.now().toEpochDay()
                val completedToday = database.habitDao()
                    .getEntriesForHabitFromDay(habitId, today)
                    .any { it.day == today }
                if (completedToday) {
                    return@thread
                }

                if (
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@thread
                }

                HabitReminderScheduler.createNotificationChannel(context)
                val notification = NotificationCompat.Builder(context, HabitReminderScheduler.notificationChannelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.habit_reminder_title))
                    .setContentText(context.getString(R.string.habit_reminder_text, habit.name))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()

                NotificationManagerCompat.from(context).notify(habitId.toInt(), notification)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
