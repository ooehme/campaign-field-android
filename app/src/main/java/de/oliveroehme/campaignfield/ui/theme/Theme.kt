package de.oliveroehme.campaignfield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val FieldColorScheme = darkColorScheme(
    primary = FieldCyan,
    secondary = FieldGreen,
    tertiary = FieldAmber,
    error = FieldRed,
    background = FieldBackground,
    surface = FieldPanel,
    onPrimary = FieldBackground,
    onBackground = FieldWhite,
    onSurface = FieldWhite,
    onSurfaceVariant = FieldMuted,
    outline = FieldBorder,
)

private val FieldShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun CampaignFieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FieldColorScheme,
        typography = FieldTypography,
        shapes = FieldShapes,
        content = content,
    )
}
