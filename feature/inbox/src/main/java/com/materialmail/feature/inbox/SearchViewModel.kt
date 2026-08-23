package com.materialmail.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.core.capability.SearchProvider
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchResultUi(
    val threadId: String,
    val senderLine: String,
    val subject: String,
    val snippet: String,
    val timeText: String,
)

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<SearchResultUi> = emptyList(),
    /** 用户已输入但结果为空（区分于未输入的初始态）。 */
    val searchedEmpty: Boolean = false,
)

class SearchViewModel(
    private val searchProvider: SearchProvider,
    private val database: MaterialMailDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _uiState.debounce(300).collectLatest { state ->
                val query = state.query.trim()
                if (query.isEmpty()) {
                    _uiState.update { it.copy(searching = false, results = emptyList(), searchedEmpty = false) }
                    return@collectLatest
                }
                _uiState.update { it.copy(searching = true) }
                val hits = runCatching { searchProvider.search(accountId = null, query = query) }
                    .getOrDefault(emptyList())
                val results = hits.mapNotNull { hit ->
                    database.threadDao().getById(hit.threadId.value)?.toModel()?.let { thread ->
                        SearchResultUi(
                            threadId = thread.id.value,
                            senderLine = thread.participants.firstOrNull()?.displayName ?: "未知发件人",
                            subject = thread.subject.ifBlank { "（无主题）" },
                            snippet = hit.snippet,
                            timeText = formatTime(thread.lastMessageAt),
                        )
                    }
                }.distinctBy { it.threadId }
                _uiState.update {
                    it.copy(searching = false, results = results, searchedEmpty = results.isEmpty())
                }
            }
        }
    }

    fun onQueryChanged(value: String) = _uiState.update { it.copy(query = value) }

    private fun formatTime(instant: Instant): String =
        instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日"))

    companion object {
        fun factory(
            searchProvider: SearchProvider,
            database: MaterialMailDatabase,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(searchProvider, database) }
        }
    }
}