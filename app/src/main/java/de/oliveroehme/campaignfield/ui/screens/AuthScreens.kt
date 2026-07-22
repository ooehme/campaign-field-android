package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.ui.components.FieldActionButton
import de.oliveroehme.campaignfield.ui.components.FieldButtonVariant
import de.oliveroehme.campaignfield.ui.components.FieldEyebrow
import de.oliveroehme.campaignfield.ui.components.FieldIcons
import de.oliveroehme.campaignfield.ui.components.FieldMessagePanel
import de.oliveroehme.campaignfield.ui.components.FieldPanel
import de.oliveroehme.campaignfield.ui.components.FieldShape
import de.oliveroehme.campaignfield.ui.components.FieldStatusPill
import de.oliveroehme.campaignfield.ui.components.FieldStatusTone
import de.oliveroehme.campaignfield.ui.session.LoginFeedback
import de.oliveroehme.campaignfield.ui.theme.FieldBlackOverlay
import de.oliveroehme.campaignfield.ui.theme.FieldBorder
import de.oliveroehme.campaignfield.ui.theme.FieldCyan
import de.oliveroehme.campaignfield.ui.theme.FieldMuted
import de.oliveroehme.campaignfield.ui.theme.FieldPanel
import de.oliveroehme.campaignfield.ui.theme.FieldRed
import de.oliveroehme.campaignfield.ui.theme.FieldWhite

@Composable
fun RestoringSessionScreen(cachedProfile: UserProfile?) {
    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        FieldPanel(
            modifier = Modifier.fillMaxWidth().widthIn(max = 384.dp),
            padding = 20.dp,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp),
                color = FieldCyan,
                strokeWidth = 2.dp,
            )
            Text(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                text = cachedProfile?.let { "Willkommen zurück, ${it.name}" } ?: "Campaign Field",
                color = FieldWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = "Sitzung wird geprüft …",
                color = FieldMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun LoginScreen(
    isLoading: Boolean,
    message: String?,
    feedback: LoginFeedback,
    onInputChanged: () -> Unit,
    onLogin: (email: String, password: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        FieldPanel(
            modifier = Modifier.fillMaxWidth().widthIn(max = 384.dp),
            padding = 20.dp,
        ) {
            FieldEyebrow(text = "Campaign Field", letterSpacing = androidx.compose.ui.unit.TextUnit(2.64f, androidx.compose.ui.unit.TextUnitType.Sp))
            Text(
                modifier = Modifier.padding(top = 20.dp),
                text = "Login",
                color = FieldWhite,
                style = MaterialTheme.typography.headlineLarge,
            )
            FieldInput(
                modifier = Modifier.padding(top = 24.dp),
                label = "E-Mail",
                value = email,
                onValueChange = {
                    email = it
                    onInputChanged()
                },
                enabled = !isLoading,
                error = feedback.emailError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
            )
            FieldInput(
                modifier = Modifier.padding(top = 16.dp).focusRequester(passwordFocusRequester),
                label = "Passwort",
                value = password,
                onValueChange = {
                    password = it
                    onInputChanged()
                },
                enabled = !isLoading,
                error = feedback.passwordError,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onLogin(email, password)
                    },
                ),
            )
            FieldActionButton(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                text = "Anmelden",
                isLoading = isLoading,
                enabled = !isLoading,
                onClick = {
                    focusManager.clearFocus()
                    onLogin(email, password)
                },
            )
            message?.let {
                FieldMessagePanel(
                    modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    message = it,
                    tint = FieldRed,
                )
            }
        }
    }
}

