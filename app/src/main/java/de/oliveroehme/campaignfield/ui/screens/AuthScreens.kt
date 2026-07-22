package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.ui.session.LoginFeedback

@Composable
fun RestoringSessionScreen(cachedProfile: UserProfile?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        cachedProfile?.let {
            Text(
                modifier = Modifier.padding(top = 20.dp),
                text = "Willkommen zurück, ${it.name}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            modifier = Modifier.padding(top = if (cachedProfile == null) 20.dp else 8.dp),
            text = "Sitzung wird geprüft …",
            style = MaterialTheme.typography.bodyLarge,
        )
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
            Text(
                text = "Campaign Field",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
                text = "Mit deinem Campaign-Konto anmelden.",
                style = MaterialTheme.typography.bodyLarge,
            )

            message?.let {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .padding(bottom = 16.dp),
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = email,
                onValueChange = {
                    email = it
                    onInputChanged()
                },
                enabled = !isLoading,
                singleLine = true,
                label = { Text("E-Mail-Adresse") },
                isError = feedback.emailError != null,
                supportingText = feedback.emailError?.let { error -> ({ Text(error) }) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() },
                ),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                value = password,
                onValueChange = {
                    password = it
                    onInputChanged()
                },
                enabled = !isLoading,
                singleLine = true,
                label = { Text("Passwort") },
                visualTransformation = PasswordVisualTransformation(),
                isError = feedback.passwordError != null,
                supportingText = feedback.passwordError?.let { error -> ({ Text(error) }) },
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
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                enabled = !isLoading,
                onClick = {
                    focusManager.clearFocus()
                    onLogin(email, password)
                },
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (isLoading) "Anmeldung läuft …" else "Anmelden")
            }
        }
    }
}

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    profile: UserProfile,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(24.dp),
    ) {
        Text(
            text = "Profil",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            modifier = Modifier.padding(top = 20.dp),
            text = profile.name,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = profile.email.ifBlank { "Keine E-Mail-Adresse angegeben" },
            style = MaterialTheme.typography.bodyLarge,
        )

        ProfileSection(title = "Rollen (nur Anzeige)") {
            Text(profile.roles.ifEmpty { listOf("Keine Rollen angegeben") }.joinToString(" · "))
        }

        ProfileSection(title = "Teams") {
            if (profile.teams.isEmpty()) {
                Text("Keine Teammitgliedschaften angegeben")
            } else {
                profile.teams.forEachIndexed { index, team ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(team.teamName, style = MaterialTheme.typography.titleMedium)
                    if (team.roles.isNotEmpty()) {
                        Text(
                            modifier = Modifier.padding(top = 4.dp),
                            text = team.roles.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            enabled = !isLoggingOut,
            onClick = onLogout,
        ) {
            if (isLoggingOut) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Abmeldung läuft …")
                }
            } else {
                Text("Abmelden")
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        text = title,
        style = MaterialTheme.typography.titleMedium,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}
