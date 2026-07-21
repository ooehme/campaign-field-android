package de.oliveroehme.campaignfield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FieldColorScheme = darkColorScheme(
    primary = FieldCyan,
    secondary = FieldGreen,
    tertiary = FieldAmber,
    error = FieldRed,
    background = FieldBackground,
    surface = FieldPanel,
    onPrimary = FieldBackground,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = FieldMuted,
)

@Composable
fun CampaignFieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FieldColorScheme,
        typography = FieldTypography,
        content = content,
    )
}
