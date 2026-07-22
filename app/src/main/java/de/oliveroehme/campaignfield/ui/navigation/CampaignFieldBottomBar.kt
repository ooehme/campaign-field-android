package de.oliveroehme.campaignfield.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.oliveroehme.campaignfield.ui.theme.FieldBorder
import de.oliveroehme.campaignfield.ui.theme.FieldCyan
import de.oliveroehme.campaignfield.ui.theme.FieldMuted
import de.oliveroehme.campaignfield.ui.theme.FieldPanel

@Composable
fun CampaignFieldBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FieldPanel)
            .drawBehind {
                drawLine(
                    color = FieldBorder,
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppDestination.shellItems.forEach { destination ->
            val selected = currentRoute == destination.route ||
                (destination == AppDestination.Assignments && currentRoute == AppDestination.AssignmentDetail.route)
            val shape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .clip(shape)
                    .background(if (selected) FieldCyan.copy(alpha = 0.10f) else Color.Transparent)
                    .border(1.dp, if (selected) FieldCyan else Color.Transparent, shape)
                    .clickable {
                        navController.navigate(destination.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(AppDestination.Assignments.route) { saveState = true }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = destination.icon,
                        contentDescription = null,
                        tint = if (selected) FieldCyan else FieldMuted,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = destination.label,
                        color = if (selected) FieldCyan else FieldMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
