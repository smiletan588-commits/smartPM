# AI 任务规划草稿接口（V12）

所有路径以 `/api/project/{projectId}/planning` 为前缀，需要登录。读取草稿需有项目查看权限且草稿由当前用户创建；生成、修改、单项重生成与应用还需项目写权限。系统管理员不绕过项目隔离。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/drafts` | 当前用户最近 20 条草稿 |
| `POST` | `/drafts` | 澄清信息提交后生成草稿；不创建真实任务 |
| `GET` | `/drafts/{draftId}` | 读取草稿、版本及最新检查提示 |
| `PUT` | `/drafts/{draftId}?version=N` | 保存用户编辑的 `content`，版本不符则拒绝 |
| `POST` | `/drafts/{draftId}/regenerate-item?index=N` | 仅返回第 N 项新建议，不自动保存或创建 |
| `POST` | `/drafts/{draftId}/apply?version=N` | 重新校验，事务创建任务、依赖与完整计划的里程碑 |

生成请求示例：

```json
{
  "mode": "DECOMPOSE",
  "parentTaskId": 12,
  "projectType": "SOFTWARE",
  "goal": "让用户完成活动报名",
  "deliverable": "可运行的报名流程及验收记录",
  "scope": "表单、提交反馈、异常处理；不含支付",
  "deadline": "2026-10-15",
  "team": "1名前端、1名测试",
  "wikiIds": [5]
}
```

`mode` 为 `INIT`、`PLAN` 或 `DECOMPOSE`；仅拆解模式需要当前项目的有效主任务 `parentTaskId`。目标、交付物、范围和项目类型必填。最多选择 3 篇同项目 Wiki。`projectType=GENERAL` 表示非研发项目，不强制研发岗位或开发标签。

草稿响应提供 `id`、`version`、`status`、`input`、`content`、`issues`。每项任务含标题、工作说明、交付物、验收标准、目标关联说明、建议岗位或能力、日期、前置任务索引及可选负责人。AI 生成时负责人始终为空；用户可以在确认前指定项目成员。`issues[].blocking=true` 必须修正才能应用；相似任务等非阻断提示需要人工判断。拆解结果可以为空并给出 `noSplitReason`，此时不创建任务。

真实任务仍采用未指定日期时“今天开始、15天后截止”的既有规则；明确填写的非法日期和父任务期限冲突会被阻断，不静默修改。旧的直接创建 AI 接口已从页面入口退出，新页面只调用草稿接口；旧接口暂保留兼容，后续客户端迁移完成后再移除。
