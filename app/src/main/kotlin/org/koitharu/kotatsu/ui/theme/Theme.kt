@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.koitharu.kotatsu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koitharu.kotatsu.domain.model.AppColorPalette
import org.koitharu.kotatsu.domain.model.AppThemeMode
import org.koitharu.kotatsu.domain.model.CoverCornerStyle
import org.koitharu.kotatsu.domain.model.ReaderBackground

private fun buildDarkPaletteScheme(palette: AppColorPalette): ColorScheme {
    val base = darkColorScheme(
        background = SumiBackground,
        onBackground = SumiOnBackground,
        surface = SumiSurface,
        onSurface = SumiOnBackground,
        surfaceVariant = SumiSurfaceContainer,
        onSurfaceVariant = SumiOnSurfaceVariant,
        surfaceContainerLowest = SumiBackground,
        surfaceContainerLow = SumiSurfaceLow,
        surfaceContainer = SumiSurfaceContainer,
        surfaceContainerHigh = SumiSurfaceHigh,
        surfaceContainerHighest = SumiSurfaceHighest,
        outline = SumiOutline,
        scrim = Color.Black
    )
    return when (palette) {
        AppColorPalette.KOTATSU_VERMILION -> base.copy(
            primary = VermilionPrimaryDark,
            onPrimary = VermilionOnPrimaryDark,
            primaryContainer = VermilionContainerDark,
            onPrimaryContainer = VermilionOnContainerDark,
            secondary = PlumSecondaryDark,
            onSecondary = PlumOnSecondaryDark,
            secondaryContainer = PlumContainerDark,
            onSecondaryContainer = PlumOnContainerDark,
            tertiary = AmberTertiaryDark,
            onTertiary = AmberOnTertiaryDark,
            tertiaryContainer = AmberContainerDark,
            onTertiaryContainer = AmberOnContainerDark
        )
        AppColorPalette.SAKURA_CRIMSON -> base.copy(
            primary = SakuraPrimaryDark,
            onPrimary = SakuraOnPrimaryDark,
            primaryContainer = SakuraContainerDark,
            onPrimaryContainer = SakuraOnContainerDark,
            secondary = SakuraSecondaryDark,
            onSecondary = Color(0xFF471E35),
            secondaryContainer = SakuraContainerSecDark,
            onSecondaryContainer = Color(0xFFFFD8EC),
            tertiary = SakuraTertiaryDark,
            onTertiary = Color(0xFF4B2700),
            tertiaryContainer = SakuraContainerTerDark,
            onTertiaryContainer = Color(0xFFFFDCC5)
        )
        AppColorPalette.OCEAN_SAPPHIRE -> base.copy(
            primary = OceanPrimaryDark,
            onPrimary = OceanOnPrimaryDark,
            primaryContainer = OceanContainerDark,
            onPrimaryContainer = OceanOnContainerDark,
            secondary = OceanSecondaryDark,
            onSecondary = Color(0xFF1D324B),
            secondaryContainer = OceanContainerSecDark,
            onSecondaryContainer = Color(0xFFD4E4FF),
            tertiary = OceanTertiaryDark,
            onTertiary = Color(0xFF003829),
            tertiaryContainer = OceanContainerTerDark,
            onTertiaryContainer = Color(0xFF83FBD4)
        )
        AppColorPalette.EMERALD_JADE -> base.copy(
            primary = EmeraldPrimaryDark,
            onPrimary = EmeraldOnPrimaryDark,
            primaryContainer = EmeraldContainerDark,
            onPrimaryContainer = EmeraldOnContainerDark,
            secondary = EmeraldSecondaryDark,
            onSecondary = Color(0xFF213527),
            secondaryContainer = EmeraldContainerSecDark,
            onSecondaryContainer = Color(0xFFD0E8D4),
            tertiary = EmeraldTertiaryDark,
            onTertiary = Color(0xFF402D00),
            tertiaryContainer = EmeraldContainerTerDark,
            onTertiaryContainer = Color(0xFFFFDF9E)
        )
        AppColorPalette.ROYAL_AMETHYST -> base.copy(
            primary = AmethystPrimaryDark,
            onPrimary = AmethystOnPrimaryDark,
            primaryContainer = AmethystContainerDark,
            onPrimaryContainer = AmethystOnContainerDark,
            secondary = AmethystSecondaryDark,
            onSecondary = Color(0xFF4E1949),
            secondaryContainer = AmethystContainerSecDark,
            onSecondaryContainer = Color(0xFFFFD7F4),
            tertiary = AmethystTertiaryDark,
            onTertiary = Color(0xFF561E17),
            tertiaryContainer = AmethystContainerTerDark,
            onTertiaryContainer = Color(0xFFFFDAD4)
        )
        AppColorPalette.SOLAR_AMBER -> base.copy(
            primary = SolarPrimaryDark,
            onPrimary = SolarOnPrimaryDark,
            primaryContainer = SolarContainerDark,
            onPrimaryContainer = SolarOnContainerDark,
            secondary = SolarSecondaryDark,
            onSecondary = Color(0xFF422C15),
            secondaryContainer = SolarContainerSecDark,
            onSecondaryContainer = Color(0xFFFFDCC0),
            tertiary = SolarTertiaryDark,
            onTertiary = Color(0xFF003733),
            tertiaryContainer = SolarContainerTerDark,
            onTertiaryContainer = Color(0xFF8AF3EA)
        )
    }
}