@Composable
private fun FieldInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (error == null) FieldMuted else FieldRed,
            style = MaterialTheme.typography.bodyMedium,
        )
        BasicTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(48.dp)
                .clip(FieldShape)
                .background(FieldBlackOverlay)
                .border(1.dp, if (error == null) FieldBorder else FieldRed.copy(alpha = 0.65f), FieldShape),
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = FieldWhite)),
            cursorBrush = SolidColor(FieldCyan),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { innerTextField() }
            },
        )
        error?.let {
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = it,
                color = FieldRed,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    profile: UserProfile,
    isRefreshingProfile: Boolean,
    isLoggingOut: Boolean,
    onRefreshProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            FieldEyebrow("Profil")
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = profile.name.ifBlank { "Benutzer" },
                color = FieldWhite,
                style = MaterialTheme.typography.headlineMedium,
            )
            if (profile.email.isNotBlank()) {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = profile.email,
                    color = FieldMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        FieldPanel(modifier = Modifier.fillMaxWidth()) {
            ProfileDetailRow("Benutzer-ID", profile.id ?: "-")
            ProfileDetailRow(
                modifier = Modifier.padding(top = 12.dp),
                label = "App-Rolle",
                value = profile.appRole?.formatRole() ?: "Keine Rolle",
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    FieldEyebrow("Teamzugehörigkeit", letterSpacing = androidx.compose.ui.unit.TextUnit(1.68f, androidx.compose.ui.unit.TextUnitType.Sp))
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = FieldIcons.UsersRound,
                            contentDescription = null,
                            tint = FieldCyan,
                        )
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = "Teams",
                            color = FieldWhite,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                FieldStatusPill(
                    label = if (profile.teams.size == 1) "1 Team" else "${profile.teams.size} Teams",
                    tone = if (profile.teams.isEmpty()) FieldStatusTone.Neutral else FieldStatusTone.Active,
                )
            }
            if (profile.teams.isEmpty()) {
                FieldPanel(modifier = Modifier.fillMaxWidth()) {
                    Text("Keine Teamzugehörigkeit hinterlegt.", color = FieldMuted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                profile.teams.forEach { team ->
                    FieldPanel(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = FieldCyan.copy(alpha = 0.35f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FieldCyan.copy(alpha = 0.10f))
                                    .border(1.dp, FieldCyan.copy(alpha = 0.40f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = team.teamName.initials("T"),
                                    color = FieldCyan,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        modifier = Modifier.weight(1f, fill = false),
                                        text = team.teamName,
                                        color = FieldWhite,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    if (team.isCurrent) {
                                        FieldStatusPill(
                                            modifier = Modifier.padding(start = 8.dp),
                                            label = "Aktuell",
                                            tone = FieldStatusTone.Ready,
                                        )
                                    }
                                }
                                Text(
                                    modifier = Modifier.padding(top = 4.dp),
                                    text = team.teamId?.let { "Team #$it" } ?: "Team",
                                    color = FieldMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            FieldStatusPill(
                                label = team.role?.formatRole() ?: "Mitglied",
                                tone = if (team.role.isTeamLeadRole()) FieldStatusTone.Warning else FieldStatusTone.Neutral,
                            )
                        }
                    }
                }
            }
        }

        FieldActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = if (isRefreshingProfile) "Profil wird aktualisiert …" else "Profil aktualisieren",
            icon = FieldIcons.RefreshCcw,
            isLoading = isRefreshingProfile,
            enabled = !isRefreshingProfile && !isLoggingOut,
            variant = FieldButtonVariant.Secondary,
            onClick = onRefreshProfile,
        )

        FieldActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = if (isLoggingOut) "Logout läuft …" else "Logout",
            icon = FieldIcons.LogOut,
            isLoading = isLoggingOut,
            enabled = !isLoggingOut,
            variant = FieldButtonVariant.Secondary,
            onClick = onLogout,
        )
    }
}

@Composable
private fun ProfileDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(modifier = Modifier.width(120.dp), text = label, color = FieldMuted, style = MaterialTheme.typography.bodyMedium)
        Text(modifier = Modifier.weight(1f), text = value, color = FieldWhite, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun String.formatRole(): String = when (trim().lowercase().replace('-', '_')) {
    "user", "app_user" -> "Benutzer"
    "admin", "app_admin" -> "Administrator"
    "lead", "teamlead", "team_lead" -> "Teamlead"
    "member" -> "Mitglied"
    else -> replace('_', ' ')
}

private fun String?.isTeamLeadRole(): Boolean = this
    ?.trim()
    ?.lowercase()
    ?.replace(Regex("[-\\s]+"), "_")
    .let { it == "lead" || it == "leader" || it == "teamlead" || it == "team_lead" || it == "team_leader" }

private fun String.initials(fallback: String): String = trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercase() }
    .joinToString("")
    .ifBlank { fallback }
