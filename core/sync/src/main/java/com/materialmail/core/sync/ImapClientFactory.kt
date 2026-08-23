package com.materialmail.core.sync

import com.materialmail.core.mail.imap.ImapClient
import com.materialmail.core.mail.imap.JakartaImapClient

/** 每个同步任务持有独立的 client 实例（IMAP 连接不可跨任务共享）。 */
interface ImapClientFactory {
    fun create(): ImapClient
}

class DefaultImapClientFactory : ImapClientFactory {
    override fun create(): ImapClient = JakartaImapClient()
}