# 包名与发布身份（铁律，勿改）

> 决策日期：2026-08-25，由项目所有者（Tangmjiu）拍板。
> 下一位接手本项目的助手：**先读这条，再动任何 `applicationId`。**

## 规则

| 构建产物 | applicationId | 身份 | 分发渠道 |
|---|---|---|---|
| `:pro:app`（完整版，含 Pro 模块） | **`com.materialmail`** | **正式发布版（Release）** | GitHub Releases，**收费** |
| `:app`（社区版） | `com.materialmail.community` | 开发者/开源构建 | 源码自编译，不上架 |

## 为什么

1. **Pro 就是拿来卖的**：它是发布到 GitHub Releases 的正式产物，必须占有品牌包名
   `com.materialmail`。用户装的就是它，买的也是它。
2. **社区版不是产品**：它存在的意义是让开源社区能编译、能贡献、能审计。
   它用 `com.materialmail.community`，避免和正式版抢身份、抢签名、抢升级通道。
3. **升级路径**：用户从旧社区版（历史上曾占用 `com.materialmail`）装 Pro 版
   是同包名覆盖升级，本地数据（数据库/凭据）平滑保留。任何把包名换回去的
   "重构"都会打断这条路径。

## 红线

- ❌ 不得把 `:pro:app` 的 applicationId 改成 `com.materialmail` 以外的值；
- ❌ 不得把 `:app` 的 applicationId 改回 `com.materialmail`；
- ❌ 不得引入第三个面向用户的包名（内部测试变体除外）；
- ✅ FileProvider 等 authority 一律用 `${applicationId}` 占位符（现状已如此），
  包名差异不会导致 provider 冲突。

## 与仓库策略的关系

见 `docs/REPO-STRATEGY.md`：私有仓库 `material-mail-full` 包含 `pro/`（含 `:pro:app`），
公开仓库 `material-mail` 经 `tools/publish-community.ps1` 剔除 `pro/` 后只含社区版。
两个仓库共用同一套 `:core` / `:feature` / `:app-shell` 代码，包名差异只存在于
两个 app 壳模块的 `build.gradle.kts`。
