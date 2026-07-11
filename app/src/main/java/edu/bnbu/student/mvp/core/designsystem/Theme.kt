package edu.bnbu.student.mvp.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import edu.bnbu.student.mvp.core.model.AppThemeMode

/**
 * ── Google Material 3 Dynamic Color Palette ─────────────────
 *
 * Seed color:  Google Blue  #1A73E8
 * Secondary:   Sport Orange #FD7E14
 *
 * The "高级感" (premium feel) comes from NeutralVariant — grays
 * with a faint blue/purple undertone, never pure #808080.
 *
 * Light background:  #F8F9FA  (slightly warm off-white)
 * Dark background:   #0F172A  (deep navy, per the design spec)
 */

// ── Primary (Google Blue family) ────────────────────────────
private val md_primary_light = Color(0xFF1A73E8)
private val md_onPrimary_light = Color(0xFFFFFFFF)
private val md_primaryContainer_light = Color(0xFFD3E3FD)
private val md_onPrimaryContainer_light = Color(0xFF041E49)

private val md_primary_dark = Color(0xFF8AB4F8)
private val md_onPrimary_dark = Color(0xFF062E6F)
private val md_primaryContainer_dark = Color(0xFF0842A0)
private val md_onPrimaryContainer_dark = Color(0xFFD3E3FD)

// ── Secondary (Sport Orange) ─────────────────────────────────
private val md_secondary_light = Color(0xFFFD7E14)
private val md_onSecondary_light = Color(0xFFFFFFFF)
private val md_secondaryContainer_light = Color(0xFFFFDCC2)
private val md_onSecondaryContainer_light = Color(0xFF2E1500)

private val md_secondary_dark = Color(0xFFFFB77C)
private val md_onSecondary_dark = Color(0xFF4A2000)
private val md_secondaryContainer_dark = Color(0xFF6A2F00)
private val md_onSecondaryContainer_dark = Color(0xFFFFDCC2)

// ── Tertiary (Teal accent) ──────────────────────────────────
private val md_tertiary_light = Color(0xFF00897B)
private val md_onTertiary_light = Color(0xFFFFFFFF)
private val md_tertiaryContainer_light = Color(0xFFA7F3D0)
private val md_onTertiaryContainer_light = Color(0xFF001A12)

private val md_tertiary_dark = Color(0xFF80CBC4)
private val md_onTertiary_dark = Color(0xFF00382E)
private val md_tertiaryContainer_dark = Color(0xFF005048)
private val md_onTertiaryContainer_dark = Color(0xFFA7F3D0)

// ── Error ────────────────────────────────────────────────────
private val md_error_light = Color(0xFFD93025)
private val md_onError_light = Color(0xFFFFFFFF)
private val md_errorContainer_light = Color(0xFFF9DEDC)
private val md_onErrorContainer_light = Color(0xFF410002)

private val md_error_dark = Color(0xFFFFB4AB)
private val md_onError_dark = Color(0xFF690005)
private val md_errorContainer_dark = Color(0xFF93000A)
private val md_onErrorContainer_dark = Color(0xFFF9DEDC)

// ── Background / Surface ─────────────────────────────────────
private val md_background_light = Color(0xFFF8F9FA)
private val md_onBackground_light = Color(0xFF202124)
private val md_surface_light = Color(0xFFFFFFFF)
private val md_onSurface_light = Color(0xFF202124)
private val md_surfaceVariant_light = Color(0xFFF1F3F9)
private val md_onSurfaceVariant_light = Color(0xFF44474E)
private val md_outline_light = Color(0xFF747775)

private val md_background_dark = Color(0xFF0F172A)
private val md_onBackground_dark = Color(0xFFE2E2E3)
private val md_surface_dark = Color(0xFF1E2433)
private val md_onSurface_dark = Color(0xFFE2E2E3)
private val md_surfaceVariant_dark = Color(0xFF2A3142)
private val md_onSurfaceVariant_dark = Color(0xFFC4C6D0)
private val md_outline_dark = Color(0xFF8E918F)

// ── Inverse ──────────────────────────────────────────────────
private val md_inverseSurface_light = Color(0xFF2F3033)
private val md_inverseOnSurface_light = Color(0xFFF1F1F1)
private val md_inversePrimary_light = Color(0xFF8AB4F8)

