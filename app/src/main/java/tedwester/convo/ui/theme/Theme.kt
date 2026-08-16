package tedwester.convo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = DarkUserBubbleOn,
    secondary = DarkIconButton,
    onSecondary = DarkOnBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnSurface,
    outline = DarkOutline,
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = LightUserBubbleOn,
    secondary = LightIconButton,
    onSecondary = LightOnBackground,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightOnSurface,
    outline = LightOutline,
)

@Composable
fun ConvoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    shapes: Shapes = ConvoShapes,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = shapes,
        content = content,
    )
}
