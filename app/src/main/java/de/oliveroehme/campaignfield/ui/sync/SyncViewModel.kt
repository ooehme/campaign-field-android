package de.oliveroehme.campaignfield.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.oliveroehme.campaignfield.data.sync.SyncRepository
import de.oliveroehme.campaignfield.domain.SyncQueueItem
import de.oliveroehme.campaignfield.domain.SyncQueueSummary
import de.oliveroehme.campaignfield.domain.toSyncQueueSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SyncUiState(
    val events: List<SyncQueueItem> = emptyList(),
    val summary: SyncQueueSummary = SyncQueueSummary(),
    val retryingEventId: String? = null,
    val message: String? = null,
)

class SyncViewModel(
    private val repository: SyncRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.events.collect { events ->
                mutableState.value = mutableState.value.copy(
                    events = events,
                    summary = events.toSyncQueueSummary(),
                )
            }
        }
    }

    fun synchronize() {
        repository.synchronize()
        mutableState.value = mutableState.value.copy(
            message = "Synchronisierung wurde angefordert.",
        )
    }

    fun retry(eventId: String) {
        if (mutableState.value.retryingEventId != null) return
        mutableState.value = mutableState.value.copy(retryingEventId = eventId, message = null)
        viewModelScope.launch {
            val didRetry = repository.retry(eventId)
            mutableState.value = mutableState.value.copy(
                retryingEventId = null,
                message = if (didRetry) {
                    "Erneuter Versuch wurde gestartet."
                } else {
                    "Der Queue-Eintrag konnte nicht erneut gestartet werden."
                },
            )
        }
    }

    companion object {
        fun factory(repository: SyncRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(SyncViewModel::class.java))
                    return SyncViewModel(repository) as T
                }
            }
    }
}
