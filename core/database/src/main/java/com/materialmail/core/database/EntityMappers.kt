package com.materialmail.core.database

import com.materialmail.core.database.entity.AccountEntity
import com.materialmail.core.database.entity.AttachmentEntity
import com.materialmail.core.database.entity.DraftEntity
import com.materialmail.core.database.entity.FolderEntity
import com.materialmail.core.database.entity.LabelEntity
import com.materialmail.core.database.entity.MessageEntity
import com.materialmail.core.database.entity.ThreadEntity
import com.materialmail.core.model.Account
import com.materialmail.core.model.AccountId
import com.materialmail.core.model.Attachment
import com.materialmail.core.model.AttachmentId
import com.materialmail.core.model.BodyFormat
import com.materialmail.core.model.BodyRef
import com.materialmail.core.model.ColorInt
import com.materialmail.core.model.Draft
import com.materialmail.core.model.DraftId
import com.materialmail.core.model.Encryption
import com.materialmail.core.model.Folder
import com.materialmail.core.model.FolderId
import com.materialmail.core.model.FolderRole
import com.materialmail.core.model.Label
import com.materialmail.core.model.LabelId
import com.materialmail.core.model.Message
import com.materialmail.core.model.MessageId
import com.materialmail.core.model.Protocol
import com.materialmail.core.model.ServerEndpoint
import com.materialmail.core.model.SyncState
import com.materialmail.core.model.Thread
import com.materialmail.core.model.ThreadId
import java.time.Instant

/** Entity ↔ Model 双向映射。Entity 是存储细节，模块外只暴露 Model。 */

private fun Instant.toEpochMs(): Long = toEpochMilli()
private fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id.value,
    email = email,
    displayName = displayName,
    protocol = protocol.name,
    imapHost = imap.host,
    imapPort = imap.port,
    imapEncryption = imap.encryption.name,
    smtpHost = smtp.host,
    smtpPort = smtp.port,
    smtpEncryption = smtp.encryption.name,
    syncState = syncState.name,
    createdAtEpochMs = createdAt.toEpochMs(),
)

fun AccountEntity.toModel(): Account = Account(
    id = AccountId(id),
    email = email,
    displayName = displayName,
    protocol = Protocol.valueOf(protocol),
    imap = ServerEndpoint(imapHost, imapPort, Encryption.valueOf(imapEncryption)),
    smtp = ServerEndpoint(smtpHost, smtpPort, Encryption.valueOf(smtpEncryption)),
    syncState = SyncState.valueOf(syncState),
    createdAt = createdAtEpochMs.toInstant(),
)

fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id.value,
    accountId = accountId.value,
    remoteName = remoteName,
    displayName = displayName,
    role = role.name,
    unreadCount = unreadCount,
    uidValidity = uidValidity,
)

fun FolderEntity.toModel(): Folder = Folder(
    id = FolderId(id),
    accountId = AccountId(accountId),
    remoteName = remoteName,
    displayName = displayName,
    role = FolderRole.valueOf(role),
    unreadCount = unreadCount,
    uidValidity = uidValidity,
)

fun Thread.toEntity(): ThreadEntity = ThreadEntity(
    id = id.value,
    accountId = accountId.value,
    subject = subject,
    participantsJson = Converters.participantsToJson(participants),
    messageCount = messageCount,
    lastMessageAtEpochMs = lastMessageAt.toEpochMs(),
    isRead = isRead,
    labelIdsCsv = Converters.labelIdsToCsv(labels.map { it.value }.toSet()),
)

fun ThreadEntity.toModel(): Thread = Thread(
    id = ThreadId(id),
    accountId = AccountId(accountId),
    subject = subject,
    participants = Converters.participantsFromJson(participantsJson),
    messageCount = messageCount,
    lastMessageAt = lastMessageAtEpochMs.toInstant(),
    isRead = isRead,
    labels = Converters.labelIdsFromCsv(labelIdsCsv).map(::LabelId).toSet(),
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id.value,
    threadId = threadId.value,
    folderId = folderId.value,
    remoteUid = remoteUid,
    messageIdHeader = messageIdHeader,
    inReplyTo = inReplyTo,
    referencesJson = Converters.stringListToJson(references),
    fromJson = Converters.participantsToJson(listOf(from)),
    toJson = Converters.participantsToJson(to),
    ccJson = Converters.participantsToJson(cc),
    bccJson = Converters.participantsToJson(bcc),
    subject = subject,
    sentAtEpochMs = sentAt.toEpochMs(),
    snippet = bodyRef.snippet,
    plainTextPath = bodyRef.plainTextPath,
    htmlPath = bodyRef.htmlPath,
    hasAttachments = hasAttachments,
    flagsCsv = Converters.flagsToCsv(flags),
)

fun MessageEntity.toModel(): Message = Message(
    id = MessageId(id),
    threadId = ThreadId(threadId),
    folderId = FolderId(folderId),
    remoteUid = remoteUid,
    messageIdHeader = messageIdHeader,
    inReplyTo = inReplyTo,
    references = Converters.stringListFromJson(referencesJson),
    from = Converters.participantsFromJson(fromJson).first(),
    to = Converters.participantsFromJson(toJson),
    cc = Converters.participantsFromJson(ccJson),
    bcc = Converters.participantsFromJson(bccJson),
    subject = subject,
    sentAt = sentAtEpochMs.toInstant(),
    bodyRef = BodyRef(
        snippet = snippet,
        plainTextPath = plainTextPath,
        htmlPath = htmlPath,
    ),
    hasAttachments = hasAttachments,
    flags = Converters.flagsFromCsv(flagsCsv),
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id.value,
    messageId = messageId.value,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    localUri = localUri,
    contentId = contentId,
)

fun AttachmentEntity.toModel(): Attachment = Attachment(
    id = AttachmentId(id),
    messageId = MessageId(messageId),
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    localUri = localUri,
    contentId = contentId,
)

fun Draft.toEntity(): DraftEntity = DraftEntity(
    id = id.value,
    accountId = accountId.value,
    toJson = Converters.participantsToJson(to),
    ccJson = Converters.participantsToJson(cc),
    bccJson = Converters.participantsToJson(bcc),
    subject = subject,
    body = body,
    bodyFormat = bodyFormat.name,
    inReplyToMessageId = inReplyToMessageId?.value,
    updatedAtEpochMs = updatedAt.toEpochMs(),
)

fun DraftEntity.toModel(): Draft = Draft(
    id = DraftId(id),
    accountId = AccountId(accountId),
    to = Converters.participantsFromJson(toJson),
    cc = Converters.participantsFromJson(ccJson),
    bcc = Converters.participantsFromJson(bccJson),
    subject = subject,
    body = body,
    bodyFormat = BodyFormat.valueOf(bodyFormat),
    inReplyToMessageId = inReplyToMessageId?.let(::MessageId),
    updatedAt = updatedAtEpochMs.toInstant(),
)

fun Label.toEntity(): LabelEntity = LabelEntity(
    id = id.value,
    accountId = accountId.value,
    name = name,
    colorArgb = color?.argb,
)

fun LabelEntity.toModel(): Label = Label(
    id = LabelId(id),
    accountId = AccountId(accountId),
    name = name,
    color = colorArgb?.let(::ColorInt),
)