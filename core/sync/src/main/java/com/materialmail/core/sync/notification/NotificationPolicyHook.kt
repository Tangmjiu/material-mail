package com.materialmail.core.sync.notification

import com.materialmail.core.sync.NewMailInfo

/**
 * 新邮件通知策略挂点（Open Core 模式，与 NewMailDispatcher 同策略）：
 * Community 默认恒放行；Pro 壳启动时注册 VIP/免打扰策略实现。
 * 同步主流程不感知 Pro，策略实现在 :pro:extras。
 */
object NotificationPolicyHook {
    @Volatile
    var checker: (NewMailInfo) -> Boolean = { true }
        private set

    fun register(policy: (NewMailInfo) -> Boolean) {
        checker = policy
    }
}
