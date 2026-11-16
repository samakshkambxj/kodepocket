package dev.kodepocket.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Larger, softer corners are the main visual signature of Material 3
 * Expressive. The full scale is set so components pick up consistent values.
 */
val KodePocketShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
    extraLargeIncreased = RoundedCornerShape(40.dp),
    extraExtraLarge = RoundedCornerShape(52.dp),
)
