# Material Mail（Community Edition）

一个真正为 Android 设计的现代邮箱客户端 —— 开源社区版。

**Material 3 Expressive 不是组件库，是设计语言。** Material Mail 的目标是在 Android 上拥有接近 Google 第一方应用的完成度、动态感与原生感。即使完全没有 AI，它也应该是一个你愿意每天使用的邮箱。

## 原则

- **Local First**：本地处理、本地存储、本地搜索。我们不运营邮件服务器，你的邮件直连你自己的邮箱服务。
- **Privacy First**：不需要账号、不上传邮件内容、不卖数据、不插广告。
- **Open Core**：社区版是完整可用的邮箱（IMAP/SMTP/多账户/搜索/离线/基础 Agent）。
  Pro 版（自动化、企业 IM Connector、高级 Agent）闭源，在独立的私有仓库开发，
  架构上与 Core 彻底解耦 —— 详见 `docs/REPO-STRATEGY.md`。

## 技术栈

Kotlin · Jetpack Compose · Material 3 Expressive · Room · WorkManager · Coroutines/Flow · Jakarta Mail（IMAP/SMTP/MIME）

## 模块结构

```
:app              社区版壳（导航、DI、入口）
:core:model       纯 Kotlin 领域模型
:core:database    Room + 可选 SQLCipher（默认关）
:core:mail        IMAP/SMTP/MIME/threading 引擎
:core:sync        WorkManager 同步调度
:core:search      本地 FTS（进行中）
:core:capability  可插拔能力契约
:core:crypto      Keystore AES/GCM 凭据保护
:designsystem     M3 Expressive 主题与签名组件
:feature:*        inbox / composer / account / settings
:agent            Agent 能力 API + 确认协议（进行中）
:region           地区检测与服务可用性（自包含，可整体摘除）
```

模块依赖方向由 `checkModuleBoundaries` 任务强制校验：Core 永不依赖 Pro/Agent/Region/UI。

## 构建

```powershell
./gradlew assembleDebug          # 构建
./gradlew checkModuleBoundaries  # 模块边界校验
./gradlew :core:mail:test        # threading / UTF-7 单测
```

JDK 17+，Android SDK 36。中国大陆环境已默认配置阿里云 Maven 镜像（`settings.gradle.kts`）。

## 设计签名

**Ink & Paper**：墨青 + 纸白的色彩世界；未读邮件用左侧 4dp 的 Unread Spine（书脊）+ 发件人字重变化表达，不用圆点 badge；列表零圆角零卡片，层级靠留白与色阶；列表→详情是容器变换，归档是行滑出 + Undo。

## License

**MPL-2.0**（Mozilla Public License 2.0）——见 [LICENSE](LICENSE)。

文件级 copyleft：对社区版源文件的修改必须以 MPL-2.0 回馈社区，
但允许与闭源文件（如 Pro 模块）链接共存。

提交 Pull Request 前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 中的
贡献者许可协议（CLA），提交即视为同意。

## 包名与发布身份

`:pro:app`（`com.materialmail`）是唯一正式发布版（GitHub Releases，收费）；`:app`（`com.materialmail.community`）只是开发者构建。接手项目前必读 `docs/PACKAGE-IDENTITY.md`，不要动 applicationId。
