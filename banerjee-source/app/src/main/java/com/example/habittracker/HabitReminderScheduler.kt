package com.example.habittracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

object HabitReminderScheduler {
    const val notificationChannelId = "habit_reminders"
    const val reminderExtraHabitId = "reminder_habit_id"

    fun scheduleHabitReminder(context: Context, habit: HabitEntity, allowSameDayCatchUp: Boolean = false) {
        if (!habit.remindersEnabled) {
            cancelHabitReminder(context, habit.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getReminderPendingIntent(context, habit.id)
        val triggerAtMillis = getNextTriggerAtMillis(
            habit.reminderHour,
            habit.reminderMinute,
            allowSameDayCatchUp
        )
        alarmManager.cancel(pendingIntent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    fun cancelHabitReminder(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getReminderPendingIntent(context, habitId))
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            notificationChannelId,
            context.getString(R.string.habit_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.habit_reminder_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun getReminderPendingIntent(context: Context, habitId: Long): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            putExtra(reminderExtraHabitId, habitId)
        }
        return PendingIntent.getBroadcast(
            context,
            habitId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun getNextTriggerAtMillis(hour: Int, minute: Int, allowSameDayCatchUp: Boolean = false): Long {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        var triggerTime = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        if (!triggerTime.isAfter(now)) {
            if (allowSameDayCatchUp) {
                triggerTime = now.plusMinutes(1).withSecond(0).withNano(0)
            } else {
                triggerTime = triggerTime.plusDays(1)
            }
        }

        if (!triggerTime.isAfter(now)) {
            triggerTime = triggerTime.plusDays(1)
        }

        return triggerTime.atZone(zoneId).toInstant().toEpochMilli()
    }
}
