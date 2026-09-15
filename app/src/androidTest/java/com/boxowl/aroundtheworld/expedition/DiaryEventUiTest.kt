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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only UI journey backed by an explicitly synthetic daily aggregate. */
@RunWith(AndroidJUnit4::class)
class DiaryEventUiTest {
    @Test fun eventCanBeReadFromDiaryAndDoesNotReturnAfterRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("syntheticSeed") == "allow" &&
            (Build.PRODUCT.startsWith("sdk_") || Build.FINGERPRINT.contains("emulator")))
        val context = instrumentation.targetContext
        context.deleteDatabase("expedition.db")
        runBlocking {
            val repo = ExpeditionRepository(ExpeditionDatabase.get(context), ExpeditionStore(context))
            val now = Instant.now()
            val journey = repo.start(JourneyMode.FREE, now.minusSeconds(3_600), ZoneId.systemDefault())
            repo.reconcile(mapOf(journey.startDate to journey.stops.first { it.id == "departure" }.threshold), now)
        }

        val first = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            assertTrue(waitForText(instrumentation, "Новое событие · За лондонскими крышами"))
            assertTrue(clickText(instrumentation, "Дневник"))
            assertTrue(waitForText(instrumentation, "Новая запись", scroll = true))
            assertTrue(clickText(instrumentation, "Отметить прочитанной", scroll = true))
            assertTrue(waitUntil(instrumentation) { "Новая запись" !in it && "Новое событие ·" !in it })
        } finally { first.finish() }

        val second = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            assertTrue(clickText(instrumentation, "Дневник"))
            assertTrue(waitForText(instrumentation, "За лондонскими крышами", scroll = true))
            val visible = collectText(instrumentation.uiAutomation.rootInActiveWindow)
            assertFalse("Read event returned after restart: $visible", "Новое событие ·" in visible)
        } finally { second.finish() }
    }

    private fun waitForText(
        instrumentation: android.app.Instrumentation,
        value: String,
        scroll: Boolean = false,
    ): Boolean {
        repeat(40) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (value in collectText(root)) return true
            if (scroll) findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(150)
        }
        return false
    }

    private fun waitUntil(instrumentation: android.app.Instrumentation, predicate: (String) -> Boolean): Boolean {
        repeat(40) {
            instrumentation.waitForIdleSync()
            if (predicate(collectText(instrumentation.uiAutomation.rootInActiveWindow))) return true
            Thread.sleep(150)
        }
        return false
    }

    private fun clickText(
        instrumentation: android.app.Instrumentation,
        value: String,
        scroll: Boolean = false,
    ): Boolean {
        repeat(30) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val node = findText(root, value)
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            if (scroll) findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(150)
        }
        return false
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) findScrollable(node.getChild(index))?.let { return it }
        return null
    }

    private fun findText(node: AccessibilityNodeInfo?, value: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == value) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target != null) return target
        }
        for (index in 0 until node.childCount) findText(node.getChild(index), value)?.let { return it }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        return node.text?.toString().orEmpty() + " " +
            (0 until node.childCount).joinToString(" ") { collectText(node.getChild(it)) }
    }
}
