package com.materialmail.feature.composer

object ComposerRoutes {
    const val ARG_DRAFT_ID = "draftId"
    const val ARG_REPLY_TO = "replyTo"
    const val ARG_MODE = "mode"

    const val COMPOSER =
        "composer?draftId={draftId}&replyTo={replyTo}&mode={mode}"

    fun new(): String = "composer?mode=NEW"
    fun reply(messageId: String, replyAll: Boolean): String =
        "composer?replyTo=$messageId&mode=" + (if (replyAll) "REPLY_ALL" else "REPLY")
    fun forward(messageId: String): String = "composer?replyTo=$messageId&mode=FORWARD"
    fun editDraft(draftId: String): String = "composer?draftId=$draftId&mode=NEW"
}