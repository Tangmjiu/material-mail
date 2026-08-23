package com.materialmail.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toModel
import com.materialmail.core.model.SyncState
import com.materialmail.core.model.Thread
import com.materialmail.core.model.ThreadId
import com.materialmail.core.sync.MessageActionPerformer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 列表行的 UI 模型：从 Thread 领域模型映射，UI 不直接接触 Entity。 */
data class InboxThreadUi(
    val threadId: String,
    /** 参与者行：单人显示名字，多人合并展示。 */
    val senderLine: String,
    val subject: String,
    val snippet: String,
    val timeText: String,
    val unread: Boolean,
    val messageCount: Int,
)

sealed interface InboxUiState {
    data object Loading : InboxUiState

    /** 还没有账户：账户引导在后续阶段，本阶段展示诚实的空态。 */
    data object NoAccount : InboxUiState

    data class Ready(
        val accountEmail: String,
        val syncing: Boolean,
        val threads: List<InboxThreadUi>,
    ) : InboxUiState
}

/** 一次性 UI 事件（Snackbar）。 */
sealed interface InboxEvent {
    data class Archived(val threadSubject: String) : InboxEvent
}

class InboxViewModel(
    private val database: MaterialMailDatabase,
    private val actionPerformer: MessageActionPerformer,
    /** 手动刷新触发器（app 层注入 SyncScheduler.syncNow）。 */
    private val onManualRefresh: () -> Unit,
) : ViewModel() {

    private var lastArchiveSnapshot: MessageActionPerformer.ThreadMoveSnapshot? = null

    val events = MutableSharedFlow<InboxEvent>(extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<InboxUiState> = database.accountDao().observeAll()
        .flatMapLatest { accounts ->
            val account = accounts.firstOrNull()
                ?: return@flatMapLatest flowOf(InboxUiState.NoAccount)
            database.threadDao().observeInbox(account.id).map { entities ->
                InboxUiState.Ready(
                    accountEmail = account.email,
                    syncing = account.syncState == SyncState.SYNCING.name,
                    threads = entities.map { it.toModel().toUi() },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState.Loading)

    fun refresh() = onManualRefresh()

    /** 滑动归档：本地乐观更新，Undo 窗口内可撤销。 */
    fun archiveThread(threadId: String, subject: String) {
        viewModelScope.launch {
            val snapshot = actionPerformer.archiveThread(ThreadId(threadId))
            if (snapshot != null) {
                lastArchiveSnapshot = snapshot
                events.emit(InboxEvent.Archived(subject))
            }
        }
    }

    fun undoArchive() {
        val snapshot = lastArchiveSnapshot ?: return
        lastArchiveSnapshot = null
        viewModelScope.launch { actionPerformer.restore(snapshot) }
    }

    private fun Thread.toUi(): InboxThreadUi {
        val names = participants.map { it.displayName }.distinct()
        val senderLine = when {
            names.isEmpty() -> "未知发件人"
            names.size == 1 -> names.first()
            else -> names.take(2).joinToString("、") + " 等 " + names.size + " 人"
        }
        return InboxThreadUi(
            threadId = id.value,
            senderLine = senderLine,
            subject = subject.ifBlank { "（无主题）" },
            snippet = snippet,
            timeText = formatListTime(lastMessageAt),
            unread = !isRead,
            messageCount = messageCount,
        )
    }

    companion object {
        fun factory(
            database: MaterialMailDatabase,
            actionPerformer: MessageActionPerformer,
            onManualRefresh: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { InboxViewModel(database, actionPerformer, onManualRefresh) }
        }
    }

    private companion object {
        fun formatListTime(instant: Instant): String {
            val zone = ZoneId.systemDefault()
            val time = instant.atZone(zone)
            val now = Instant.now().atZone(zone)
            return when {
                time.toLocalDate() == now.toLocalDate() ->
                    time.format(DateTimeFormatter.ofPattern("HH:mm"))
                time.toLocalDate() == now.toLocalDate().minusDays(1) -> "昨天"
                time.year == now.year ->
                    time.format(DateTimeFormatter.ofPattern("M月d日"))
                else -> time.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
            }
        }
    }
}