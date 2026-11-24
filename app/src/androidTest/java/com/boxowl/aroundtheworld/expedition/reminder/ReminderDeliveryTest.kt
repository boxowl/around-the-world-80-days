package com.boxowl.aroundtheworld.expedition.reminder

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only notification verification; no physical step input is inferred. */
@RunWith(AndroidJUnit4::class)
class ReminderDeliveryTest {
    @Test fun optInPostsAndOptOutCancelsNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(Build.PRODUCT.startsWith("sdk_") || Build.FINGERPRINT.contains("emulator"))
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        context.getSharedPreferences("daily_reminder", 0).edit().clear().commit()
        try {
            assertTrue(ReminderScheduler.setEnabled(context, true))
            assertTrue(ReminderScheduler.isEnabled(context))
            val evening = LocalDate.now().atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant()
            ReminderScheduler.onAlarm(context, evening)
            assertTrue(manager.activeNotifications.any { it.id == 80 })
            assertTrue(ReminderScheduler.setEnabled(context, false))
            assertFalse(ReminderScheduler.isEnabled(context))
            assertFalse(manager.activeNotifications.any { it.id == 80 })
        } finally {
            ReminderScheduler.setEnabled(context, false)
        }
    }
}
