package com.boxowl.aroundtheworld.expedition

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Thin line icons for the dark shell (P06): 24 dp viewport, 1.8 stroke, round
 * caps and joins. Tinted by the Icon composable, so the stroke colour here is
 * only a placeholder.
 */
internal object JourneyIcons {

    /** Путь — идущий путник с посохом. */
    val Path: ImageVector = lineIcon("JourneyPath") {
        circle(10.4f, 5.0f, 1.9f)
        moveTo(10.2f, 7.2f)
        lineTo(9.5f, 13.2f)
        moveTo(9.5f, 13.2f)
        lineTo(6.4f, 19.8f)
        moveTo(9.5f, 13.2f)
        lineTo(12.4f, 19.2f)
        moveTo(10.0f, 8.6f)
        lineTo(13.4f, 11.6f)
        moveTo(14.9f, 7.4f)
        lineTo(15.8f, 20.6f)
        moveTo(10.2f, 8.0f)
        lineTo(7.4f, 10.8f)
    }

    /** Карта — сложенная дорожная карта. */
    val Map: ImageVector = lineIcon("JourneyMap") {
        moveTo(3.5f, 6f)
        lineTo(9f, 4f)
        lineTo(15f, 6f)
        lineTo(20.5f, 4f)
        lineTo(20.5f, 18f)
        lineTo(15f, 20f)
        lineTo(9f, 18f)
        lineTo(3.5f, 20f)
        close()
        moveTo(9f, 4f)
        lineTo(9f, 18f)
        moveTo(15f, 6f)
        lineTo(15f, 20f)
    }

    /** Дневник — раскрытая книга. */
    val Diary: ImageVector = lineIcon("JourneyDiary") {
        moveTo(12f, 6f)
        curveTo(10f, 4.6f, 6.8f, 4.4f, 4f, 5.4f)
        lineTo(4f, 18f)
        curveTo(6.8f, 17f, 10f, 17.2f, 12f, 18.6f)
        curveTo(14f, 17.2f, 17.2f, 17f, 20f, 18f)
        lineTo(20f, 5.4f)
        curveTo(17.2f, 4.4f, 14f, 4.6f, 12f, 6f)
        close()
        moveTo(12f, 6f)
        lineTo(12f, 18.6f)
    }

    /** Настройки — восьмизубая шестерёнка. */
    val Gear: ImageVector = lineIcon("JourneyGear") {
        circle(12f, 12f, 3.1f)
        val teeth = 8
        val root = 5.4f
        val tip = 8.4f
        val cx = 12f
        val cy = 12f
        for (i in 0 until teeth) {
            val a0 = Math.toRadians(i * 45.0 + 8.0)
            val a1 = Math.toRadians(i * 45.0 + 16.0)
            val a2 = Math.toRadians(i * 45.0 + 29.0)
            val a3 = Math.toRadians(i * 45.0 + 37.0)
            moveTo(cx + root * cos(a0).toFloat(), cy + root * sin(a0).toFloat())
            lineTo(cx + tip * cos(a1).toFloat(), cy + tip * sin(a1).toFloat())
            lineTo(cx + tip * cos(a2).toFloat(), cy + tip * sin(a2).toFloat())
            lineTo(cx + root * cos(a3).toFloat(), cy + root * sin(a3).toFloat())
        }
    }

    /** Компактная сверка шагов — круговая стрелка. */
    val Refresh: ImageVector = lineIcon("JourneyRefresh") {
        var first = true
        for (deg in 60..300 step 10) {
            val rad = Math.toRadians(deg.toDouble())
            val px = 12f + 8f * cos(rad).toFloat()
            val py = 12f + 8f * sin(rad).toFloat()
            if (first) { moveTo(px, py); first = false } else { lineTo(px, py) }
        }
        moveTo(12.8f, 3.6f)
        lineTo(16.2f, 5.2f)
        lineTo(18.6f, 1.9f)
    }

    /** Скрыть уведомление — крестик. */
    val Close: ImageVector = lineIcon("JourneyClose") {
        moveTo(6.5f, 6.5f)
        lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f)
        lineTo(6.5f, 17.5f)
    }

    private fun lineIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        ).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcToRelative(r, r, 0f, false, true, -2 * r, 0f)
        arcToRelative(r, r, 0f, false, true, 2 * r, 0f)
        close()
    }
}
