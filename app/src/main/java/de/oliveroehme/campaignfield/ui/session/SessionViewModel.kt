package de.oliveroehme.campaignfield.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.oliveroehme.campaignfield.data.auth.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginFeedback(
    val emailError: String? = null,
    val passwordError: String? = null,
)

class SessionViewModel(
    private val repository: SessionRepository,
) : ViewModel() {
    val authState = repository.authState

    private val mutableLoginFeedback = MutableStateFlow(LoginFeedback())
    val loginFeedback: StateFlow<LoginFeedback> = mutableLoginFeedback.asStateFlow()
    private val mutableIsRefreshingProfile = MutableStateFlow(false)
    val isRefreshingProfile: StateFlow<Boolean> = mutableIsRefreshingProfile.asStateFlow()
    private var signInJob: Job? = null
    private var logoutJob: Job? = null
    private var refreshProfileJob: Job? = null

    init {
        viewModelScope.launch { repository.restoreSession() }
    }

    fun signIn(email: String, password: String) {
        if (signInJob?.isActive == true) return
        val validation = validateLogin(email, password)
        mutableLoginFeedback.value = validation
        if (validation != LoginFeedback()) return

        signInJob = viewModelScope.launch { repository.signIn(email, password) }
    }

    fun logout() {
        if (logoutJob?.isActive == true) return
        logoutJob = viewModelScope.launch { repository.logout() }
    }

    fun refreshProfile() {
        if (refreshProfileJob?.isActive == true) return
        refreshProfileJob = viewModelScope.launch {
            mutableIsRefreshingProfile.value = true
            try {
                repository.refreshProfile()
            } finally {
                mutableIsRefreshingProfile.value = false
            }
        }
    }

    fun clearLoginFeedback() {
        if (mutableLoginFeedback.value != LoginFeedback()) {
            mutableLoginFeedback.value = LoginFeedback()
        }
        repository.clearMessage()
    }

    companion object {
        internal fun validateLogin(email: String, password: String): LoginFeedback = LoginFeedback(
            emailError = when {
                email.isBlank() -> "E-Mail-Adresse eingeben."
                !EMAIL_PATTERN.matches(email.trim()) -> "Gültige E-Mail-Adresse eingeben."
                else -> null
            },
            passwordError = if (password.isBlank()) "Passwort eingeben." else null,
        )

        fun factory(repository: SessionRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(SessionViewModel::class.java))
                    return SessionViewModel(repository) as T
                }
            }

        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
