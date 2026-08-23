package com.materialmail.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.entity.MessageEntity
import com.materialmail.core.database.Converters
import com.materialmail.core.model.MessageId
import com.materialmail.core.model.ThreadId
import com.materialmail.core.sync.BodyLoader
import com.materialmail.core.sync.MessageActionPerformer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailMessageUi(
    val messageId: String,
    val fromName: String,
    val fromAddress: String,
    val timeText: String,
    /** null = 正在加载正文。 */
    val bodyText: String?,
    val isHtml: Boolean,
)

data class ThreadDetailUiState(
    val subject: String = "",
    val messages: List<DetailMessageUi> = emptyList(),
    val loading: Boolean = true,
)

class ThreadDetailViewModel(
    private val threadId: ThreadId,
    private val database: MaterialMailDatabase,
    private val bodyLoader: BodyLoader,
    private val actionPerformer: MessageActionPerformer,
) : ViewModel() {

    /** messageId -> 已加载正文。 */
    private val loadedBodies = MutableStateFlow<Map<String, BodyLoader.LoadedBody>>(emptyMap())

    val uiState: StateFlow<ThreadDetailUiState> = combine(
        database.threadDao().observeById(threadId.value),
        database.messageDao().observeByThread(threadId.value),
        loadedBodies,
    ) { thread, messages, bodies ->
        ThreadDetailUiState(
            subject = thread?.subject ?: "",
            messages = messages.map { it.toUi(bodies[it.id]) },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadDetailUiState())

    init {
        // 打开详情 = 已读（Unread Spine 收缩动画由数据库状态变化驱动）
        viewModelScope.launch { actionPerformer.markThreadRead(threadId) }
        // 正文懒加载：只拉取本地还没有正文的消息
        viewModelScope.launch {
            database.messageDao().getByThread(threadId.value)
                .filter { it.plainTextPath == null && it.htmlPath == null }
                .forEach { entity ->
                    bodyLoader.loadBody(MessageId(entity.id))?.let { body ->
                        loadedBodies.value = loadedBodies.value + (entity.id to body)
                    }
                }
        }
    }

    private fun MessageEntity.toUi(body: BodyLoader.LoadedBody?): DetailMessageUi {
        val from = Converters.participantsFromJson(fromJson).firstOrNull()
        val text = when {
            body?.plainText != null -> body.plainText
            body?.html != null -> body.html // 隔离 WebView 渲染器在打磨阶段引入，本阶段原文展示
            else -> null
        }
        return DetailMessageUi(
            messageId = id,
            fromName = from?.displayName ?: "未知发件人",
            fromAddress = from?.address ?: "",
            timeText = formatDetailTime(Instant.ofEpochMilli(sentAtEpochMs)),
            bodyText = text,
            isHtml = body?.plainText == null && body?.html != null,
        )
    }

    companion object {
        fun factory(
            threadId: ThreadId,
            database: MaterialMailDatabase,
            bodyLoader: BodyLoader,
            actionPerformer: MessageActionPerformer,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ThreadDetailViewModel(threadId, database, bodyLoader, actionPerformer) }
        }
    }

    private fun formatDetailTime(instant: Instant): String =
        instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
}