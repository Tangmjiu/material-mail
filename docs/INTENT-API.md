# Intent API（Tasker / Termux / 自动化集成）

> Pro 能力清单的一部分（需求 §19：Tasker / Termux / Intent API）。

## 触发同步（Broadcast）

```
action: com.materialmail.action.SYNC_NOW
```

Termux 示例：

```bash
am broadcast -a com.materialmail.action.SYNC_NOW -p com.materialmail.pro
```

Tasker：新增 Intent 任务，Action 填上面的值，Target 选 Broadcast Receiver。

## 写信（Activity）

```
action: com.materialmail.action.COMPOSE
extras:
  mailto: 也支持标准 mailto: 深链（含 ?subject=&body=）
```

Termux 示例：

```bash
am start -a android.intent.action.VIEW -d "mailto:a@b.com?subject=Hi&body=内容" -p com.materialmail
```

## 边界

- Intent API 只做"触发"（同步/打开写信页），不提供邮件内容读取——
  内容出 App 必须走你明确确认的分享/发送路径（Privacy First）。
- MCP / AppFunctions：生态接口成熟后接入（backlog，见设计文档 §14 第 4 条）。