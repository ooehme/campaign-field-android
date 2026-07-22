package de.oliveroehme.campaignfield.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Lucide v1.17.0 paths, matching the icon set used by the web app. */
object FieldIcons {
    val ClipboardList by lazy {
        lucideIcon(
            "ClipboardList",
            "M8 2h8a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1",
            "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
            "M12 11h4",
            "M12 16h4",
            "M8 11h.01",
            "M8 16h.01",
        )
    }

    val Radar by lazy {
        lucideIcon(
            "Radar",
            "M19.07 4.93A10 10 0 0 0 6.99 3.34",
            "M4 6h.01",
            "M2.29 9.62A10 10 0 1 0 21.31 8.35",
            "M16.24 7.76A6 6 0 1 0 8.23 16.67",
            "M12 18h.01",
            "M17.99 11.66A6 6 0 0 1 15.77 16.67",
            circle(12f, 12f, 2f),
            "m13.41 10.59 5.66-5.66",
        )
    }

    val RefreshCcw by lazy {
        lucideIcon(
            "RefreshCcw",
            "M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
            "M3 3v5h5",
            "M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16",
            "M16 16h5v5",
        )
    }

    val UserRound by lazy {
        lucideIcon(
            "UserRound",
            circle(12f, 8f, 5f),
            "M20 21a8 8 0 0 0-16 0",
        )
    }

    val ArrowRight by lazy {
        lucideIcon("ArrowRight", "M5 12h14", "m12 5 7 7-7 7")
    }

    val ArrowLeft by lazy {
        lucideIcon("ArrowLeft", "m12 19-7-7 7-7", "M19 12H5")
    }

    val MapPin by lazy {
        lucideIcon(
            "MapPin",
            "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
            circle(12f, 10f, 3f),
        )
    }

    val MapPinX by lazy {
        lucideIcon(
            "MapPinX",
            "M19.752 11.901A7.78 7.78 0 0 0 20 10a8 8 0 0 0-16 0c0 4.993 5.539 10.193 7.399 11.799a1 1 0 0 0 1.202 0 19 19 0 0 0 .09-.077",
            circle(12f, 10f, 3f),
            "m21.5 15.5-5 5",
            "m21.5 20.5-5-5",
        )
    }

    val MapPinCheck by lazy {
        lucideIcon(
            "MapPinCheck",
            "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
            "m9 10 2 2 4-4",
        )
    }

    val UsersRound by lazy {
        lucideIcon(
            "UsersRound",
            "M18 21a8 8 0 0 0-16 0",
            circle(10f, 8f, 5f),
            "M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3",
        )
    }

    val CalendarClock by lazy {
        lucideIcon(
            "CalendarClock",
            "M16 14v2.2l1.6 1",
            "M16 2v4",
            "M21 7.5V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h3.5",
            "M3 10h5",
            "M8 2v4",
            circle(16f, 16f, 6f),
        )
    }

    val Map by lazy {
        lucideIcon(
            "Map",
            "M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0z",
            "M15 5.764v15",
            "M9 3.236v15",
        )
    }

    val FileText by lazy {
        lucideIcon(
            "FileText",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10 9H8",
            "M16 13H8",
            "M16 17H8",
        )
    }

    val LogOut by lazy {
        lucideIcon("LogOut", "m16 17 5-5-5-5", "M21 12H9", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4")
    }

    val Database by lazy {
        lucideIcon(
            "Database",
            "M3 5V19A9 3 0 0 0 21 19V5",
            "M3 12A9 3 0 0 0 21 12",
            "M3 5A9 3 0 0 1 21 5A9 3 0 0 1 3 5",
        )
    }

    val Server by lazy {
        lucideIcon(
            "Server",
            roundedRect(2f, 2f, 20f, 8f, 2f),
            roundedRect(2f, 14f, 20f, 8f, 2f),
            "M6 6h.01",
            "M6 18h.01",
        )
    }

    val CheckCircle by lazy {
        lucideIcon(
            "CheckCircle",
            "M22 11.08V12a10 10 0 1 1-5.93-9.14",
            "m9 11 3 3L22 4",
        )
    }
}

private fun lucideIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { path ->
            addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun circle(cx: Float, cy: Float, radius: Float): String =
    "M${cx - radius} $cy a$radius $radius 0 1 0 ${radius * 2} 0 a$radius $radius 0 1 0 ${-radius * 2} 0"

private fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float): String =
    "M${x + radius} $y H${x + width - radius} A$radius $radius 0 0 1 ${x + width} ${y + radius} " +
        "V${y + height - radius} A$radius $radius 0 0 1 ${x + width - radius} ${y + height} " +
        "H${x + radius} A$radius $radius 0 0 1 $x ${y + height - radius} " +
        "V${y + radius} A$radius $radius 0 0 1 ${x + radius} $y"
