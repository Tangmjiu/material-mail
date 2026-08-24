package com.materialmail.feature.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.core.database.Converters
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toEntity
import com.materialmail.core.database.toModel
import com.materialmail.core.mail.smtp.OutgoingAttachment
import com.materialmail.core.mail.smtp.OutgoingMessage
import com.materialmail.core.model.AccountId
import com.materialmail.core.model.BodyFormat
import com.materialmail.core.model.Draft
import com.materialmail.core.model.DraftId
import com.materialmail.core.model.MessageId
import com.materialmail.core.model.Participant
import com.materialmail.core.sync.BodyLoader
import com.materialmail.core.sync.MessageSender
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ComposeMode { NEW, REPLY, REPLY_ALL, FORWARD }

data class ComposerUiState(
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val showCcBcc: Boolean = false,
    val subject: String = "",
    val body: String = "",
    val accountEmail: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    /** 表单尚未初始化完成（预填进行中）。 */
    val initializing: Boolean = true,
    val attachments: List<OutgoingAttachment> = emptyList(),
)

sealed interface ComposerEvent {
    data object Sent : ComposerEvent
    data class Failed(val reason: String) : ComposerEvent
}

class ComposerViewModel(
    private val draftId: DraftId?,
    private val replyToMessageId: MessageId?,
    private val mode: ComposeMode,
    private val database: MaterialMailDatabase,
    private val messageSender: MessageSender,
    private val bodyLoader: BodyLoader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposerUiState())
    val uiState: StateFlow<ComposerUiState> = _uiState.asStateFlow()

    val events = MutableSharedFlow<ComposerEvent>(extraBufferCapacity = 1)

    private var accountId: AccountId? = null
    private var currentDraftId: DraftId? = draftId
    private var inReplyToHeader: String? = null
    private var referenceHeaders: List<String> = emptyList()
    /** 发送中/已发送时停止自动保存（避免发送成功后草稿复活）。 */
    private var autosaveEnabled = true

    init {
        viewModelScope.launch { initialize() }
        startAutosave()
    }

    private suspend fun initialize() {
        val account = database.accountDao().observeAll().first().firstOrNull()?.toModel()
        if (account == null) {
            _uiState.update { it.copy(initializing = false, error = "还没有账户，请先添加账户") }
            return
        }
        accountId = account.id
        _uiState.update { it.copy(accountEmail = account.email) }

        when {
            draftId != null -> loadDraft(draftId)
            replyToMessageId != null -> prefillFromOriginal(account.email)
        }
        _uiState.update { it.copy(initializing = false) }
    }

    private suspend fun loadDraft(id: DraftId) {
        val draft = database.draftDao().getById(id.value)?.toModel() ?: return
        inReplyToHeader = null
        _uiState.update {
            it.copy(
                to = draft.to.joinToString(", ") { p -> p.address },
                cc = draft.cc.joinToString(", ") { p -> p.address },
                bcc = draft.bcc.joinToString(", ") { p -> p.address },
                showCcBcc = draft.cc.isNotEmpty() || draft.bcc.isNotEmpty(),
                subject = draft.subject,
                body = draft.body,
            )
        }
    }

    private suspend fun prefillFromOriginal(ownEmail: String) {
        val id = replyToMessageId ?: return
        val entity = database.messageDao().getById(id.value) ?: return
        val original = entity.toModel()
        inReplyToHeader = original.messageIdHeader
        referenceHeaders = original.references + original.messageIdHeader

        val originalBody = bodyLoader.loadBody(id)
        val quoted = buildQuotedBody(original, originalBody?.plainText, originalBody?.html)

        _uiState.update { state ->
            when (mode) {
                ComposeMode.REPLY -> state.copy(
                    to = original.from.address,
                    subject = "Re: " + original.subject.removeReplyPrefixes(),
                    body = quoted,
                )

                ComposeMode.REPLY_ALL -> {
                    val to = (listOf(original.from) + original.to)
                        .map { it.address }
                        .distinct()
                        .filter { !it.equals(ownEmail, ignoreCase = true) }
                    val cc = original.cc.map { it.address }.distinct()
                        .filter { !it.equals(ownEmail, ignoreCase = true) }
                    state.copy(
                        to = to.joinToString(", "),
                        cc = cc.joinToString(", "),
                        showCcBcc = cc.isNotEmpty(),
                        subject = "Re: " + original.subject.removeReplyPrefixes(),
                        body = quoted,
                    )
                }

                ComposeMode.FORWARD -> state.copy(
                    subject = "Fwd: " + original.subject.removeReplyPrefixes(),
                    body = quoted,
                )

                ComposeMode.NEW -> state
            }
        }
    }

    private fun buildQuotedBody(
        original: com.materialmail.core.model.Message,
        plainText: String?,
        html: String?,
    ): String {
        val time = original.sentAt.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
        val content = (plainText ?: html?.replace(Regex("<[^>]+>"), " ") ?: "")
            .replace(Regex("\\s+\n"), "\n").trim()
        val quotedLines = content.lines().joinToString("\n") { "> " + it }
        return "\n\n在 " + time + "，" + original.from.displayName + " 写道：\n" + quotedLines
    }

    // ── 字段变更 ─────────────────────────────────────────────

    fun onToChanged(v: String) = _uiState.update { it.copy(to = v) }
    fun onCcChanged(v: String) = _uiState.update { it.copy(cc = v) }
    fun onBccChanged(v: String) = _uiState.update { it.copy(bcc = v) }
    fun onToggleCcBcc() = _uiState.update { it.copy(showCcBcc = !it.showCcBcc) }

    /** 添加附件（内容读入内存；单文件 ≤10MB、合计 ≤25MB，超出明确报错）。 */
    fun addAttachment(fileName: String, mimeType: String, data: ByteArray) {
        val current = _uiState.value.attachments
        if (data.size > MAX_ATTACHMENT_BYTES) {
            _uiState.update { it.copy(error = "$fileName 超过 10MB 上限") }
            return
        }
        if (current.sumOf { it.data.size } + data.size > MAX_TOTAL_BYTES) {
            _uiState.update { it.copy(error = "附件总大小超过 25MB 上限") }
            return
        }
        _uiState.update {
            it.copy(attachments = current + OutgoingAttachment(fileName, mimeType, data))
        }
    }

    fun removeAttachment(index: Int) {
        _uiState.update { state ->
            state.copy(attachments = state.attachments.filterIndexed { i, _ -> i != index })
        }
    }
    fun onSubjectChanged(v: String) = _uiState.update { it.copy(subject = v) }
    fun onBodyChanged(v: String) = _uiState.update { it.copy(body = v) }

    // ── 草稿自动保存（防抖 800ms，内容非空才落库）──────────────

    @OptIn(FlowPreview::class)
    private fun startAutosave() {
        viewModelScope.launch {
            _uiState // to/cc/bcc/subject/body 任一变化都会重发整个 state
                .debounce(800)
                .collect { state ->
                    if (!autosaveEnabled || state.initializing || state.sending) return@collect
                    if (state.to.isBlank() && state.subject.isBlank() && state.body.isBlank()) {
                        return@collect
                    }
                    saveDraft(state)
                }
        }
    }

    private suspend fun saveDraft(state: ComposerUiState) {
        val account = accountId ?: return
        val id = currentDraftId ?: DraftId("draft_" + UUID.randomUUID().toString())
            .also { currentDraftId = it }
        database.draftDao().upsert(
            Draft(
                id = id,
                accountId = account,
                to = parseAddresses(state.to),
                cc = parseAddresses(state.cc),
                bcc = parseAddresses(state.bcc),
                subject = state.subject,
                body = state.body,
                bodyFormat = BodyFormat.PLAIN_TEXT,
                inReplyToMessageId = replyToMessageId,
                updatedAt = Instant.now(),
            ).toEntity(),
        )
    }

    // ── 发送 ─────────────────────────────────────────────────

    fun send() {
        val state = _uiState.value
        val account = accountId ?: return
        val to = parseAddresses(state.to)
        if (to.isEmpty()) {
            _uiState.update { it.copy(error = "请至少填写一个收件人") }
            return
        }
        val invalid = (to + parseAddresses(state.cc) + parseAddresses(state.bcc))
            .map { it.address }
            .filterNot { ADDRESS_REGEX.matches(it) }
        if (invalid.isNotEmpty()) {
            _uiState.update { it.copy(error = "地址格式不正确：" + invalid.first()) }
            return
        }
        autosaveEnabled = false
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val accountModel = database.accountDao().getById(account.value)?.toModel() ?: return@launch
            val result = messageSender.send(
                accountId = account,
                message = OutgoingMessage(
                    from = Participant(
                        address = accountModel.email,
                        name = accountModel.displayName,
                    ),
                    to = to,
                    cc = parseAddresses(state.cc),
                    bcc = parseAddresses(state.bcc),
                    subject = state.subject,
                    body = state.body,
                    bodyFormat = BodyFormat.PLAIN_TEXT,
                    inReplyTo = inReplyToHeader,
                    references = referenceHeaders,
                    attachments = state.attachments,
                ),
                draftIdToDelete = currentDraftId,
            )
            when (result) {
                is MessageSender.Result.Sent -> events.emit(ComposerEvent.Sent)
                is MessageSender.Result.Failure -> {
                    autosaveEnabled = true
                    _uiState.update { it.copy(sending = false) }
                    events.emit(ComposerEvent.Failed(result.reason))
                }
            }
        }
    }

    private fun parseAddresses(raw: String): List<Participant> =
        raw.split(',', ';', '，', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Participant(address = it) }

    private fun String.removeReplyPrefixes(): String =
        replace(Regex("(?i)^(re|fwd?|答复|回复|转发)[:：]\\s*"), "")

    companion object {
        private val ADDRESS_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 25 * 1024 * 1024

        fun factory(
            draftId: DraftId?,
            replyToMessageId: MessageId?,
            mode: ComposeMode,
            database: MaterialMailDatabase,
            messageSender: MessageSender,
            bodyLoader: BodyLoader,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ComposerViewModel(draftId, replyToMessageId, mode, database, messageSender, bodyLoader)
            }
        }
    }
}