package com.boxowl.aroundtheworld.expedition

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boxowl.aroundtheworld.MainActivity
import com.boxowl.aroundtheworld.expedition.credit.V2CreditRepository
import com.boxowl.aroundtheworld.expedition.globe.GlobeStopCardsV1
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
            instrumentation.waitForIdleSync()
            for (attempt in 0 until 30) {
                val scrollable = findScrollable(instrumentation.uiAutomation.rootInActiveWindow) ?: break
                if (!scrollable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)) break
                instrumentation.waitForIdleSync()
            }
            var descriptions = ""
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                descriptions = collectDescriptions(instrumentation.uiAutomation.rootInActiveWindow)
                if ("Глобус: подтверждено 15.000 книжных километров" in descriptions) break
                Thread.sleep(100)
            }
            assertTrue("production globe missing: $descriptions",
                "Глобус: подтверждено 15.000 книжных километров" in descriptions)
            val slider = findClass(instrumentation.uiAutomation.rootInActiveWindow, "android.widget.SeekBar")
            assertTrue("browse slider label missing: $descriptions",
                collectDescriptions(instrumentation.uiAutomation.rootInActiveWindow).contains("Позиция просмотра маршрута"))
            val halfway = Bundle().apply {
                putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0.5f)
            }
            assertTrue("browse slider missing", slider?.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, halfway,
            ) == true)
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                descriptions = collectDescriptions(instrumentation.uiAutomation.rootInActiveWindow)
                if ("Глобус: просмотр прошлого 7.500 книжных километров" in descriptions) break
                Thread.sleep(100)
            }
            assertTrue("browsed globe missing: $descriptions",
                "Глобус: просмотр прошлого 7.500 книжных километров" in descriptions)
            val returnText = findText(instrumentation.uiAutomation.rootInActiveWindow, "Вернуться к текущей")
            clickable = returnText
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            assertTrue("return action missing", clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                descriptions = collectDescriptions(instrumentation.uiAutomation.rootInActiveWindow)
                if ("Глобус: подтверждено 15.000 книжных километров" in descriptions) break
                Thread.sleep(100)
            }
            assertTrue("confirmed globe did not return: $descriptions",
                "Глобус: подтверждено 15.000 книжных километров" in descriptions)
            assertTrue("browse changed stored credit", runBlocking {
                repository.loadActive()!!.ledger.totalBookMeters == 15_000L
            })
            var mapText = collectText(instrumentation.uiAutomation.rootInActiveWindow)
            assertTrue("start card detail missing: $mapText", "Выбрана остановка: Лондон · london#1" in mapText)
            assertTrue("start arrival invented: $mapText", "Старт маршрута" in mapText)
            val suezIndex = GlobeStopCardsV1.cards.indexOfFirst { it.occurrenceId == "suez#1" }
            assertTrue("Suez card missing from route", suezIndex > 0)
            for (index in 1..suezIndex) {
                var nextStop = findText(instrumentation.uiAutomation.rootInActiveWindow, "Следующая остановка")
                for (attempt in 0 until 10) {
                    if (nextStop?.isVisibleToUser == true) break
                    swipeUp(instrumentation)
                    instrumentation.waitForIdleSync()
                    nextStop = findText(instrumentation.uiAutomation.rootInActiveWindow, "Следующая остановка")
                }
                assertTrue("next stop not visible at $index", nextStop?.isVisibleToUser == true)
                tapNode(instrumentation, nextStop!!)
                val card = GlobeStopCardsV1.cards[index]
                val selected = "Выбрана остановка: ${card.nameRu} · ${card.occurrenceId}"
                for (attempt in 0 until 30) {
                    instrumentation.waitForIdleSync()
                    mapText = collectText(instrumentation.uiAutomation.rootInActiveWindow)
                    if (selected in mapText) break
                    Thread.sleep(100)
                }
                assertTrue("selection $index missing: $mapText", selected in mapText)
            }
            assertTrue("Suez arrival missing: $mapText", "Глава 8 · Водный путь" in mapText)
            assertTrue("unreached Suez was marked reached: $mapText", "Впереди" in mapText)
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

    private fun findClass(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString() == target) return node
        for (i in 0 until node.childCount) findClass(node.getChild(i), target)?.let { return it }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) findScrollable(node.getChild(i))?.let { return it }
        return null
    }

    private fun swipeUp(instrumentation: android.app.Instrumentation) {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val startY = metrics.heightPixels * 0.75f
        val endY = metrics.heightPixels * 0.30f
        val down = SystemClock.uptimeMillis()
        for (step in 0..12) {
            val action = when (step) {
                0 -> MotionEvent.ACTION_DOWN
                12 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val event = MotionEvent.obtain(down, down + step * 16L, action, x, startY + (endY - startY) * step / 12f, 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
    }

    private fun tapNode(instrumentation: android.app.Instrumentation, node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val down = SystemClock.uptimeMillis()
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat()
        val press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
        val release = MotionEvent.obtain(down, down + 80L, MotionEvent.ACTION_UP, x, y, 0)
        instrumentation.uiAutomation.injectInputEvent(press, true)
        instrumentation.uiAutomation.injectInputEvent(release, true)
        press.recycle()
        release.recycle()
    }
}
