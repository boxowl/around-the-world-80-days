package com.boxowl.aroundtheworld.expedition

import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boxowl.aroundtheworld.MainActivity
import com.boxowl.aroundtheworld.expedition.credit.V2CreditRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only rendering with explicitly synthetic saved steps; never claims physical HC input. */
@RunWith(AndroidJUnit4::class)
class V2JourneyUiTest {
    @Test fun activeV2OpensFirstAndKeepsStoredV1() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("syntheticSeed") == "allow" &&
            (Build.PRODUCT.startsWith("sdk_") || Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator")))
        val context = instrumentation.targetContext
        val database = ExpeditionDatabase.get(context)
        val legacy = ExpeditionRepository(database, ExpeditionStore(context))
        val repository = V2CreditRepository(database)
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val oldV1 = runBlocking { legacy.load() ?: legacy.start(JourneyMode.FREE, now.minusSeconds(3600), zone) }
        val seq = runBlocking {
            val active = repository.loadActive()
            val started = repository.start(active?.expedition?.id, now.minusSeconds(120), zone, JourneyMode.FREE, 750L)
            repository.reconcile(started.expedition.id, mapOf(now.atZone(zone).toLocalDate() to 1_000L), now)
            started.expedition.id
        }
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            var visible = ""
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                visible = collectText(instrumentation.uiAutomation.rootInActiveWindow)
                if ("Путь вокруг света" in visible && "Расчётное реальное расстояние: 0.8 км" in visible &&
                    "Книжный прогресс: 15.0" in visible) break
                Thread.sleep(100)
            }
            assertTrue("v2 path missing: $visible", "Путь вокруг света" in visible)
            assertTrue("estimated real distance missing: $visible", "Расчётное реальное расстояние: 0.8 км" in visible)
            assertTrue("book distance missing: $visible", "Книжный прогресс: 15.0" in visible)
            assertTrue("passport navigation missing: $visible", "Паспорт" in visible)
            assertTrue("free journey should have no wager deadline: $visible", "Свободное путешествие без срока." in visible)
            assertTrue("stored v1 was changed", runBlocking { legacy.load()!!.startedAt == oldV1.startedAt })
            val mapTab = findText(instrumentation.uiAutomation.rootInActiveWindow, "Карта")
            var clickable = mapTab
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            assertTrue("map tab missing", clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            var descriptions = ""
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                descriptions = collectDescriptions(instrumentation.uiAutomation.rootInActiveWindow)
                if ("Глобус: подтверждено 15.000 книжных километров" in descriptions) break
                Thread.sleep(100)
            }
            assertTrue("production globe missing: $descriptions",
                "Глобус: подтверждено 15.000 книжных километров" in descriptions)
        } finally {
            activity.finish()
            runBlocking { database.v2Dao().endExpedition(seq, Instant.now().toString()) }
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val own = node.text?.toString().orEmpty()
        val children = (0 until node.childCount).joinToString(" ") { collectText(node.getChild(it)) }
        return "$own $children"
    }

    private fun collectDescriptions(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val own = node.contentDescription?.toString().orEmpty()
        val children = (0 until node.childCount).joinToString(" ") { collectDescriptions(node.getChild(it)) }
        return "$own $children"
    }

    private fun findText(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == target) return node
        for (i in 0 until node.childCount) findText(node.getChild(i), target)?.let { return it }
        return null
    }
}
