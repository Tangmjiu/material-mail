package com.materialmail.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.core.database.Converters
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.model.AttachmentId
import com.materialmail.core.model.MessageId
import com.materialmail.core.model.ThreadId
import com.materialmail.core.sync.AttachmentDownloader
import com.materialmail.core.sync.BodyLoader
import com.materialmail.core.sync.MessageActionPerformer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AttachmentUi(
    val attachmentId: String,
    val fileName: String,
    val sizeText: String,
    val downloaded: Boolean,
)

data class DetailMessageUi(
    val messageId: String,
    val fromName: String,
    val fromAddress: String,
    val timeText: String,
    /** null = 正在加载正文。 */
    val bodyText: String?,
    val isHtml: Boolean,
    val remoteImagesAllowed: Boolean,
    val attachments: List<AttachmentUi>,
)

data class ThreadDetailUiState(
    val subject: String = "",
    val messages: List<DetailMessageUi> = emptyList(),
    val loading: Boolean = true,
)

sealed interface ThreadDetailEvent {
    /** 附件已就绪，交给系统查看器打开。 */
    data class AttachmentReady(val path: String, val mimeType: String, val fileName: String) :
        ThreadDetailEvent

    data class AttachmentFailed(val fileName: String) : ThreadDetailEvent
}

class ThreadDetailViewModel(
    private val threadId: ThreadId,
    private val database: MaterialMailDatabase,
    private val bodyLoader: BodyLoader,
    private val actionPerformer: MessageActionPerformer,
    private val attachmentDownloader: AttachmentDownloader,
) : ViewModel() {

    private val loadedBodies = MutableStateFlow<Map<String, BodyLoader.LoadedBody>>(emptyMap())
    private val loadedAttachments =
        MutableStateFlow<Map<String, List<AttachmentUi>>>(emptyMap())

    /** 用户逐封授权显示远程图片的邮件 id 集（默认禁止，防追踪像素）。 */
    private val remoteImagesAllowedFor = MutableStateFlow<Set<String>>(emptySet())

    val events = MutableSharedFlow<ThreadDetailEvent>(extraBufferCapacity = 4)

    val uiState: StateFlow<ThreadDetailUiState> = combine(
        database.threadDao().observeById(threadId.value),
        database.messageDao().observeByThread(threadId.value),
        loadedBodies,
        loadedAttachments,
        remoteImagesAllowedFor,
    ) { thread, messages, bodies, attachments, remoteAllowed ->
        ThreadDetailUiState(
            subject = thread?.subject ?: "",
            loading = false,
            messages = messages.map { entity ->
                val body = bodies[entity.id]
                val from = Converters.participantsFromJson(entity.fromJson).firstOrNull()
                DetailMessageUi(
                    messageId = entity.id,
                    fromName = from?.displayName ?: "未知发件人",
                    fromAddress = from?.address ?: "",
                    timeText = formatDetailTime(Instant.ofEpochMilli(entity.sentAtEpochMs)),
                    bodyText = when {
                        body?.plainText != null -> body.plainText
                        body?.html != null -> body.html
                        else -> null
                    },
                    isHtml = body?.plainText == null && body?.html != null,
                    remoteImagesAllowed = entity.id in remoteAllowed,
                    attachments = attachments[entity.id] ?: emptyList(),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadDetailUiState())

    init {
        // 打开详情 = 已读（Unread Spine 收缩动画由数据库状态变化驱动）
        viewModelScope.launch { actionPerformer.markThreadRead(threadId) }
        // 正文懒加载：只拉取本地还没有正文的消息；正文解析后附件元数据随之落库
        viewModelScope.launch {
            database.messageDao().getByThread(threadId.value)
                .filter { it.plainTextPath == null && it.htmlPath == null }
                .forEach { entity ->
                    bodyLoader.loadBody(MessageId(entity.id))?.let { body ->
                        loadedBodies.value = loadedBodies.value + (entity.id to body)
                        refreshAttachments(entity.id)
                    }
                }
            // 本地已有正文的消息：直接补附件列表
            database.messageDao().getByThread(threadId.value)
                .filter { it.hasAttachments }
                .forEach { refreshAttachments(it.id) }
        }
    }

    private suspend fun refreshAttachments(messageId: String) {
        val rows = database.attachmentDao().getByMessage(messageId)
        if (rows.isEmpty()) return
        loadedAttachments.value = loadedAttachments.value + (messageId to rows.map { entity ->
            AttachmentUi(
                attachmentId = entity.id,
                fileName = entity.fileName,
                sizeText = formatSize(entity.sizeBytes),
                downloaded = entity.localUri != null,
            )
        })
    }

    fun enableRemoteImages(messageId: String) {
        remoteImagesAllowedFor.value = remoteImagesAllowedFor.value + messageId
    }

    fun openAttachment(attachment: AttachmentUi) {
        viewModelScope.launch {
            val result = attachmentDownloader.download(AttachmentId(attachment.attachmentId))
            if (result != null) {
                events.emit(
                    ThreadDetailEvent.AttachmentReady(
                        result.file.absolutePath, result.mimeType, result.fileName,
                    ),
                )
            } else {
                events.emit(ThreadDetailEvent.AttachmentFailed(attachment.fileName))
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> (bytes / 1_048_576).toString() + " MB"
        bytes >= 1_024 -> (bytes / 1_024).toString() + " KB"
        bytes > 0 -> bytes.toString() + " B"
        else -> ""
    }

    private fun formatDetailTime(instant: Instant): String =
        instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))

    companion object {
        fun factory(
            threadId: ThreadId,
            database: MaterialMailDatabase,
            bodyLoader: BodyLoader,
            actionPerformer: MessageActionPerformer,
            attachmentDownloader: AttachmentDownloader,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ThreadDetailViewModel(
                    threadId, database, bodyLoader, actionPerformer, attachmentDownloader,
                )
            }
        }
    }
}