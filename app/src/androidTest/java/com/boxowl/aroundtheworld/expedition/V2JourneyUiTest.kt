package com.boxowl.aroundtheworld.expedition

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boxowl.aroundtheworld.MainActivity
import com.boxowl.aroundtheworld.expedition.credit.V2CreditRepository
import com.boxowl.aroundtheworld.expedition.globe.GlobeStopCardsV1
import com.boxowl.aroundtheworld.expedition.globe.RoutePosition
import com.boxowl.aroundtheworld.expedition.globe.V2ChapterMarkerHitTest
import com.boxowl.aroundtheworld.expedition.globe.vecToLatLon
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min
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
            assertTrue("current transport missing: $visible", "Текущий транспорт: Поезд · Лондон → Дувр · глава 3" in visible)
            assertTrue("confirmed companions missing: $visible", "Спутники: Фогг, Паспарту" in visible)
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
            assertTrue("start card detail missing: $mapText", "Выбрана остановка: Лондон · старт" in mapText)
            assertTrue("start arrival invented: $mapText", "Старт маршрута" in mapText)
            assertTrue("start narrative missing: $mapText", "Паспорт открыт. У вокзала начинается дорога вокруг света." in mapText)
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
                val selected = "Выбрана остановка: ${card.nameRu}"
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
            assertTrue("future Suez narrative exposed: $mapText", "Запись пока закрыта." in mapText)
            assertTrue("future Suez text visible: $mapText", "Средиземное море осталось за кормой." !in mapText)
            val passportTab = findText(instrumentation.uiAutomation.rootInActiveWindow, "Паспорт")
            assertTrue("passport tab missing", passportTab != null)
            tapNode(instrumentation, passportTab!!)
            instrumentation.waitForIdleSync()
            for (attempt in 0 until 30) {
                val scrollable = findScrollable(instrumentation.uiAutomation.rootInActiveWindow) ?: break
                if (!scrollable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)) break
                instrumentation.waitForIdleSync()
            }
            var suezLink = findText(instrumentation.uiAutomation.rootInActiveWindow, "На карте: Суэц")
            for (attempt in 0 until 10) {
                if (suezLink?.isVisibleToUser == true) break
                swipeUp(instrumentation)
                instrumentation.waitForIdleSync()
                suezLink = findText(instrumentation.uiAutomation.rootInActiveWindow, "На карте: Суэц")
            }
            assertTrue("Suez passport link missing", suezLink?.isVisibleToUser == true)
            clickable = suezLink
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            assertTrue("Suez passport button missing", clickable != null)
            val suezButton = clickable!!
            assertTrue("passport stamp status inaccessible", suezButton.stateDescription?.toString() == "Штамп впереди")
            val suezBounds = Rect()
            suezButton.getBoundsInScreen(suezBounds)
            assertTrue("Suez button click rejected: $suezBounds", suezButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                mapText = collectText(instrumentation.uiAutomation.rootInActiveWindow)
                if ("Выбрана остановка: Суэц" in mapText) break
                Thread.sleep(100)
            }
            assertTrue("passport did not open Suez from $suezBounds: $mapText", "Выбрана остановка: Суэц" in mapText)
            assertTrue("passport opened future note: $mapText", "Запись пока закрыта." in mapText)
            assertTrue("passport changed stored credit", runBlocking {
                repository.loadActive()!!.ledger.totalBookMeters == 15_000L
            })
            var passportReturned = false
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                clickable = findText(instrumentation.uiAutomation.rootInActiveWindow, "Паспорт")
                while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    passportReturned = true
                    break
                }
                Thread.sleep(100)
            }
            assertTrue("passport return missing", passportReturned)
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                if ("Паспорт экспедиции" in collectText(instrumentation.uiAutomation.rootInActiveWindow)) break
                Thread.sleep(100)
            }
            for (attempt in 0 until 30) {
                val scrollable = findScrollable(instrumentation.uiAutomation.rootInActiveWindow) ?: break
                if (!scrollable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)) break
                instrumentation.waitForIdleSync()
            }
            val londonLink = findText(instrumentation.uiAutomation.rootInActiveWindow, "На карте: Лондон · старт")
            clickable = londonLink
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            assertTrue("London passport link missing", clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                mapText = collectText(instrumentation.uiAutomation.rootInActiveWindow)
                if ("Выбрана остановка: Лондон · старт" in mapText) break
                Thread.sleep(100)
            }
            assertTrue("passport did not open start London: $mapText", "Выбрана остановка: Лондон · старт" in mapText)
            var picker = findText(instrumentation.uiAutomation.rootInActiveWindow, "Выбрать остановку")
            for (attempt in 0 until 10) {
                if (picker?.isVisibleToUser == true) break
                swipeUp(instrumentation)
                instrumentation.waitForIdleSync()
                picker = findText(instrumentation.uiAutomation.rootInActiveWindow, "Выбрать остановку")
            }
            assertTrue("direct stop picker missing", picker?.isVisibleToUser == true)
            tapNode(instrumentation, picker!!)
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                if (findText(instrumentation.uiAutomation.rootInActiveWindow, "Остановки маршрута") != null) break
                Thread.sleep(100)
            }
            assertTrue("stop picker dialog did not open", findText(instrumentation.uiAutomation.rootInActiveWindow, "Остановки маршрута") != null)
            var suezChoice = findText(instrumentation.uiAutomation.rootInActiveWindow, "Суэц")
            for (attempt in 0 until 10) {
                if (suezChoice?.isVisibleToUser == true) break
                val scrolled = findScrollable(instrumentation.uiAutomation.rootInActiveWindow)
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
                if (!scrolled) swipeUp(instrumentation)
                instrumentation.waitForIdleSync()
                suezChoice = findText(instrumentation.uiAutomation.rootInActiveWindow, "Суэц")
            }
            assertTrue("direct Suez choice missing", suezChoice?.isVisibleToUser == true)
            clickable = suezChoice
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            assertTrue("direct Suez choice not clickable", clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                mapText = collectText(instrumentation.uiAutomation.rootInActiveWindow)
                if ("Выбрана остановка: Суэц" in mapText) break
                Thread.sleep(100)
            }
            assertTrue("direct selection did not open Suez: $mapText", "Выбрана остановка: Суэц" in mapText)
            assertTrue("direct selection exposed future note: $mapText", "Запись пока закрыта." in mapText)
            assertTrue("direct selection changed stored credit", runBlocking {
                repository.loadActive()!!.ledger.totalBookMeters == 15_000L
            })
            for (attempt in 0 until 30) {
                val scrollable = findScrollable(instrumentation.uiAutomation.rootInActiveWindow) ?: break
                if (!scrollable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)) break
                instrumentation.waitForIdleSync()
            }
            var globe = findDescriptionContaining(
                instrumentation.uiAutomation.rootInActiveWindow,
                "Глобус: подтверждено 15.000 книжных километров",
            )
            val fullGlobeHeight = (320f * context.resources.displayMetrics.density).toInt()
            for (attempt in 0 until 10) {
                val visibleBounds = Rect()
                globe?.getBoundsInScreen(visibleBounds)
                if (globe?.isVisibleToUser == true && visibleBounds.height() >= fullGlobeHeight - 2) break
                swipeDown(instrumentation)
                instrumentation.waitForIdleSync()
                globe = findDescriptionContaining(instrumentation.uiAutomation.rootInActiveWindow,
                    "Глобус: подтверждено 15.000 книжных километров")
            }
            val fullBounds = Rect()
            globe?.getBoundsInScreen(fullBounds)
            assertTrue("globe node not fully visible for marker tap: $fullBounds expected=$fullGlobeHeight", globe?.isVisibleToUser == true && fullBounds.height() >= fullGlobeHeight - 2)
            val globeBounds = Rect().also(globe!!::getBoundsInScreen)
            val (heroLat, heroLon) = vecToLatLon(RoutePosition.positionAt(15.0))
            val londonMark = V2ChapterMarkerHitTest.targets(
                Offset(globeBounds.width() / 2f, globeBounds.height() / 2f),
                min(globeBounds.width(), globeBounds.height()) * 0.4f,
                heroLat,
                heroLon,
            ).single { it.occurrenceId == "london#2" }
            tapPoint(instrumentation, globeBounds.left + londonMark.center.x, globeBounds.top + londonMark.center.y)
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync()
                mapText = collectText(instrumentation.uiAutomation.rootInActiveWindow)
                if ("Выбрана остановка: Лондон · финиш" in mapText) break
                swipeUp(instrumentation)
                Thread.sleep(100)
            }
            assertTrue("globe marker did not select final London: bounds=$globeBounds mark=${londonMark.center} screen=${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels} text=$mapText", "Выбрана остановка: Лондон · финиш" in mapText)
            assertTrue("marker tap changed stored credit", runBlocking {
                repository.loadActive()!!.ledger.totalBookMeters == 15_000L
            })
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

    private fun findDescriptionContaining(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString()?.contains(target) == true) return node
        for (i in 0 until node.childCount) findDescriptionContaining(node.getChild(i), target)?.let { return it }
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

    private fun swipeUp(instrumentation: android.app.Instrumentation) = swipe(instrumentation, 0.75f, 0.30f)

    private fun swipeDown(instrumentation: android.app.Instrumentation) = swipe(instrumentation, 0.30f, 0.75f)

    private fun swipe(instrumentation: android.app.Instrumentation, startFraction: Float, endFraction: Float) {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val startY = metrics.heightPixels * startFraction
        val endY = metrics.heightPixels * endFraction
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
        tapPoint(instrumentation, bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun tapPoint(instrumentation: android.app.Instrumentation, x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        val press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
        val release = MotionEvent.obtain(down, down + 80L, MotionEvent.ACTION_UP, x, y, 0)
        instrumentation.uiAutomation.injectInputEvent(press, true)
        instrumentation.uiAutomation.injectInputEvent(release, true)
        press.recycle()
        release.recycle()
    }
}
