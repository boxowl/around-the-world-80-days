package com.boxowl.aroundtheworld.expedition

import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boxowl.aroundtheworld.MainActivity
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only accessibility smoke check using synthetic daily steps. */
@RunWith(AndroidJUnit4::class)
class FirstLegMapUiTest {
    @Test fun mapShowsCurrentSegmentAndKeepsDiaryNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("syntheticSeed") == "allow" &&
            (Build.PRODUCT.startsWith("sdk_") || Build.FINGERPRINT.contains("emulator")))
        val context = instrumentation.targetContext
        runBlocking {
            val repo = ExpeditionRepository(ExpeditionDatabase.get(context), ExpeditionStore(context))
            val saved = repo.load() ?: repo.start(JourneyMode.FREE,
                Instant.now().minusSeconds(3_600), ZoneId.systemDefault())
            val now = Instant.now()
            repo.reconcile(mapOf(now.atZone(saved.zone).toLocalDate() to 4_500L), now)
        }
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            val automation = instrumentation.uiAutomation
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                val tab = findText(automation.rootInActiveWindow, "Карта")
                if (tab != null) {
                    assertTrue("Map tab did not click", tab.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    break
                }
                Thread.sleep(200)
            }
            var visible = ""
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                visible = collectText(automation.rootInActiveWindow).replace('\u00a0', ' ')
                if ("Участок: Дувр → Кале" in visible) break
                Thread.sleep(200)
            }
            assertTrue("Map segment missing: $visible", "Участок: Дувр → Кале" in visible)
            assertTrue("Diary tab missing: $visible", "Дневник" in visible)
            assertTrue("Diagnostics tab missing: $visible", "Шаги и доступ" in visible)
            val diaryTab = findText(automation.rootInActiveWindow, "Дневник")
            assertTrue("Diary tab did not click", diaryTab?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            var diary = ""
            for (attempt in 0 until 20) {
                instrumentation.waitForIdleSync()
                diary = collectText(automation.rootInActiveWindow)
                if ("Дневник путешествия" in diary) break
                Thread.sleep(100)
            }
            assertTrue("Diary did not open: $diary", "Дневник путешествия" in diary)
        } finally {
            activity.finish()
        }
    }

    private fun findText(node: AccessibilityNodeInfo?, value: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == value) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target != null) return target
        }
        for (i in 0 until node.childCount) findText(node.getChild(i), value)?.let { return it }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        return node.text?.toString().orEmpty() + " " +
            (0 until node.childCount).joinToString(" ") { collectText(node.getChild(it)) }
    }

}
