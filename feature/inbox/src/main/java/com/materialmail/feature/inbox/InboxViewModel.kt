package com.materialmail.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.entity.FolderEntity
import com.materialmail.core.database.toModel
import com.materialmail.core.model.Draft
import com.materialmail.core.model.FolderRole
import com.materialmail.core.model.SyncState
import com.materialmail.core.model.Thread
import com.materialmail.core.model.ThreadId
import com.materialmail.core.sync.MessageActionPerformer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 列表行的 UI 模型。 */
data class InboxThreadUi(
    val threadId: String,
    val senderLine: String,
    val subject: String,
    val snippet: String,
    val timeText: String,
    val unread: Boolean,
    val messageCount: Int,
)

/** 抽屉里的文件夹行。 */
data class FolderUi(
    val folderId: String,
    val displayName: String,
    val role: FolderRole,
    val unreadCount: Int,
)

/** 本地草稿行。 */
data class DraftUi(
    val draftId: String,
    val toLine: String,
    val subject: String,
    val timeText: String,
)

/** 当前查看目标：某个文件夹，或本地草稿箱。 */
sealed interface InboxDestination {
    data class FolderDest(val folderId: String, val displayName: String) : InboxDestination
    data object Drafts : InboxDestination
}

sealed interface InboxUiState {
    data object Loading : InboxUiState
    data object NoAccount : InboxUiState

    data class Ready(
        val accountId: String,
        val accountEmail: String,
        /** 全部账户（抽屉切换器用）：accountId -> email。 */
        val accounts: List<Pair<String, String>>,
        val syncing: Boolean,
        val destination: InboxDestination,
        val folders: List<FolderUi>,
        val threads: List<InboxThreadUi>,
        val drafts: List<DraftUi>,
    ) : InboxUiState
}

sealed interface InboxEvent {
    data class Archived(val threadSubject: String) : InboxEvent
    data class Deleted(val threadSubject: String) : InboxEvent

