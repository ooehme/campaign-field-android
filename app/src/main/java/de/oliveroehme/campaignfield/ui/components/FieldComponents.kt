package de.oliveroehme.campaignfield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oliveroehme.campaignfield.ui.theme.FieldAmber
import de.oliveroehme.campaignfield.ui.theme.FieldBackground
import de.oliveroehme.campaignfield.ui.theme.FieldBlackOverlay
import de.oliveroehme.campaignfield.ui.theme.FieldBorder
import de.oliveroehme.campaignfield.ui.theme.FieldCyan
import de.oliveroehme.campaignfield.ui.theme.FieldGreen
import de.oliveroehme.campaignfield.ui.theme.FieldMuted
import de.oliveroehme.campaignfield.ui.theme.FieldPanel
import de.oliveroehme.campaignfield.ui.theme.FieldRed
import de.oliveroehme.campaignfield.ui.theme.FieldWhite
import de.oliveroehme.campaignfield.network.CoreApiStatus
import de.oliveroehme.campaignfield.ui.status.LocationAccessState

val FieldShape = RoundedCornerShape(8.dp)

@Composable
fun FieldGridBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldBackground)
            .drawBehind {
                val spacing = 32.dp.toPx()
                var offset = 0f
                while (offset <= size.height) {
                    drawLine(
                        color = FieldCyan.copy(alpha = 0.05f),
                        start = androidx.compose.ui.geometry.Offset(0f, offset),
                        end = androidx.compose.ui.geometry.Offset(size.width, offset),
                        strokeWidth = 1.dp.toPx(),
                    )
                    offset += spacing
                }
                offset = 0f
                while (offset <= size.width) {
                    drawLine(
                        color = FieldCyan.copy(alpha = 0.04f),
                        start = androidx.compose.ui.geometry.Offset(offset, 0f),
                        end = androidx.compose.ui.geometry.Offset(offset, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    offset += spacing
                }
            },
    ) {
        content()
    }
}

@Composable
fun FieldHeader(
    coreApiStatus: CoreApiStatus,
    locationAccessState: LocationAccessState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FieldPanel)
            .drawBehind {
                drawLine(
                    color = FieldBorder,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                FieldEyebrow(text = "Campaign Field", letterSpacing = 2.64.sp)
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = "Operativ",
                    color = FieldCyan,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderStatusIcon(
                    icon = if (locationAccessState.hasPosition) {
                        FieldIcons.MapPinCheck
                    } else {
                        FieldIcons.MapPinX
                    },
                    tint = when {
                        locationAccessState.hasPosition -> FieldGreen
                        locationAccessState.isRequesting -> FieldCyan
                        else -> FieldAmber
                    },
                    contentDescription = locationAccessState.statusLabel,
                    isLoading = locationAccessState.isRequesting,
                    onClick = locationAccessState.requestPosition,
                )
                HeaderStatusIcon(
                    icon = FieldIcons.Server,
                    tint = when (coreApiStatus) {
                        CoreApiStatus.Checking -> FieldCyan
                        CoreApiStatus.Reachable -> FieldGreen
                        CoreApiStatus.Unreachable -> FieldAmber
                    },
                    contentDescription = when (coreApiStatus) {
                        CoreApiStatus.Checking -> "Core API wird geprüft"
                        CoreApiStatus.Reachable -> "Core API erreichbar"
                        CoreApiStatus.Unreachable -> "Core API nicht erreichbar"
                    },
                    isLoading = coreApiStatus == CoreApiStatus.Checking,
                )
            }
        }
    }
}

@Composable
private fun HeaderStatusIcon(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.50f), CircleShape)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = tint,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

@Composable
fun FieldEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 2.4.sp,
) {
    Text(
        modifier = modifier,
        text = text.uppercase(),
        color = FieldMuted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = letterSpacing,
    )
}

@Composable
fun FieldPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = FieldBorder,
    backgroundColor: Color = FieldPanel,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(FieldShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, FieldShape)
            .padding(padding),
        content = content,
    )
}

enum class FieldStatusTone {
    Neutral,
    Ready,
    Active,
    Warning,
    Danger,
}

@Composable
fun FieldStatusPill(
    label: String,
    modifier: Modifier = Modifier,
    tone: FieldStatusTone = FieldStatusTone.Neutral,
) {
    val tint = when (tone) {
        FieldStatusTone.Ready -> FieldGreen
        FieldStatusTone.Active -> FieldCyan
        FieldStatusTone.Warning -> FieldAmber
        FieldStatusTone.Danger -> FieldRed
        FieldStatusTone.Neutral -> FieldMuted
    }
    val border = if (tone == FieldStatusTone.Neutral) FieldBorder else tint.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 28.dp)
            .clip(CircleShape)
            .background(if (tone == FieldStatusTone.Neutral) Color.White.copy(alpha = 0.05f) else tint.copy(alpha = 0.10f))
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

enum class FieldButtonVariant {
    Primary,
    Secondary,
    Danger,
    Ghost,
}

@Composable
fun FieldActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    variant: FieldButtonVariant = FieldButtonVariant.Primary,
) {
    val tint = when (variant) {
        FieldButtonVariant.Primary -> FieldCyan
        FieldButtonVariant.Secondary -> FieldCyan
        FieldButtonVariant.Danger -> FieldRed
        FieldButtonVariant.Ghost -> FieldMuted
    }
    val borderColor = when (variant) {
        FieldButtonVariant.Primary -> FieldCyan
        FieldButtonVariant.Secondary -> FieldBorder
        FieldButtonVariant.Danger -> FieldRed.copy(alpha = 0.60f)
        FieldButtonVariant.Ghost -> Color.Transparent
    }
    val background = when (variant) {
        FieldButtonVariant.Primary -> FieldCyan.copy(alpha = 0.15f)
        FieldButtonVariant.Secondary -> FieldBlackOverlay.copy(alpha = 0.20f)
        FieldButtonVariant.Danger -> FieldRed.copy(alpha = 0.15f)
        FieldButtonVariant.Ghost -> Color.Transparent
    }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(FieldShape)
            .background(background)
            .border(1.dp, borderColor, FieldShape)
            .clickable(
                enabled = enabled && !isLoading,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = tint,
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
        }
        if (isLoading || icon != null) Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            color = if (enabled) tint else tint.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun FieldIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FieldCyan,
    enabled: Boolean = true,
    size: Dp = 36.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(FieldShape)
            .border(1.dp, FieldBorder, FieldShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else FieldMuted.copy(alpha = 0.55f),
        )
    }
}

@Composable
fun FieldMessagePanel(
    message: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.40f), FieldShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = message, color = tint, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FieldEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    FieldPanel(modifier = modifier.fillMaxWidth()) {
        Text(text = title, color = FieldWhite, style = MaterialTheme.typography.titleSmall)
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = message,
            color = FieldMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
