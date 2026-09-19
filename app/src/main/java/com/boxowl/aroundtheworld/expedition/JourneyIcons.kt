package com.boxowl.aroundtheworld.expedition

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Thin line icons for the bottom navigation: 24 dp viewport, 1.8 stroke, round
 * caps and joins. Tinted by the Icon composable, so the stroke colour here is
 * only a placeholder.
 */
internal object JourneyIcons {

    /** Путь — два следа идущего. */
    val Path: ImageVector = lineIcon("JourneyPath") {
        moveTo(7.2f, 3.6f)
        curveTo(5.7f, 3.6f, 4.9f, 5.2f, 4.9f, 6.9f)
        curveTo(4.9f, 8.6f, 5.7f, 9.9f, 7.2f, 9.9f)
        curveTo(8.7f, 9.9f, 9.5f, 8.6f, 9.5f, 6.9f)
        curveTo(9.5f, 5.2f, 8.7f, 3.6f, 7.2f, 3.6f)
        close()
        circle(7.2f, 12.5f, 1.3f)
        moveTo(16.8f, 11.6f)
        curveTo(15.3f, 11.6f, 14.5f, 13.2f, 14.5f, 14.9f)
        curveTo(14.5f, 16.6f, 15.3f, 17.9f, 16.8f, 17.9f)
        curveTo(18.3f, 17.9f, 19.1f, 16.6f, 19.1f, 14.9f)
        curveTo(19.1f, 13.2f, 18.3f, 11.6f, 16.8f, 11.6f)
        close()
        circle(16.8f, 20.5f, 1.3f)
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

    /** Шаги и доступ — походный башмак в профиль. */
    val Steps: ImageVector = lineIcon("JourneySteps") {
        moveTo(5.5f, 3.5f)
        lineTo(10.5f, 3.5f)
        lineTo(10.5f, 10f)
        curveTo(12.8f, 10.8f, 17.5f, 12.2f, 19f, 14.6f)
        lineTo(19f, 18f)
        lineTo(5.5f, 18f)
        close()
        moveTo(5.5f, 15.4f)
        lineTo(19f, 15.4f)
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
