package com.boxowl.aroundtheworld.expedition.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.boxowl.aroundtheworld.MainActivity
import com.boxowl.aroundtheworld.R
import java.time.Instant
import java.time.ZoneId

/** One opt-in, inexact daily alarm; no background health-data access. */
object ReminderScheduler {
    const val ACTION_REMIND = "com.boxowl.aroundtheworld.action.DAILY_REMINDER"
    private const val PREFS = "daily_reminder"
    private const val ENABLED = "enabled"
    private const val LAST_SHOWN = "last_shown_date"
    private const val CHANNEL = "journey_reminder"
    private const val NOTIFICATION_ID = 80
    private const val REQUEST_CODE = 80

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED, false)

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val app = context.applicationContext
        if (enabled) {
            ensureChannel(app)
            if (!canPost(app)) return false
        }
        if (!prefs(app).edit().putBoolean(ENABLED, enabled).commit()) return false
        return runCatching {
            if (enabled) {
                setRescheduleReceiverEnabled(app, true)
                scheduleNext(app)
            } else {
                cancel(app)
                app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
                setRescheduleReceiverEnabled(app, false)
            }
            true
        }.getOrElse {
            if (enabled) {
                prefs(app).edit().putBoolean(ENABLED, false).commit()
                runCatching { cancel(app); setRescheduleReceiverEnabled(app, false) }
            }
            false
        }
    }

    /** Called by system broadcasts after reboot, package replacement or clock/zone changes. */
    fun restore(context: Context) {
        if (isEnabled(context)) scheduleNext(context.applicationContext)
    }

    /** Performs only quick local notification work, then schedules the next local day. */
    fun onAlarm(context: Context, now: Instant = Instant.now()) {
        val app = context.applicationContext
        if (!isEnabled(app)) return
        val zone = ZoneId.systemDefault()
        val local = now.atZone(zone)
        val today = local.toLocalDate().toString()
        try {
            if (local.hour >= 19 && today != prefs(app).getString(LAST_SHOWN, null) && canPost(app)) {
                ensureChannel(app)
                if (post(app)) prefs(app).edit().putString(LAST_SHOWN, today).commit()
            }
        } finally {
            if (isEnabled(app)) scheduleNext(app)
        }
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Напоминания о прогулке", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun post(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            val open = PendingIntent.getActivity(
                context, REQUEST_CODE, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Вокруг света за 80 дней")
                .setContentText("Небольшая прогулка тоже двигает путешествие вперёд.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    private fun alarmIntent(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_REMIND),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleNext(context: Context) {
        val alarm = checkNotNull(context.getSystemService(AlarmManager::class.java))
        val pending = checkNotNull(alarmIntent(context, PendingIntent.FLAG_UPDATE_CURRENT))
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            ReminderTime.next(Instant.now(), ZoneId.systemDefault()).toEpochMilli(),
            pending,
        )
    }

    private fun cancel(context: Context) {
        val pending = alarmIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    private fun setRescheduleReceiverEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, ReminderRescheduleReceiver::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
