# SmartPM

SmartPM 是一个融合 AI 任务规划、实时协作、可解释风险预警和项目数据分析的项目管理平台。毕业设计题目建议为《基于 Spring Boot 与 Vue 3 的 AI 智能协同项目管理系统设计与实现》。

## 核心能力

- AI 项目规划、任务拆解/优化、周报和结构化风险建议，同时记录成功率、响应时间、采纳率与用户评分。
- 任务评论、显式 @提醒、不可篡改的动态时间线、全局通知中心与 WebSocket 实时同步。
- 基于确定性规则的风险中心，返回分数、等级、命中因素、阻塞关系和成员工作量。
- RBAC 项目隔离、拖拽看板、甘特图、里程碑、Wiki、附件审计、回收站、数据大屏和 Docker 部署。
- Flyway 自动建表/升级，任务依赖和里程碑关联使用规范化关系表。
- 个人工作台聚合今日、逾期、本周、阻塞与提及任务，并提供权限范围内的项目/任务/Wiki 全局搜索和批量操作。
- 风险预警支持负责人、处理状态、应对措施、期限与不可变事件时间线；项目洞察支持项目与日期筛选、按时率、延期和周期指标。
- Wiki 全文搜索、任务关联与版本恢复，项目模板、周期任务、CSV 安全导入导出，以及默认关闭的 SMTP 邮件通知。
- 项目级“产品共创”工作台支持持久化 AI 对话、显式上下文、PRD/用户故事等版本化成果，以及预览确认后转换为 Wiki、任务、清单、里程碑或决策。
- 角色化工作台、项目决策驾驶舱、独立验收状态机、验收清单、评审证据、逐日工时、成员容量/请假例外、管理简报 PDF/邮件和系统操作审计。

## 一键启动

### Windows

安装 Docker Desktop 后，双击 `start.bat` 即可一键构建并启动 MySQL、Redis、Spring Boot 后端和 Nginx 前端。Docker Desktop 未运行时，脚本会尝试自动启动并等待其就绪。

首次运行需要下载基础镜像和项目依赖，可能耗时数分钟。启动完成后脚本会自动打开 <http://localhost:3000>；数据库数据保存在 Docker 命名卷中，关闭启动窗口不会停止服务。

如果 Windows/Hyper-V 占用了 `3000` 端口，启动脚本会请求一次管理员权限，将异常的低位动态端口范围恢复为 Windows 标准范围，然后继续启动 Docker。

### macOS / Linux

```bash
./start.sh
```

也可以在项目根目录执行：

```bash
docker compose up -d --build
```

启动完成后访问 <http://localhost:3000>。

## 环境变量

可在项目根目录创建 `.env` 文件：

```env
MYSQL_ROOT_PASSWORD=修改为安全密码
AI_API_KEY=你的模型接口密钥
JWT_SECRET=至少-32-位的随机密钥
INITIAL_ADMIN_PASSWORD=至少-8-位的初始管理员密码
ALLOWED_ORIGINS=http://localhost:3000
FRONTEND_URL=http://localhost:3000
# 邮件通知可选；未配置时站内通知和其他业务不受影响
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=smartpm@example.com
SMTP_PASSWORD=应用专用密码
SMTP_AUTH=true
SMTP_STARTTLS=true
# 默认使用国内 Docker Hub 镜像代理；海外环境可改为 docker.io
DOCKER_REGISTRY_MIRROR=m.daocloud.io/docker.io
```

AI 功能需要配置 `AI_API_KEY`；不配置时，基础看板、协作与规则风险分析仍可用。系统只在首次启动创建 `admin`，默认密码为 `SmartPM@2026`，生产环境必须修改。

## 接口文档与测试

Docker 启动后可访问 <http://localhost:3000/swagger-ui.html>，在 Swagger 页面使用登录返回的 Bearer Token 调试受保护接口；健康检查地址为 <http://localhost:3000/actuator/health>。

```bash
# 后端 JUnit 5；Docker 可用时同时运行 Testcontainers MySQL 集成测试
mvn test

# 前端 Vitest 与生产构建
cd kanban-frontend
npm test
npm run build
```

AI 论文实验的 20 组需求数据、评价方法和结果模板位于 `docs/`。

## 常用操作

```bash
docker compose ps
docker compose logs -f backend
docker compose down
```

MySQL 和 Redis 数据保存在 Docker 命名卷中。全新数据库由 Flyway 从 V1 自动建表，已有数据库会建立基线并执行后续升级；旧库中的任务依赖和里程碑关联会自动迁移且不丢失。

当前迁移版本为 V11：V4 增加 CPM 与计划基线，V5 增加工作台索引和风险处理，V6 增加 Wiki 版本与任务关联，V7 增加项目模板和周期任务，V8 增加邮箱验证、通知偏好和邮件 Outbox，V9 增加产品共创会话与成果版本，V10 增加验收、工时和成员容量，V11 增加操作审计、登录事件与系统运营记录。
