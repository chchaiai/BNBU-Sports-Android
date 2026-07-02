package edu.bnbu.student.mvp.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BNBUColors {
    val Ink = Color(0xFF0B0B0C)
    val Paper = Color(0xFFF3F9FF)
    val Surface = Color.White
    val Muted = Color(0xFF4D6F8F)
    val Line = Color(0xFF0B0B0C)
    val Blue = Color(0xFF3A9DF6)
    val BlueLight = Color(0xFF7EBEFB)
    val BlueSoft = Color(0xFFE3F2FF)
    val Pale = Color(0xFFF7FAFD)
}

private val BNBULightColorScheme = lightColorScheme(
    primary = BNBUColors.Ink,
    onPrimary = BNBUColors.Surface,
    secondary = BNBUColors.Blue,
    onSecondary = BNBUColors.Surface,
    background = BNBUColors.Paper,
    onBackground = BNBUColors.Ink,
    surface = BNBUColors.Surface,
    onSurface = BNBUColors.Ink,
    outline = BNBUColors.Line
)

@Composable
fun BNBUStudentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BNBULightColorScheme,
        content = content
    )
}