private val md_inverseSurface_dark = Color(0xFFE2E2E3)
private val md_inverseOnSurface_dark = Color(0xFF2F3033)
private val md_inversePrimary_dark = Color(0xFF1A73E8)

// ── Color schemes ────────────────────────────────────────────

private val BNBULightColorScheme = lightColorScheme(
    primary = md_primary_light,
    onPrimary = md_onPrimary_light,
    primaryContainer = md_primaryContainer_light,
    onPrimaryContainer = md_onPrimaryContainer_light,
    secondary = md_secondary_light,
    onSecondary = md_onSecondary_light,
    secondaryContainer = md_secondaryContainer_light,
    onSecondaryContainer = md_onSecondaryContainer_light,
    tertiary = md_tertiary_light,
    onTertiary = md_onTertiary_light,
    tertiaryContainer = md_tertiaryContainer_light,
    onTertiaryContainer = md_onTertiaryContainer_light,
    error = md_error_light,
    onError = md_onError_light,
    errorContainer = md_errorContainer_light,
    onErrorContainer = md_onErrorContainer_light,
    background = md_background_light,
    onBackground = md_onBackground_light,
    surface = md_surface_light,
    onSurface = md_onSurface_light,
    surfaceVariant = md_surfaceVariant_light,
    onSurfaceVariant = md_onSurfaceVariant_light,
    outline = md_outline_light,
    inverseSurface = md_inverseSurface_light,
    inverseOnSurface = md_inverseOnSurface_light,
    inversePrimary = md_inversePrimary_light
)

private val BNBUDarkColorScheme = darkColorScheme(
    primary = md_primary_dark,
    onPrimary = md_onPrimary_dark,
    primaryContainer = md_primaryContainer_dark,
    onPrimaryContainer = md_onPrimaryContainer_dark,
    secondary = md_secondary_dark,
    onSecondary = md_onSecondary_dark,
    secondaryContainer = md_secondaryContainer_dark,
    onSecondaryContainer = md_onSecondaryContainer_dark,
    tertiary = md_tertiary_dark,
    onTertiary = md_onTertiary_dark,
    tertiaryContainer = md_tertiaryContainer_dark,
    onTertiaryContainer = md_onTertiaryContainer_dark,
    error = md_error_dark,
    onError = md_onError_dark,
    errorContainer = md_errorContainer_dark,
    onErrorContainer = md_onErrorContainer_dark,
    background = md_background_dark,
    onBackground = md_onBackground_dark,
    surface = md_surface_dark,
    onSurface = md_onSurface_dark,
    surfaceVariant = md_surfaceVariant_dark,
    onSurfaceVariant = md_onSurfaceVariant_dark,
    outline = md_outline_dark,
    inverseSurface = md_inverseSurface_dark,
    inverseOnSurface = md_inverseOnSurface_dark,
    inversePrimary = md_inversePrimary_dark
)

// ── Legacy convenience singleton ─────────────────────────────
//
// Retained for gradual migration.  Prefer `MaterialTheme.colorScheme`
// in new code; use these only where the call-site hasn't been updated yet.

object BNBUColors {
    // Primary family
    val Ink   get() = Color(0xFF202124)        // onBackground / onSurface → #202124
    val Paper get() = Color(0xFFF8F9FA)        // background → #F8F9FA
    val Surface get() = Color.White            // surface → #FFFFFF
    val Muted get() = Color(0xFF5F6368)        // onSurfaceVariant (readable gray)
    val Line  get() = Color(0xFF747775)        // outline (M3 outline, NOT black)

    // Accent family
    val Blue      get() = Color(0xFF1A73E8)    // primary
    val BlueLight get() = Color(0xFF8AB4F8)    // primary dark-mode variant
    val BlueSoft  get() = Color(0xFFD3E3FD)    // primaryContainer (light)

    // Retained for compatibility; unused in new components
    val Pale      get() = Color(0xFFF8F9FA)
}

// ── Theme composable ─────────────────────────────────────────

@Composable
@Suppress("DEPRECATION")
fun BNBUStudentTheme(
    themeMode: AppThemeMode = AppThemeMode.Light,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
        AppThemeMode.System -> systemDark
    }
    val colorScheme = if (darkTheme) BNBUDarkColorScheme else BNBULightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BNBUTypography,
        shapes = BNBUShapes,
        content = content
    )
}
