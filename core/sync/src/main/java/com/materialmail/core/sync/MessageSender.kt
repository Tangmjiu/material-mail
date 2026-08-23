package com.materialmail.core.sync

import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toModel
import com.materialmail.core.mail.imap.ServerConfig
import com.materialmail.core.mail.smtp.JakartaSmtpClient
import com.materialmail.core.mail.smtp.MimeMessageBuilder
import com.materialmail.core.mail.smtp.OutgoingMessage
import com.materialmail.core.mail.smtp.SendOutcome
import com.materialmail.core.mail.smtp.SmtpClient
import com.materialmail.core.model.AccountId
import com.materialmail.core.model.DraftId
import com.materialmail.core.model.Encryption
import com.materialmail.core.model.FolderRole
import com.materialmail.core.model.MessageFlag
import com.materialmail.core.model.Participant
import com.materialmail.core.model.ServerEndpoint

/**
 * 发送编排（Composer 的唯一出口）：
 *
 * 1. 构造 MIME 报文（含 Message-ID / 回复引用链）；
 * 2. SMTP 发送（Bcc 在线上剥离，见 JakartaSmtpClient）；
 * 3. 成功后把完整报文（含 Bcc）追加到远端 Sent 文件夹，
 *    下次同步自然回流到本地列表；
 * 4. 删除对应本地草稿。
 *
 * 步骤 3 失败不回滚步骤 2（邮件已发出是事实），只记录警告 ——
 * 下次全量同步的删除对账不受影响。
 */
class MessageSender(
    private val database: MaterialMailDatabase,
    private val credentialProvider: AccountCredentialProvider,
    private val clientFactory: ImapClientFactory = DefaultImapClientFactory(),
    private val smtpClient: SmtpClient = JakartaSmtpClient(),
) {

    sealed interface Result {
        data object Sent : Result
        data class Failure(val reason: String) : Result
    }

    suspend fun send(
        accountId: AccountId,
        message: OutgoingMessage,
        draftIdToDelete: DraftId? = null,
    ): Result {
        val account = database.accountDao().getById(accountId.value)?.toModel()
            ?: return Result.Failure("账户不存在")
        val credentials = credentialProvider.credentialsFor(account)
            ?: return Result.Failure("凭据缺失，请重新登录")

        val raw = MimeMessageBuilder.build(message)
        val recipients = (message.to + message.cc + message.bcc).map { it.address }

        return when (val outcome = smtpClient.send(
            account.smtp.toServerConfig(),
            credentials,
            raw,
            recipients,
        )) {
            is SendOutcome.Sent -> {
                // 追加到远端 Sent（尽力而为）
                runCatching {
                    val sentFolder = database.folderDao()
                        .findByRole(accountId.value, FolderRole.SENT.name)
                    if (sentFolder != null) {
                        val client = clientFactory.create()
                        try {
                            client.connect(account.imap.toServerConfig(), credentials)
                            client.appendMessage(
                                sentFolder.remoteName,
                                raw,
                                setOf(MessageFlag.SEEN),
                            )
                        } finally {
                            runCatching { client.disconnect() }
                        }
                    }
                }
                if (draftIdToDelete != null) {
                    database.draftDao().deleteById(draftIdToDelete.value)
                }
                Result.Sent
            }

            is SendOutcome.Failure -> Result.Failure(outcome.reason)
        }
    }

    private fun ServerEndpoint.toServerConfig(): ServerConfig = ServerConfig(
        host = host,
        port = port,
        encryption = encryption,
        allowCleartext = encryption == Encryption.NONE,
    )
}