package edu.bnbu.student.mvp.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Google Material 3 rounded-corner system.
 *
 * Replaces the previous RectangleShape (0 dp) everywhere with M3's
 * layered rounding: tighter for tiny chips, generous for cards and
 * large containers.
 */
val BNBUShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // tiny badges, chips
    small = RoundedCornerShape(8.dp),         // inputs, small buttons
    medium = RoundedCornerShape(12.dp),       // cards, panels
    large = RoundedCornerShape(16.dp),        // dialogs, large cards
    extraLarge = RoundedCornerShape(28.dp)    // bottom sheets, FABs
)
