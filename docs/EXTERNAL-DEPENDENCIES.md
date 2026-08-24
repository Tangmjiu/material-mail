# 外部依赖与人工干预清单

> 维护日期：2026-08-24。任何需要"人类去第三方平台注册/开通"的事项集中在此。

## 阻塞型（没有就只能等）

### Gmail OAuth
- 动作：Google Cloud Console → 创建 OAuth 2.0 Android 客户端
- 需要：包名 `com.materialmail`（社区版）/ `com.materialmail.pro`（Pro 版）+ 签名 SHA-1 指纹
- 产出：client_id → 交给开发接入 AppAuth-Android
- 状态：⏸ 等待用户提供

### Microsoft OAuth（Outlook）
- 动作：Microsoft Entra → 注册应用 → 委派权限（IMAP.AccessAsUser.All / SMTP.Send 等）
- 优先级：低于 Gmail（Outlook 密码/应用密码流仍可用）
- 状态：⏸ 等待

## 可绕开型（已实现降级路径）

### 企业 IM Connector（飞书/钉钉/企业微信）
- 完整能力（搜索/读取消息）：需要企业管理员开通自建应用，个人无法代办
- **降级路径**：三家的「群自定义机器人 Webhook」零注册可用 → 发送能力完整实现；
  搜索/读取在 UI 中诚实标注「需要企业管理员开通自建应用」，配置入口已预留
- 这符合产品定位：Connector 是「企业用户配置自己的凭据」，不是我们代持密钥

## 发布时一次性动作

### 签名 keystore
- 发布 APK/AAB 前生成正式签名密钥（.gitignore 已排除，绝不入库）
- debug 构建用自动生成的 debug.keystore，无需动作

### Fake Pay 替换
- `BillingConfig.USE_FAKE_PAY = true`（唯一开关点）
- 正式商业化前：接入 Play Billing 或其他渠道，替换 FakeBillingGateway，开关置 false