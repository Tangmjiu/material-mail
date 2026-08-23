# 仓库策略（Open Core 落地）

> 决策日期：2026-08-23。对应设计文档 §3「Pro 与 Community 必须彻底解耦」。

## 仓库拓扑

```
material-mail-full（本仓库，私有）          material-mail（公开，社区版）
├─ 全部 core/community 代码                ├─ 从私有仓库同步的开放子集
├─ pro/（闭源 Pro 模块，未来）              └─ 接受社区 PR，由维护者回合到私有仓库
└─ 开发主战场                               
```

- **私有仓库** `Tangmjiu/material-mail-full`：完整产品，含未来 `pro/` 模块，开发主战场。
- **公开仓库** `Tangmjiu/material-mail`：社区版（Community Edition），
  包含 `:core/*`、`:designsystem`、`:feature:*`、`:agent`、`:region`、`:app`、`:build-logic`。
- 公开仓库的代码 = 私有仓库的同一份代码（不是 fork 出来的重写版），
  通过 `tools/publish-community.ps1` 同步。

## 为什么 .gitignore 挡不住闭源

`.gitignore` 只对未跟踪文件生效，挡不住已提交内容。真正的隔离机制：

1. **目录边界**：所有闭源代码必须放在 `pro/` 下，社区版同步脚本按路径剔除；
2. **构建边界**：`settings.gradle.kts` 中 pro 模块条件包含（目录存在 + `materialmail.includePro` 开关），
   社区版仓库连 `pro/` 目录都不存在，物理上不可能误编；
3. **依赖边界**：`checkModuleBoundaries` 任务禁止 `:app`（社区壳）和任何 `:core:`/`:feature:`
   依赖 `:pro:*`；
4. **历史边界**：向公开仓库推送前运行发布脚本，剔除 `pro/` 路径的历史。

## 社区版同步流程

**当前阶段（pro/ 尚不存在）**：两个远端推送同一份 main：

```powershell
git push origin main   # material-mail-full（私有）
git push oss main      # material-mail（公开）
```

**网络兜底（本机 github.com 的 git/SSH 端口被阻断时）**：
`tools/push-via-api.ps1 -Repo <repo>` 通过 api.github.com 的 Git Data API
逐提交回放，对象 SHA 与本地完全一致（内容寻址），历史无损。

**pro/ 落地后**：改为发布脚本（基于 git filter-repo 剔除 pro/ 历史），
见 `tools/publish-community.ps1`。

## 提交前检查单（涉及 pro/ 或密钥时必过）

- [ ] 闭源代码全部位于 `pro/` 目录内
- [ ] 没有提交 `*.keystore / *.jks / signing.properties / secrets.properties / .env`
- [ ] `local.properties` 未被跟踪
- [ ] `./gradlew checkModuleBoundaries` 通过
- [ ] 向 oss 推送前：本次提交不含 pro/ 路径变更（有则先走发布脚本流程）

## GitHub 操作记录（2026-08-23）

- 原 `Tangmjiu/material-mail`（公开）→ 改名 `material-mail-full` 并设为私有；
- 新建公开仓库 `Tangmjiu/material-mail` 作为社区版；
- 本地 remote：`origin` = 私有完整仓库，`oss` = 公开社区仓库。