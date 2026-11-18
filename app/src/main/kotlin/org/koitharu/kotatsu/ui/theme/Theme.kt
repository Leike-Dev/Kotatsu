@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.koitharu.kotatsu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.koitharu.kotatsu.domain.model.AppThemeMode
import org.koitharu.kotatsu.domain.model.ReaderBackground

private val DarkColorScheme = darkColorScheme(
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
    onTertiaryContainer = AmberOnContainerDark,
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

private val LightColorScheme = lightColorScheme(
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
    onTertiaryContainer = AmberOnContainerLight,
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
    amoledDark: Boolean = false,
    dynamicColors: Boolean = false,
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
        if (useDark) DarkColorScheme else LightColorScheme
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

    CompositionLocalProvider(LocalDarkThemeActive provides useDark) {
        MaterialExpressiveTheme(
            colorScheme = finalScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = KotatsuExpressiveShapes,
            typography = KotatsuTypography,
            content = content
        )
    }
}
