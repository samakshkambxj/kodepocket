package dev.kodepocket.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Material icon set bundled with the app does not ship a chat glyph, so
 * the bubble is drawn here on the same 24dp grid as the official icon. The
 * path is the standard Material "chat bubble" outline.
 */
val ChatBubbleIcon: ImageVector = ImageVector.Builder(
    name = "ChatBubble",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(20f, 2f)
        horizontalLineTo(4f)
        curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
        verticalLineTo(22f)
        lineTo(6f, 18f)
        horizontalLineTo(20f)
        curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
        verticalLineTo(4f)
        curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
        close()
    }
}.build()
