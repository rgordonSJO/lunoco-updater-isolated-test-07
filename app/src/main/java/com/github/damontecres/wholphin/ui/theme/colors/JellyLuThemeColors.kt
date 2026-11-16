package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.ui.theme.ThemeColors

// Static schemes: changing focus never constructs a palette or decodes brand artwork.
val JellyLuThemeColors: ThemeColors =
    object : ThemeColors {
        private val charcoal = Color(0xFF0D1117)
        private val ivory = Color(0xFFF1F0EC)
        private val slate = LunocoUiColors.Accent

        override val darkSchemeMaterial =
            androidx.compose.material3.darkColorScheme(
                primary = slate,
                onPrimary = charcoal,
                primaryContainer = Color(0xFF2B3A49),
                onPrimaryContainer = ivory,
                secondary = Color(0xFFC6D0DA),
                onSecondary = charcoal,
                secondaryContainer = Color(0xFF29323C),
                onSecondaryContainer = ivory,
                tertiary = LunocoUiColors.Accent,
                onTertiary = charcoal,
                tertiaryContainer = Color(0xFF2D3B44),
                onTertiaryContainer = ivory,
                background = charcoal,
                onBackground = ivory,
                surface = charcoal,
                onSurface = ivory,
                surfaceVariant = Color(0xFF252E38),
                onSurfaceVariant = Color(0xFFBAC5CF),
                outline = Color(0xFF85929F),
                outlineVariant = Color(0xFF3D4854),
                surfaceTint = Color.Transparent,
                inverseSurface = ivory,
                inverseOnSurface = charcoal,
                inversePrimary = Color(0xFF405C73),
                surfaceDim = charcoal,
                surfaceBright = Color(0xFF303A45),
                surfaceContainerLowest = Color(0xFF080C11),
                surfaceContainerLow = Color(0xFF131A22),
                surfaceContainer = Color(0xFF19222C),
                surfaceContainerHigh = Color(0xFF202A35),
                surfaceContainerHighest = Color(0xFF252E38),
            )

        override val lightSchemeMaterial =
            androidx.compose.material3.lightColorScheme(
                primary = Color(0xFF405C73),
                onPrimary = ivory,
                primaryContainer = Color(0xFFD1DFEC),
                onPrimaryContainer = charcoal,
                secondary = Color(0xFF536474),
                onSecondary = ivory,
                secondaryContainer = Color(0xFFDCE4EC),
                onSecondaryContainer = charcoal,
                tertiary = Color(0xFF4D6574),
                onTertiary = ivory,
                tertiaryContainer = Color(0xFFD7E6EF),
                onTertiaryContainer = charcoal,
                background = ivory,
                onBackground = charcoal,
                surface = ivory,
                onSurface = charcoal,
                surfaceVariant = Color(0xFFDCE4EC),
                onSurfaceVariant = Color(0xFF3E4B57),
                outline = Color(0xFF73808D),
                surfaceTint = Color.Transparent,
            )

        override val darkScheme = darkSchemeMaterial.toJellyLuTvScheme()
        override val lightScheme = lightSchemeMaterial.toJellyLuTvScheme()
    }

private fun ColorScheme.toJellyLuTvScheme() =
    androidx.tv.material3.darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        scrim = scrim,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        border = LunocoUiColors.FocusOutline,
    )
