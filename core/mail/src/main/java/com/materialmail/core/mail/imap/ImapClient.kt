package com.materialmail.core.mail.imap

import com.materialmail.core.model.MessageFlag
import kotlinx.coroutines.flow.Flow

/**
 * IMAP 客户端契约。所有实现必须：
 *
 * - 阻塞 IO 在 [kotlinx.coroutines.Dispatchers.IO] 上执行；
 * - UIDVALIDITY 变化时通过 [RemoteFolder.uidValidity] 暴露，由 sync 层决定重建缓存；
 * - IDLE 断线由实现内部重连，事件流不断（重连中事件可能丢失，sync 层负责兜底轮询）。
 *
 * CONDSTORE / QRESYNC 支持不齐（国内邮箱尤其），本阶段接口不依赖它们，
 * 后续在 [ImapCapabilities] 中协商后再启用增量同步。
 */
interface ImapClient {

    /** 连接并认证。重复调用 = 断开旧连接后重连。 */
    suspend fun connect(config: ServerConfig, credentials: AuthCredentials)

    /** 服务器能力协商结果（IDLE / CONDSTORE / QRESYNC / UIDPLUS）。 */
    suspend fun capabilities(): ImapCapabilities

    suspend fun listFolders(): List<RemoteFolder>

    /**
     * 拉取指定 UID 集合的信封（不含正文）。空列表返回空。
     */
    suspend fun fetchEnvelopes(folderName: String, uids: List<Long>): List<RemoteEnvelope>

    /** 增量同步：拉取 UID > [afterUid] 的全部信封。 */
    suspend fun fetchNewEnvelopes(folderName: String, afterUid: Long): List<RemoteEnvelope>

    /** 文件夹内当前全部 UID（删除对账用，FETCH UID 很轻）。 */
    suspend fun fetchAllUids(folderName: String): List<Long>

    /** 拉取整封原始 MIME 报文。 */
    suspend fun fetchRawMessage(folderName: String, uid: Long): RawMessage

    /** 追加消息到文件夹（已发送 / 草稿保存）。返回新邮件 UID（UIDPLUS 支持时）。 */
    suspend fun appendMessage(folderName: String, raw: ByteArray, flags: Set<MessageFlag>): Long?

    suspend fun setFlags(folderName: String, uids: List<Long>, flags: Set<MessageFlag>, value: Boolean)

    /** UIDPLUS MOVE 优先，不支持时 COPY + \Deleted + EXPUNGE。 */
    suspend fun moveMessages(folderName: String, uids: List<Long>, targetFolderName: String)

    /** 服务器端删除（标记 \Deleted + EXPUNGE）。 */
    suspend fun expungeMessages(folderName: String, uids: List<Long>)

    /**
     * IDLE 事件流。collector 挂起期间保持 IDLE；
     * 服务器不支持 IDLE 时实现退化为周期 NOOP 轮询并仍产出事件。
     */
    fun idle(folderName: String): Flow<FolderEvent>

    suspend fun disconnect()
}

data class ImapCapabilities(
    val hasIdle: Boolean,
    val hasCondstore: Boolean,
    val hasQresync: Boolean,
    val hasUidPlus: Boolean,
    val hasMove: Boolean,
)