private fun buildLightPaletteScheme(palette: AppColorPalette): ColorScheme {
    val base = lightColorScheme(
        background = PaperBackground,
        onBackground = PaperOnBackground,
        surface = PaperSurface,
        onSurface = PaperOnBackground,
        surfaceVariant = PaperSurfaceContainer,
        onSurfaceVariant = PaperOnSurfaceVariant,
        surfaceContainerLowest = PaperSurface,
        surfaceContainerLow = PaperSurfaceLow,
        surfaceContainer = PaperSurfaceContainer,
        surfaceContainerHigh = PaperSurfaceHigh,
        surfaceContainerHighest = PaperSurfaceHighest,
        outline = PaperOutline,
        scrim = Color.Black
    )
    return when (palette) {
        AppColorPalette.KOTATSU_VERMILION -> base.copy(
            primary = VermilionPrimaryLight,
            onPrimary = VermilionOnPrimaryLight,
            primaryContainer = VermilionContainerLight,
            onPrimaryContainer = VermilionOnContainerLight,
            secondary = PlumSecondaryLight,
            onSecondary = PlumOnSecondaryLight,
            secondaryContainer = PlumContainerLight,
            onSecondaryContainer = PlumOnContainerLight,
            tertiary = AmberTertiaryLight,
            onTertiary = AmberOnTertiaryLight,
            tertiaryContainer = AmberContainerLight,
            onTertiaryContainer = AmberOnContainerLight
        )
        AppColorPalette.SAKURA_CRIMSON -> base.copy(
            primary = SakuraPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = SakuraContainerLight,
            onPrimaryContainer = Color(0xFF3E001B),
            secondary = SakuraSecondaryLight,
            onSecondary = Color.White,
            secondaryContainer = SakuraContainerSecLight,
            onSecondaryContainer = Color(0xFF310B21),
            tertiary = SakuraTertiaryLight,
            onTertiary = Color.White,
            tertiaryContainer = SakuraContainerTerLight,
            onTertiaryContainer = Color(0xFF2F1500)
        )
        AppColorPalette.OCEAN_SAPPHIRE -> base.copy(
            primary = OceanPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = OceanContainerLight,
            onPrimaryContainer = Color(0xFF001E2B),
            secondary = OceanSecondaryLight,
            onSecondary = Color.White,
            secondaryContainer = OceanContainerSecLight,
            onSecondaryContainer = Color(0xFF071D35),
            tertiary = OceanTertiaryLight,
            onTertiary = Color.White,
            tertiaryContainer = OceanContainerTerLight,
            onTertiaryContainer = Color(0xFF002117)
        )
        AppColorPalette.EMERALD_JADE -> base.copy(
            primary = EmeraldPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = EmeraldContainerLight,
            onPrimaryContainer = Color(0xFF002110),
            secondary = EmeraldSecondaryLight,
            onSecondary = Color.White,
            secondaryContainer = EmeraldContainerSecLight,
            onSecondaryContainer = Color(0xFF0B1F13),
            tertiary = EmeraldTertiaryLight,
            onTertiary = Color.White,
            tertiaryContainer = EmeraldContainerTerLight,
            onTertiaryContainer = Color(0xFF261A00)
        )
        AppColorPalette.ROYAL_AMETHYST -> base.copy(
            primary = AmethystPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = AmethystContainerLight,
            onPrimaryContainer = Color(0xFF21005D),
            secondary = AmethystSecondaryLight,
            onSecondary = Color.White,
            secondaryContainer = AmethystContainerSecLight,
            onSecondaryContainer = Color(0xFF360434),
            tertiary = AmethystTertiaryLight,
            onTertiary = Color.White,
            tertiaryContainer = AmethystContainerTerLight,
            onTertiaryContainer = Color(0xFF3A0905)
        )
        AppColorPalette.SOLAR_AMBER -> base.copy(
            primary = SolarPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = SolarContainerLight,
            onPrimaryContainer = Color(0xFF291800),
            secondary = SolarSecondaryLight,
            onSecondary = Color.White,
            secondaryContainer = SolarContainerSecLight,
            onSecondaryContainer = Color(0xFF2B1704),
            tertiary = SolarTertiaryLight,
            onTertiary = Color.White,
            tertiaryContainer = SolarContainerTerLight,
            onTertiaryContainer = Color(0xFF00201E)
        )
    }
}

val KotatsuExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Immutable
data class ReaderSurfacePalette(
    val pageBackground: Color,
    val pageContent: Color,
    val hudContainer: Color,
    val hudContent: Color
)

@Immutable
data class CoverVisualConfig(
    val cornerStyle: CoverCornerStyle = CoverCornerStyle.EXPRESSIVE,
    val showUnreadBadges: Boolean = true,
    val showProgressBars: Boolean = true
)

val LocalCoverVisualConfig = staticCompositionLocalOf { CoverVisualConfig() }

fun resolveReaderPalette(background: ReaderBackground): ReaderSurfacePalette = when (background) {
    ReaderBackground.AMOLED -> ReaderSurfacePalette(
        pageBackground = ReaderAmoledSurface,
        pageContent = ReaderAmoledContent,
        hudContainer = ReaderHudScrim,
        hudContent = ReaderHudContent
    )
    ReaderBackground.SUMI_INK -> ReaderSurfacePalette(
        pageBackground = ReaderInkSurface,
        pageContent = ReaderInkContent,
        hudContainer = ReaderHudScrim,
        hudContent = ReaderHudContent
    )
    ReaderBackground.SEPIA -> ReaderSurfacePalette(
        pageBackground = ReaderSepiaSurface,
        pageContent = ReaderSepiaContent,
        hudContainer = ReaderHudScrim,
        hudContent = ReaderHudContent
    )
    ReaderBackground.PAPER_LIGHT -> ReaderSurfacePalette(
        pageBackground = ReaderLightSurface,
        pageContent = ReaderLightContent,
        hudContainer = ReaderHudScrim,
        hudContent = ReaderHudContent
    )
}

val LocalDarkThemeActive = staticCompositionLocalOf { true }

@Composable
fun KotatsuTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    colorPalette: AppColorPalette = AppColorPalette.KOTATSU_VERMILION,
    amoledDark: Boolean = false,
    dynamicColors: Boolean = false,
    coverVisualConfig: CoverVisualConfig = CoverVisualConfig(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val baseScheme = if (dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (useDark) buildDarkPaletteScheme(colorPalette) else buildLightPaletteScheme(colorPalette)
    }

    val finalScheme = if (useDark && amoledDark) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0C10),
            surfaceContainer = Color(0xFF13151D)
        )
    } else {
        baseScheme
    }

    CompositionLocalProvider(
        LocalDarkThemeActive provides useDark,
        LocalCoverVisualConfig provides coverVisualConfig
    ) {
        MaterialExpressiveTheme(
            colorScheme = finalScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = KotatsuExpressiveShapes,
            typography = KotatsuTypography,
            content = content
        )
    }
}
