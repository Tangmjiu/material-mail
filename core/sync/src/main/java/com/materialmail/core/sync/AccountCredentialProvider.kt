package com.materialmail.core.sync

import com.materialmail.core.mail.imap.AuthCredentials
import com.materialmail.core.model.Account

/**
 * 凭据供给契约。同步引擎不感知 Keystore / DataStore 细节（安全模型 §11），
 * 真实实现由账户/认证层（后续阶段）提供并注入。
 *
 * 返回 null 表示凭据缺失或已失效 → 同步跳过该账户，不重试、不猜测。
 */
fun interface AccountCredentialProvider {
    suspend fun credentialsFor(account: Account): AuthCredentials?
}