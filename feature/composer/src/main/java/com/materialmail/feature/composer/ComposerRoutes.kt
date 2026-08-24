package com.materialmail.feature.composer

import android.net.Uri

object ComposerRoutes {
    const val ARG_DRAFT_ID = "draftId"
    const val ARG_REPLY_TO = "replyTo"
    const val ARG_MODE = "mode"
    const val ARG_PREFILL_TO = "prefillTo"
    const val ARG_PREFILL_SUBJECT = "prefillSubject"
    const val ARG_PREFILL_BODY = "prefillBody"

    const val COMPOSER = "composer?draftId={draftId}&replyTo={replyTo}&mode={mode}" +
        "&prefillTo={prefillTo}&prefillSubject={prefillSubject}&prefillBody={prefillBody}"

    fun new(): String = "composer?mode=NEW"
    fun reply(messageId: String, replyAll: Boolean): String =
        "composer?replyTo=$messageId&mode=" + (if (replyAll) "REPLY_ALL" else "REPLY")
    fun forward(messageId: String): String = "composer?replyTo=$messageId&mode=FORWARD"
    fun editDraft(draftId: String): String = "composer?draftId=$draftId&mode=NEW"

    /** mailto: / 分享 / Shortcut 的预填入口。 */
    fun newPrefilled(to: String? = null, subject: String? = null, body: String? = null): String =
        buildString {
            append("composer?mode=NEW")
            to?.let { append("&prefillTo=").append(Uri.encode(it)) }
            subject?.let { append("&prefillSubject=").append(Uri.encode(it)) }
            body?.let { append("&prefillBody=").append(Uri.encode(it)) }
        }
}