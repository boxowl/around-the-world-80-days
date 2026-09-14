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

/** Emulator-only end-to-end rendering check with explicitly synthetic saved steps. */
@RunWith(AndroidJUnit4::class)
class JourneyHomeUiTest {
    @Test fun rendersStoredStepsAndNextStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("syntheticSeed") == "allow" &&
            (Build.PRODUCT.startsWith("sdk_") || Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator")))
        val context = instrumentation.targetContext
        runBlocking {
            val repo = ExpeditionRepository(ExpeditionDatabase.get(context), ExpeditionStore(context))
            val start = Instant.now().minusSeconds(3_600)
            val expedition = repo.load() ?: repo.start(JourneyMode.FREE, start, ZoneId.systemDefault())
            val now = Instant.now()
            repo.reconcile(mapOf(now.atZone(expedition.zone).toLocalDate() to 4_500L), now)
        }
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            var visible = ""
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                visible = collectText(instrumentation.uiAutomation.rootInActiveWindow).replace('\u00a0', ' ')
                if ("Лондон → Суэц" in visible && "4 500" in visible && "Кале" in visible) break
                Thread.sleep(200)
            }
            assertTrue("Route title missing from UI: $visible", "Лондон → Суэц" in visible)
            assertTrue("Stored synthetic steps missing from UI: $visible", "4 500" in visible)
            assertTrue("Scaled next stop missing from UI: $visible", "Кале" in visible)
        } finally {
            activity.finish()
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val own = node.text?.toString().orEmpty()
        val children = (0 until node.childCount).joinToString(" ") { collectText(node.getChild(it)) }
        return "$own $children"
    }
}
