package de.oliveroehme.campaignfield.network.auth

enum class SessionErrorKind {
    NETWORK,
    UNAUTHORIZED,
    FORBIDDEN,
    VALIDATION,
    CSRF,
    SERVER,
    INVALID_RESPONSE,
    UNEXPECTED,
}

data class SessionFailure(
    val stage: SessionStage,
    val httpStatus: Int?,
    val kind: SessionErrorKind,
    val userMessage: String,
)

internal object SessionErrorNormalizer {
    fun from(stage: SessionStage, status: Int?): SessionFailure {
        val kind = when {
            status == null -> SessionErrorKind.NETWORK
            status == 401 -> SessionErrorKind.UNAUTHORIZED
            status == 403 -> SessionErrorKind.FORBIDDEN
            status == 419 -> SessionErrorKind.CSRF
            status == 422 -> SessionErrorKind.VALIDATION
            status in 500..599 -> SessionErrorKind.SERVER
            else -> SessionErrorKind.UNEXPECTED
        }
        val message = when (kind) {
            SessionErrorKind.NETWORK -> "Keine Verbindung zum Server. Bitte Netzwerk prüfen und erneut versuchen."
            SessionErrorKind.UNAUTHORIZED -> if (stage == SessionStage.LOGIN) {
                "E-Mail-Adresse oder Passwort ist falsch."
            } else {
                "Die Sitzung ist abgelaufen. Bitte erneut anmelden."
            }
            SessionErrorKind.FORBIDDEN -> "Keine Berechtigung für diese Aktion."
            SessionErrorKind.VALIDATION -> "Die Eingaben wurden vom Server abgelehnt. Bitte prüfen."
            SessionErrorKind.CSRF -> "Die Sicherheitsprüfung ist abgelaufen. Bitte erneut versuchen."
            SessionErrorKind.SERVER -> "Der Server ist vorübergehend nicht verfügbar. Bitte später erneut versuchen."
            SessionErrorKind.INVALID_RESPONSE,
            SessionErrorKind.UNEXPECTED,
            -> "Die Serverantwort konnte nicht verarbeitet werden. Bitte erneut versuchen."
        }
        return SessionFailure(stage, status, kind, message)
    }

    fun invalidResponse(stage: SessionStage): SessionFailure = SessionFailure(
        stage = stage,
        httpStatus = 200,
        kind = SessionErrorKind.INVALID_RESPONSE,
        userMessage = "Die Serverantwort konnte nicht verarbeitet werden. Bitte erneut versuchen.",
    )
}
