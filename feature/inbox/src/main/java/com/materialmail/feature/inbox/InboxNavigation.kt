package com.materialmail.feature.inbox

/** 路由常量。导航图由 app 组装层接线（feature 不持有 NavController 依赖方向）。 */
object InboxRoutes {
    const val INBOX = "inbox"
    const val THREAD_DETAIL = "thread/{threadId}"

    fun threadDetail(threadId: String): String = "thread/$threadId"
}