package com.peyi.sonypodsbattery

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons Material doesn't have, drawn to sit with the Rounded set. */
internal object SunIcons {
    /** The XM5 charging case: a rounded pebble with the lid seam across it. */
    val Case: ImageVector by lazy {
        ImageVector.Builder("SunCase", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(7f, 6f); lineTo(17f, 6f)
                curveTo(20.3f, 6f, 22f, 8.6f, 22f, 12f)
                curveTo(22f, 15.4f, 20.3f, 18f, 17f, 18f)
                lineTo(7f, 18f)
                curveTo(3.7f, 18f, 2f, 15.4f, 2f, 12f)
                curveTo(2f, 8.6f, 3.7f, 6f, 7f, 6f); close()
                moveTo(7f, 8f); lineTo(17f, 8f)
                curveTo(19f, 8f, 19.9f, 9.3f, 20f, 11f)
                lineTo(4f, 11f)
                curveTo(4.1f, 9.3f, 5f, 8f, 7f, 8f); close()
                moveTo(4f, 13f); lineTo(20f, 13f)
                curveTo(19.9f, 14.7f, 19f, 16f, 17f, 16f)
                lineTo(7f, 16f)
                curveTo(5f, 16f, 4.1f, 14.7f, 4f, 13f); close()
            }
        }.build()
    }
}
