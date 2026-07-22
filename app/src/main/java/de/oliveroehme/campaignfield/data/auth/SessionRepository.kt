package de.oliveroehme.campaignfield.data.auth

import de.oliveroehme.campaignfield.data.Repository
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.network.auth.SessionErrorKind
import de.oliveroehme.campaignfield.network.auth.SessionRemoteDataSource
import de.oliveroehme.campaignfield.network.auth.SessionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthState {
    data class Restoring(val cachedProfile: UserProfile?) : AuthState
    data class SignedOut(val message: String? = null) : AuthState
    data object SigningIn : AuthState
    data class SignedIn(val profile: UserProfile) : AuthState
    data class SigningOut(val profile: UserProfile) : AuthState
}

class SessionRepository(
    private val remote: SessionRemoteDataSource,
    private val profileStore: UserProfileStore,
    private val cleaner: LocalSessionCleaner,
    unauthorizedHandler: UnauthorizedSessionHandler? = null,
) : Repository {
    private val operationMutex = Mutex()
    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.Restoring(profileStore.load()))
    val authState: StateFlow<AuthState> = mutableAuthState.asStateFlow()

    init {
        unauthorizedHandler?.attach(::onUnauthorized)
    }

    suspend fun restoreSession() = operationMutex.withLock {
        if (mutableAuthState.value !is AuthState.Restoring) return@withLock
        applyAuthenticationResult(remote.checkSession())
    }

    suspend fun signIn(email: String, password: String) = operationMutex.withLock {
        mutableAuthState.value = AuthState.SigningIn
        if (!cleaner.clear()) {
            mutableAuthState.value = AuthState.SignedOut(CLEANUP_ERROR)
            return@withLock
        }
        applyAuthenticationResult(remote.signIn(email.trim(), password))
    }

    suspend fun refreshProfile() = operationMutex.withLock {
        if (mutableAuthState.value !is AuthState.SignedIn) return@withLock
        applyAuthenticationResult(remote.checkSession())
    }

    suspend fun logout() = operationMutex.withLock {
        val profile = (mutableAuthState.value as? AuthState.SignedIn)?.profile
            ?: (mutableAuthState.value as? AuthState.SigningOut)?.profile
        if (profile != null) mutableAuthState.value = AuthState.SigningOut(profile)

        val result = remote.logout()
        val cleanupSucceeded = cleaner.clear()
        val remoteFailure = (result as? SessionResult.Failure)?.error
            ?.takeUnless { it.kind == SessionErrorKind.UNAUTHORIZED }
        val message = when {
            !cleanupSucceeded -> CLEANUP_ERROR
            remoteFailure != null ->
                "Lokale Daten wurden gelöscht. Die serverseitige Abmeldung ist fehlgeschlagen: " +
                    remoteFailure.userMessage
            else -> null
        }
        mutableAuthState.value = AuthState.SignedOut(message)
    }

    fun clearMessage() {
        val current = mutableAuthState.value
        if (current is AuthState.SignedOut && current.message != null) {
            mutableAuthState.value = AuthState.SignedOut()
        }
    }

    private fun applyAuthenticationResult(result: SessionResult) {
        mutableAuthState.value = when (result) {
            is SessionResult.Authenticated -> {
                profileStore.save(result.profile)
                AuthState.SignedIn(result.profile)
            }
            SessionResult.LoggedOut -> AuthState.SignedOut()
            is SessionResult.Failure -> AuthState.SignedOut(result.error.userMessage)
        }
    }

    private fun onUnauthorized(cleanupSucceeded: Boolean) {
        mutableAuthState.value = AuthState.SignedOut(
            if (cleanupSucceeded) SESSION_EXPIRED else CLEANUP_ERROR,
        )
    }

    private companion object {
        const val SESSION_EXPIRED = "Die Sitzung ist abgelaufen. Bitte erneut anmelden."
        const val CLEANUP_ERROR =
            "Lokale Sitzungsdaten konnten nicht vollständig gelöscht werden. Bitte App-Daten zurücksetzen."
    }
}
