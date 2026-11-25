package com.budget.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.budget.app.R
import com.budget.app.data.Accent
import com.budget.app.data.Appearance
import com.budget.app.data.ThemeMode

/**
 * Cool grays plus one accent, light or dark. The accent is the person's
 * choice (More → Appearance); Cobalt by default.
 */
@Immutable
data class BudgetColors(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    /** Chips, keypad keys, tile backgrounds. */
    val raised: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    /** Text and icons on an accent fill. */
    val onAccent: Color,
    val warn: Color,
    val warnSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val scrim: Color,
) {
    /** Older name kept for the setup screen. */
    val accentText: Color get() = accent
}

private data class Tones(val light: Color, val lightSoft: Color, val dark: Color, val darkSoft: Color, val onDark: Color)

private fun tones(accent: Accent): Tones = when (accent) {
    Accent.COBALT -> Tones(Color(0xFF2851D6), Color(0xFFE3EAFC), Color(0xFF8FB0FF), Color(0xFF1C2A4D), Color(0xFF0B1633))
    Accent.VIOLET -> Tones(Color(0xFF6D3FD1), Color(0xFFEEE7FB), Color(0xFFC3A8FF), Color(0xFF2B2148), Color(0xFF1A0F38))
    Accent.GRAPHITE -> Tones(Color(0xFF1F2937), Color(0xFFE3E6EA), Color(0xFFE8ECF1), Color(0xFF2E353F), Color(0xFF0E1116))
    Accent.FOREST -> Tones(Color(0xFF2F6B3B), Color(0xFFE3F1E5), Color(0xFF7BD88F), Color(0xFF1C3322), Color(0xFF0D2513))
    Accent.PLUM -> Tones(Color(0xFF86307A), Color(0xFFF6E4F2), Color(0xFFF0A6E2), Color(0xFF3A1F36), Color(0xFF2E0E28))
    Accent.TEAL -> Tones(Color(0xFF0F766E), Color(0xFFDDF3EF), Color(0xFF34D3BF), Color(0xFF123430), Color(0xFF04211E))
}

/** The update badge colors, the same as the Kairos app's, whatever the accent. */
val UpdateDot = Color(0xFF4FC3F7)
/** The app icon's blue, as an ARGB int for notifications. */
val BrandBlue: Int = 0xFF2851D6.toInt()
val UpdatePill = Color(0xFF2563EB)

/** The swatch shown for an accent in the picker (the light-mode tone). */
fun swatchOf(accent: Accent): Color = tones(accent).light

fun budgetColors(dark: Boolean, accent: Accent): BudgetColors {
    val t = tones(accent)
    return if (dark) {
        BudgetColors(
            dark = true,
            background = Color(0xFF0E1116), surface = Color(0xFF171B22), raised = Color(0xFF232932),
            line = Color(0xFF2A3039), ink = Color(0xFFE8ECF1), muted = Color(0xFF9AA4B2),
            accent = t.dark, accentSoft = t.darkSoft, onAccent = t.onDark,
            warn = Color(0xFFF2B04B), warnSoft = Color(0xFF3A2A10),
            danger = Color(0xFFFF8A80), dangerSoft = Color(0xFF3D1714),
            scrim = Color(0x99000000),
        )
    } else {
        BudgetColors(
            dark = false,
            background = Color(0xFFF4F6F8), surface = Color(0xFFFFFFFF), raised = Color(0xFFEEF1F4),
            line = Color(0xFFE2E6EB), ink = Color(0xFF111827), muted = Color(0xFF556070),
            accent = t.light, accentSoft = t.lightSoft, onAccent = Color.White,
            warn = Color(0xFF8A5205), warnSoft = Color(0xFFFBEFD9),
            danger = Color(0xFFB42318), dangerSoft = Color(0xFFFEE4E2),
            scrim = Color(0x73111827),
        )
    }
}

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
)

/** Every style in Figtree with tabular figures, so amounts line up in columns. */
private fun TextStyle.budget(): TextStyle = copy(fontFamily = Figtree, fontFeatureSettings = "tnum")

private fun budgetTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.budget(), displayMedium = b.displayMedium.budget(), displaySmall = b.displaySmall.budget(),
        headlineLarge = b.headlineLarge.budget(), headlineMedium = b.headlineMedium.budget(), headlineSmall = b.headlineSmall.budget(),
        titleLarge = b.titleLarge.budget(), titleMedium = b.titleMedium.budget(), titleSmall = b.titleSmall.budget(),
        bodyLarge = b.bodyLarge.budget(), bodyMedium = b.bodyMedium.budget(), bodySmall = b.bodySmall.budget(),
        labelLarge = b.labelLarge.budget(), labelMedium = b.labelMedium.budget(), labelSmall = b.labelSmall.budget(),
    )
}

val LocalBudgetColors = staticCompositionLocalOf { budgetColors(false, Accent.COBALT) }

object Budget {
    val colors: BudgetColors
        @Composable get() = LocalBudgetColors.current
}

@Composable
fun BudgetTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val dark = when (appearance.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = budgetColors(dark, appearance.accent)

    // Status and navigation bar icons follow the app's choice, not just the phone's.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
            secondaryContainer = c.accentSoft, onSecondaryContainer = c.accent,
            background = c.background, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.raised, onSurfaceVariant = c.muted, outline = c.line, outlineVariant = c.line,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface, surfaceContainerHighest = c.raised,
            error = c.danger, scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
            secondaryContainer = c.accentSoft, onSecondaryContainer = c.accent,
            background = c.background, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.raised, onSurfaceVariant = c.muted, outline = c.line, outlineVariant = c.line,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface, surfaceContainerHighest = c.raised,
            error = c.danger, scrim = c.scrim,
        )
    }
    CompositionLocalProvider(LocalBudgetColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = budgetTypography(), content = content)
    }
}