    /** 批量操作完成（批量场景不做单条撤销，提示数量）。 */
    data class BatchArchived(val count: Int) : InboxEvent
    data class BatchDeleted(val count: Int) : InboxEvent
    data class BatchMarkedRead(val count: Int) : InboxEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val database: MaterialMailDatabase,
    private val actionPerformer: MessageActionPerformer,
    private val onManualRefresh: () -> Unit,
) : ViewModel() {

    private var lastMoveSnapshot: MessageActionPerformer.ThreadMoveSnapshot? = null

    /** null = 跟随账户默认 INBOX。 */
    private val selectedDestination = MutableStateFlow<InboxDestination?>(null)

    /** null = 第一个账户。 */
    private val selectedAccountId = MutableStateFlow<String?>(null)

    val events = MutableSharedFlow<InboxEvent>(extraBufferCapacity = 1)

    // ── 批量选择（MD3E 情境顶栏模式）─────────────────────────
    private val _selectedThreadIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedThreadIds: StateFlow<Set<String>> = _selectedThreadIds.asStateFlow()

    fun toggleSelection(threadId: String) {
        _selectedThreadIds.update {
            if (threadId in it) it - threadId else it + threadId
        }
    }

    fun clearSelection() { _selectedThreadIds.value = emptySet() }

    fun archiveSelected() = batchOp(
        op = { actionPerformer.archiveThread(ThreadId(it)) },
        event = { InboxEvent.BatchArchived(it) },
    )

    fun deleteSelected() = batchOp(
        op = { actionPerformer.deleteThread(ThreadId(it)) },
        event = { InboxEvent.BatchDeleted(it) },
    )

    fun markSelectedRead() = batchOp(
        op = { actionPerformer.markThreadRead(ThreadId(it)); Unit },
        event = { InboxEvent.BatchMarkedRead(it) },
    )

    private fun batchOp(
        op: suspend (String) -> Any?,
        event: (Int) -> InboxEvent,
    ) {
        val ids = _selectedThreadIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            var done = 0
            ids.forEach { id -> runCatching { op(id) }.onSuccess { done++ } }
            _selectedThreadIds.value = emptySet()
            events.emit(event(done))
        }
    }

    val uiState: StateFlow<InboxUiState> = database.accountDao().observeAll()
        .flatMapLatest { accounts ->
            val account = accounts.firstOrNull {
                it.id == selectedAccountId.value
            } ?: accounts.firstOrNull()
                ?: return@flatMapLatest flowOf(InboxUiState.NoAccount)
            combine(
                database.folderDao().observeByAccount(account.id),
                database.draftDao().observeByAccount(account.id),
                selectedDestination,
            ) { folders, drafts, selected -> Triple(folders, drafts, selected) }
                .flatMapLatest { (folders, drafts, selected) ->
                    val allAccounts = accounts.map { it.id to it.email }
                    val folderEntities = folders.sortedBy { it.role }
                    val destination = when (selected) {
                        null -> folderEntities.firstOrNull { it.role == FolderRole.INBOX.name }
                            ?.let { InboxDestination.FolderDest(it.id, localizedFolderName(FolderRole.valueOf(it.role), it.displayName)) }
                        is InboxDestination.Drafts -> InboxDestination.Drafts
                        is InboxDestination.FolderDest -> selected
                    }
                    if (destination == null) {
                        return@flatMapLatest flowOf(
                            readyState(account.id, account.email, allAccounts, account.syncState,
                                folderEntities, drafts, InboxDestination.Drafts, emptyList()),
                        )
                    }
                    when (destination) {
                        InboxDestination.Drafts -> flowOf(
                            readyState(account.id, account.email, allAccounts, account.syncState,
                                folderEntities, drafts, InboxDestination.Drafts, emptyList()),
                        )

                        is InboxDestination.FolderDest ->
                            database.threadDao().observeInFolder(destination.folderId)
                                .map { entities ->
                                    readyState(
                                        account.id, account.email, allAccounts, account.syncState,
                                        folderEntities, drafts,
                                        destination, entities.map { it.toModel().toUi() },
                                    )
                                }
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState.Loading)

    private fun readyState(
        accountId: String,
        email: String,
        accounts: List<Pair<String, String>>,
        syncState: String,
        folders: List<FolderEntity>,
        drafts: List<com.materialmail.core.database.entity.DraftEntity>,
        destination: InboxDestination,
        threads: List<InboxThreadUi>,
    ) = InboxUiState.Ready(
        accountId = accountId,
        accountEmail = email,
        accounts = accounts,
        syncing = syncState == SyncState.SYNCING.name,
        destination = destination,
        folders = folders.map {
            FolderUi(it.id, localizedFolderName(FolderRole.valueOf(it.role), it.displayName), FolderRole.valueOf(it.role), it.unreadCount)
        },
        threads = threads,
        drafts = drafts.map { it.toModel().toUi() },
    )

    /** 切换账户：重置文件夹选择到新账户的 INBOX。 */
    fun selectAccount(accountId: String) {
        selectedAccountId.value = accountId
        selectedDestination.value = null
    }

    fun selectFolder(folderId: String, displayName: String) {
        selectedDestination.value = InboxDestination.FolderDest(folderId, displayName)
    }

    fun selectDrafts() {
        selectedDestination.value = InboxDestination.Drafts
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch { database.draftDao().deleteById(draftId) }
    }

    fun refresh() = onManualRefresh()

    fun archiveThread(threadId: String, subject: String) {
        viewModelScope.launch {
            val snapshot = actionPerformer.archiveThread(ThreadId(threadId))
            if (snapshot != null) {
                lastMoveSnapshot = snapshot
                events.emit(InboxEvent.Archived(subject))
            }
        }
    }

    fun deleteThread(threadId: String, subject: String) {
        viewModelScope.launch {
            val snapshot = actionPerformer.deleteThread(ThreadId(threadId))
            if (snapshot != null) {
                lastMoveSnapshot = snapshot
                events.emit(InboxEvent.Deleted(subject))
            }
        }
    }

    fun undoMove() {
        val snapshot = lastMoveSnapshot ?: return
        lastMoveSnapshot = null
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

    private fun Draft.toUi(): DraftUi = DraftUi(
        draftId = id.value,
        toLine = to.firstOrNull()?.address ?: "（无收件人）",
        subject = subject.ifBlank { "（无主题）" },
        timeText = formatListTime(updatedAt),
    )

    companion object {
        fun factory(
            database: MaterialMailDatabase,
            actionPerformer: MessageActionPerformer,
            onManualRefresh: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { InboxViewModel(database, actionPerformer, onManualRefresh) }
        }

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
/**
 * 角色优先的文件夹中文名（修复“INBOX/Sent 直接裸露”的汉化缺口）：
 * 标准角色一律中文；服务器返回的中文名（QQ/163）与自定义文件夹保留原名。
 */
internal fun localizedFolderName(role: FolderRole, serverName: String): String = when (role) {
    FolderRole.INBOX -> "收件箱"
    FolderRole.SENT -> "已发送"
    FolderRole.DRAFTS -> "草稿箱"
    FolderRole.TRASH -> "已删除"
    FolderRole.ARCHIVE -> "归档"
    FolderRole.CUSTOM -> serverName
}