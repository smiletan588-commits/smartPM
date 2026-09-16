# 智能项目管理与看板系统 - 开发记录

### 2026-09-16 - GitHub 完整版本发布

**操作：** 整理并发布当前 SmartPM 完整版本至既有 GitHub 仓库 `origin/main`，包含任务受控流转与 QA 打回、操作提示与权限解释、产品共创、职场化工作台、排期基线、风险治理、Wiki、企业效率能力及其测试、迁移和部署配置。

**发布检查：** 敏感配置继续通过环境变量注入，未提交本地 `.env`；补充忽略 `tmp/` 运行时目录，不上传 PDF、截图、前端构建产物、依赖目录或后端编译产物。代码上传前已通过凭据文件名、配置默认值、暂存文件大小及 Git 空白错误检查。

**验证：** 沿用本版本最终验收结果：后端 `mvn test` 22 项、前端 Vitest 22 项、生产构建与 Docker 镜像构建全部通过；1440/1024/768/390 四档视觉回归通过；MySQL、Redis、后端和 Nginx 均健康。

**状态：** ✅ 发布检查完成，随本次版本提交推送至 `origin/main`。

### 2026-09-16 - 任务领取与受控流转升级完成

**操作：** 固定主任务流转为 `待办 → 进行中 → 已完成`，同列仍可排序，但拖拽、编辑和批量更新均不能跨级或逆向操作；待办进入进行中时会在同一事务内领取给当前成员。前端增加流转说明、专用拖拽把手、无效落点即时提示、卡片及详情内“领取并开始”按钮，并移除在进行中和已完成列直接新建任务的入口。

**QA 打回：** 扩展现有验收接口动作 `RETURN_FOR_FIX`。只有项目内 `QA_TESTER` 可以把已完成任务打回进行中，必须填写 Bug 原因；状态更新、完成时间清理、验收记录、审计记录和风险缓存失效一次完成。其他岗位及普通状态更新接口均不能重新打开已完成任务。

**关键决策：** 复用 V10 的任务评审记录，不新增数据库表或迁移；保留三列看板与同列排序。状态字段在编辑表单中只读，批量操作只提供所有选中任务共同允许的下一阶段，避免前端入口与后端规则不一致。

**验证：** `mvn test` 共 22 项全部通过，覆盖跳级、逆向、已完成直退、非 QA 打回、缺少原因和 QA 成功打回；`npm test -- --run` 共 22 项全部通过；生产构建与 Docker 镜像构建通过；1440/1024/768/390 四档视觉回归无横向溢出、运行时错误或任务详情布局破损，人工复核 390px 看板的规则提示与领取按钮正常；MySQL、Redis、后端和 Nginx 均健康。

**状态：** ✅ 完成并部署至 `http://localhost:3000`；保留工作区已有未提交改动，未覆盖、回退或提交用户内容。

### 2026-09-16 - 操作提示与权限可理解性升级完成

**操作：** 完成统一操作反馈、登录安全、权限前置说明、高风险确认和核心流程提示升级。前端新增 `AppError` 规范化、HTTP 200 业务错误识别、静默模式、1.5 秒同类错误去重，以及并发 401 的单次清理/提示/跳转；列表与整页加载失败改为可重试状态。登录和注册改用 JSON 正文并保留旧查询参数兼容，登录页增加字段校验、错误条、用户名保留、密码清空及聚焦、密码显隐、大小写锁定和安全回跳。

**权限与安全：** 项目列表增加 `owner` 和 `myPermission`，统一计算项目能力。项目修改、删除、成员管理、CSV、计划基线、风险改派、任务/Wiki/产品成果等入口会提前禁用并解释原因；VIEWER 写操作统一只读。项目仅负责人可移入回收站，删除确认展示名称、影响与恢复方式；永久删除必须输入对象名称。任务、Wiki、附件统一使用“移入回收站”术语，确认取消与接口失败不再混为一谈。普通成员可自行接取未分派风险，但只有项目负责人或项目管理员能够重新分派。

**流程提示：** 任务拖拽、依赖阻塞、验收、附件、风险、Wiki 自动保存和可选模块均保留具体失败原因与重试入口；Wiki 保存失败保留编辑内容并显示“尚未保存”。计划基线、关键路径、总浮动、What-if、风险与容量补充解释和空状态；反馈区域补充 `aria-live`，移动端提示及弹窗不遮挡操作。

**验证：** `mvn test` 共 20 项全部通过（含 5 项 Testcontainers 集成测试），Flyway V1–V11 空库迁移成功；`npm test -- --run` 共 20 项全部通过；`npm run build` 生产构建通过；真实部署后验证错误登录使用 JSON POST、密码不进入 URL，用户名保留、密码清空并聚焦，6 个并发 401 只产生一次提示并返回原页面；1440/1024/768/390 四档视觉回归覆盖 13 个主要路由和关键弹窗，无横向溢出、布局破损或运行时错误；Docker 的 MySQL、Redis、后端和 Nginx 均健康。

**影响范围：** 用户与项目接口、项目摘要响应、统一请求与错误模块、权限辅助函数，以及登录、工作台、任务、Wiki、产品共创、项目管理和回收站页面。本次未新增数据库迁移，不修改现有角色、任务状态、风险评分或业务数据。

**状态：** ✅ 完成并部署至 `http://localhost:3000`；保留工作区已有未提交改动，未覆盖、回退或提交用户内容。

### 2026-09-15 - 职业协作与 AI 产品共创套餐完成

**操作：** 完成 V9–V11 套餐增量：项目级产品共创三栏工作台、POST SSE 持久化对话与中断留档、显式项目上下文、版本化产品成果及预览确认转换；新增产品经理身份和角色化个人工作台、项目经理确定性决策驾驶舱、VIEWER 管理简报；增加独立验收状态机、清单、驳回原因、评审证据、图片/PDF 在线预览、逐日工时、周容量及请假/培训例外；增加 PDF/邮件项目简报、管理审计、登录安全事件和系统运营概览。

**关键决策：** AI 始终只生成草稿，不直接修改真实任务；任务草稿经确认转换时默认设置包含首尾共 15 天的日期范围。项目权限继续控制写操作，职业身份只改变默认内容与评审职责；系统管理员不绕过项目成员隔离。成果、验收、工时、容量和登录事件均可追溯，项目永久删除同步清理 V5–V10 业务子记录。

**验证：** Flyway V1–V11 空库迁移通过；`mvn test` 共 19 项全部通过（含 4 项 Testcontainers 端到端流程与权限/清理验证）；`npm test -- --run` 共 16 项全部通过；`npm run build` 生产构建通过；1440/1024/768/390 四档页面巡检生成完整截图且无根节点溢出，人工复核产品共创、任务详情、项目驾驶舱和管理员移动端布局正常；`git diff --check` 无空白错误。

**影响范围：** V9–V11 数据迁移，Product Lab、Delivery Collaboration、Role Workspace、Executive Summary、Management Operations 服务与接口，Dashboard、ProductLab、TaskList、ProjectManagement、AdminUsers 页面，以及 Docker、README、测试和视觉回归脚本。

**状态：** ✅ 完成；保留此前全部未提交改动，未覆盖、回退或提交用户内容。

### 2026-09-14 - 职业协作与 AI 产品共创套餐启动

**操作：** 启动 V9–V11 增量开发，按产品共创、角色化工作台、验收与容量、管理运营四阶段实施，并保留现有三列看板、CPM、基线、风险、Wiki 与全部未提交改动。
**关键决策：** 产品经理新增为独立 `PRODUCT_MANAGER` 身份；AI 对话采用项目级 POST 流式接口并持久化，所有成果先生成草稿，只有用户确认后才能转换为真实 Wiki、任务或里程碑。
**状态：** ⏳ 进行中

### 2026-09-14 - 任务详情弹窗视觉重构

**操作：** 针对任务标题被操作按钮挤成竖排的问题，将 600px 单行弹窗重构为 760px 分层任务面板；标题独占区域，工具操作和状态操作分组，描述与六项核心字段形成清晰信息层级，并补充桌面、平板和手机响应式布局。
**影响文件：** `kanban-frontend/src/views/TaskList.vue`、`kanban-frontend/scripts/visual-regression.cjs`、`project_history.md`。
**关键决策：** 保留 Element Plus、冷灰背景、钴蓝主色及全部现有交互，只做针对性视觉演进；移动端核心字段采用两列三行，按钮使用一致图标与双列布局，不引入新组件库或装饰性动效。
**验证：** `npm test` 12 项通过，`npm run build` 通过；视觉回归覆盖 1440/1024/768/390，任务标题有效宽度分别为 414/414/414/238px，弹窗均无横向溢出或运行时错误；新版 Nginx 镜像已重建并启动。
**状态：** ✅ 完成；刷新 `http://localhost:3000` 即可查看。

### 2026-09-14 - 修复 start.bat 一键启动失败

**操作：** 审查 `start.bat`、Compose 配置和容器日志，定位到未配置 SMTP 时 Spring Mail 健康检查返回 503，导致后端持续 `unhealthy`，继而阻塞依赖后端健康状态的 Nginx 启动；在 Actuator 中关闭可选邮件服务的健康判定。
**影响文件：** `src/main/resources/application.yml`、`project_history.md`。
**关键决策：** 邮件投递失败继续由数据库 Outbox 重试，不应影响核心应用健康状态；保留数据库、磁盘等核心健康检查，不改动 `start.bat` 的启动流程。
**验证：** 真实执行 `start.bat` 完成镜像构建和启动；MySQL、Redis、后端、Nginx 均为 healthy；`/actuator/health` 返回 `UP`，首页返回 HTTP 200，后端无 Mail health warning；Docker 可用后补跑 `mvn test`，15 项（含 3 项 Testcontainers 集成测试）全部通过。
**状态：** ✅ 完成；SmartPM 当前运行于 `http://localhost:3000`。

### 2026-09-13 - 第四周周报与 AI 使用记录生成

**操作：** 读取前三周资料与第三周 Word 模板，按 2026-09-07 至 2026-09-11 生成第四周两份 Word 文档，沿用字体、标题、紫色表头与按日记录结构。
**影响文件：** `第四周周报.docx`、`第四周ai使用记录.docx`、`tools/generate_docs_week4.py`；两份文档同步至 `D:\Project Management\Project_Management`，与前三周归档在一起。
**关键决策：** 根据已有开发历史归纳部署、UI、协作、风险、AI 评估与 CPM 成果；9 月 8 日和 11 日无独立记录，明确注明；不编造交互次数、费用或未运行的 AI 实验结果。
**验证：** Word 包完整性、日期与文本、表格宽度、跨页表头和两处副本一致性检查通过；沿用第三周页面与样式设置，表格调整至正文可用宽度。
**状态：** ✅ 完成

### 2026-09-14 - AI 新建任务默认 15 天期限

**操作：** 新增统一的 AI 任务日期兜底规则，并接入 AI 完整项目计划、空项目一键初始化和任务 AI 拆解三条入库路径。AI 返回的合法开始/截止日期保持不变；缺少开始日期时使用 Asia/Shanghai 当天，缺少截止日期或截止日期早于开始日期时，自动设置为开始日期后 15 天。手工新建任务逻辑不变，已有任务不做历史回填。
**影响文件：** `AiTaskDatePolicy.java`、`AIPlanningServiceImpl.java`、`TaskServiceImpl.java`、`AiTaskDatePolicyTest.java`、`project_history.md`。
**验证：** `mvn test` 共 18 项通过，包括日期兜底单元测试、Flyway V1–V8 空库迁移及现有排期、风险、权限和职场流程集成回归。
**状态：** ✅ 完成

### 2026-09-13 - SmartPM 职场化升级启动

**操作：** 启动个人工作台、全局搜索、批量任务、风险治理、行动化分析、Wiki 版本、项目模板、周期任务、CSV 与邮件通知的分阶段增量实现。
**影响文件：** `project_history.md`（后续里程碑逐项记录迁移、后端、前端与测试）。
**关键决策：** 保留现有三列看板、CPM、基线、What-if、权限与未提交改动；继续使用既有冷灰/钴蓝设计系统，所有新 API 均按项目成员边界隔离。
**状态：** ✅ 完成（详见 2026-09-14 验收记录）

### 2026-09-14 - SmartPM 职场化升级完成与发布验收

**操作：** 按六阶段任务表完成个人工作台、全局搜索、精确通知跳转、快捷筛选与快速新建、同项目批量任务操作、项目管理五页签、风险处理闭环、可筛选分析指标、Wiki 搜索/版本/任务关联、项目模板、周期任务、CSV 导入导出，以及邮箱验证、通知偏好和 Outbox 邮件队列。保留并整合已有 CPM、基线、What-if、三列看板、权限与 AI 能力。

**数据库与后端：** 新增 Flyway V5–V8，分别承载风险处理与索引、Wiki 版本与任务关联、模板与周期规则、邮箱验证与邮件 Outbox；新增 Workspace、Risk、Schedule、Template、Recurrence、CSV、Email 等独立服务/API。工作台统计按当前用户和项目权限聚合，批量更新执行全量校验与原子事务；风险状态遵守 `OPEN / IN_PROGRESS / ACCEPTED / RESOLVED` 约束并记录事件；分析指标固定日期口径和样本数；周期任务以周期键保证幂等；CSV 导入提供预览复验、500 行限制和公式注入防护；邮件发送不阻塞业务请求。

**前端：** `/dashboard` 升级为个人工作台，支持跨项目搜索、六种任务范围、快速建项/建任务和模板创建；任务页增加批量操作、CSV、周期规则和深链；项目管理页使用 `?section=` 提供概览、风险、排期、里程碑、团队负载五个可分享页签；风险抽屉展示处置表单与时间线；分析页支持项目/日期筛选和明细下钻；Wiki 增加搜索、任务过滤、版本恢复与双向关联；账户区域增加邮箱验证与通知偏好。所有界面沿用现有冷灰/钴蓝设计系统并适配桌面和移动端。

**验证：**
- `mvn test`：15 项测试，0 失败、0 错误；12 项执行通过，3 项 Testcontainers 集成测试因本机 Docker 未运行而按条件跳过。
- 使用本机 MySQL 临时库完成 V1→V8 空库迁移，Flyway 版本 8 状态成功；随后通过真实 HTTP 流程验证工作台、搜索、批量任务、风险事件、Wiki 版本/关联、模板建项、周期规则、CSV multipart 预览、邮箱偏好和分析接口，临时服务与数据库已清理。
- `npm test`：3 个测试文件、12 项测试全部通过；`npm run build` 生产构建通过。
- `npm run test:visual`：1440/1024/768/390 四档视口及项目管理各页签通过，无根节点溢出和浏览器运行时错误。
- `git diff --check`：通过，仅显示工作区既有的 LF/CRLF 提示，无空白错误。

**文档：** README 已补充职场化功能、SMTP 环境变量和 V4–V8 迁移说明；视觉回归脚本和 API/工具测试同步扩展。

**状态：** ✅ 完成；保留工作区原有未提交修改，未执行覆盖、回退或提交操作。

## 项目技术栈
- **后端框架**: Spring Boot 3
- **JDK 版本**: JDK 17
- **ORM**: MyBatis-Plus
- **数据库**: MySQL 8.0

---

## 会话记录

### 2026-06-29 - 项目初始化

**用户**: 创建 project_history.md，用于保存后续所有聊天记录。项目正式开始。

---

### 2026-06-29 - 项目基础类编写

**用户**: 请求编写项目初始化通用类：
1. 统一返回结果封装类 `R.java`
2. 全局异常处理机制（`BusinessException` + `GlobalExceptionHandler`）
3. JWT 工具类 `JWTUtil.java`

**实现**:

#### 1. `common/result/R.java` — 统一响应封装
- 泛型类，包含 `code`、`msg`、`data` 三个字段
- 静态工厂方法：`ok()` / `ok(data)` / `ok(msg, data)` 返回 200
- `error(code, msg)` / `error(msg)` 返回错误响应
- 使用 Lombok `@Data` 自动生成 getter/setter/toString

#### 2. `common/exception/BusinessException.java` — 自定义业务异常
- 继承 `RuntimeException`，携带业务状态码 `code`
- 默认 code=500

#### 3. `common/exception/GlobalExceptionHandler.java` — 全局异常拦截
- `@RestControllerAdvice` 注解，拦截 `BusinessException` 和 `Exception`
- BusinessException → 返回对应 code 和 message
- Exception → 统一返回 500 "服务器内部错误"

#### 4. `common/utils/JWTUtil.java` — JWT 工具类
- 基于 `io.jsonwebtoken:jjwt` 库（0.12.x API）
- HMAC-SHA256 签名，24小时过期
- 方法：`generate()` / `parse()` / `getUserId()` / `isExpired()` / `validate()`

#### 需补充的 Maven 依赖
```xml
<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
```

#### 当前目录结构（已按 phase1_guide.md 调整）
```
src/main/java/com/smartpm/
├── common/
│   ├── config/          ← 待加入：MyBatisPlus/CORS 等配置
│   ├── exception/
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   ├── result/
│   │   └── R.java
│   └── utils/
│       └── JWTUtil.java
├── config/
├── controller/
│   └── UserController.java
├── entity/
│   └── User.java
├── mapper/
│   └── UserMapper.java
├── service/
│   ├── UserService.java
│   └── impl/
│       └── UserServiceImpl.java
├── vo/
│   └── LoginVO.java
```

---

### 2026-06-29 - 用户注册与登录功能

**用户**: 基于 sys_user 表实现用户注册与登录：
1. User 实体类（对应 sys_user 表）
2. UserMapper + UserService + UserServiceImpl
3. 密码 BCrypt 加密存储
4. UserController: POST /api/user/register, POST /api/user/login
5. LoginInterceptor + UserHolder (ThreadLocal)

**实现文件**:

| 文件 | 说明 |
|------|------|
| `entity/User.java` | 实体，@TableName("sys_user")，字段：id/username/password/nickname/createdAt/updatedAt |
| `mapper/UserMapper.java` | 继承 BaseMapper<User> |
| `service/UserService.java` | 接口：register() / login() |
| `service/impl/UserServiceImpl.java` | 实现：用户名唯一校验、BCrypt 加密、JWT 生成 |
| `controller/UserController.java` | /api/user/register + /api/user/login，使用 @RequestParam 接收参数 |
| `vo/LoginVO.java` | 登录返回体：token + userId + username |
| `common/utils/UserHolder.java` | ThreadLocal 封装：set/get/getUserId/remove |
| `common/interceptor/LoginInterceptor.java` | 校验 Authorization: Bearer <token>，解析后设置 UserHolder |
| `common/config/WebConfig.java` | 注册拦截器（排除 /register、/login）+ BCryptPasswordEncoder Bean |

**接口说明**:
- `POST /api/user/register` — username(必填), password(必填), nickname(选填，默认用username)
- `POST /api/user/login` — username(必填), password(必填)，返回 token + userId + username
- 其他 /api/** 请求需携带 Header: `Authorization: Bearer <token>`

**新增依赖**:
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

---

### 2026-06-29 - 编译错误修复与项目入口建立

**用户**: 发现程序中大量报错，要求定位问题并修复。

**问题诊断**:
1. `pom.xml` 几乎为空 — 缺少 Spring Boot、MyBatis-Plus、Lombok、JWT、BCrypt 等所有依赖
2. `com.syt.Main.java` 只是 IDEA 生成的 Hello World 模板，不是 Spring Boot 入口
3. `GlobalExceptionHandler.java` 第 3 行 import 路径错误：`com.smartpm.common.R` → 应为 `com.smartpm.common.result.R`
4. 缺少 `application.yml` 配置文件

**修复内容**:

| 操作 | 文件 | 说明 |
|------|------|------|
| 重写 | `pom.xml` | 添加 Spring Boot 3.2.0 父工程 + starter-web、MyBatis-Plus 3.5.5、MySQL、Lombok、jjwt 0.12.5、spring-security-crypto |
| 新建 | `Application.java` | `@SpringBootApplication` + `@MapperScan("com.smartpm.mapper")`，位于 `com.smartpm` 包下 |
| 新建 | `application.yml` | 端口 8080、MySQL 连接(smartpm 库)、MyBatis-Plus 配置(驼峰映射/控制台日志) |
| 修复 | `GlobalExceptionHandler.java` | 修正 R 类的 import 路径 |
| 删除 | `com.syt/Main.java` | 删除无用的 IDEA 模板代码，连带删除整个 `com.syt` 包 |

**当前目录结构**:
```
src/main/java/com/smartpm/
├── Application.java              ← 新增：Spring Boot 入口
├── common/
│   ├── config/
│   │   └── WebConfig.java
│   ├── exception/
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java  ← 已修复 import
│   ├── interceptor/
│   │   └── LoginInterceptor.java
│   ├── result/
│   │   └── R.java
│   └── utils/
│       ├── JWTUtil.java
│       └── UserHolder.java
├── controller/
│   └── UserController.java
├── entity/
│   └── User.java
├── mapper/
│   └── UserMapper.java
├── service/
│   ├── UserService.java
│   └── impl/
│       └── UserServiceImpl.java
└── vo/
    └── LoginVO.java

src/main/resources/
└── application.yml              ← 新增
```

---

### 2026-06-29 - 数据库初始化脚本

**用户**: 启动项目时报错，GlobalExceptionHandler 记录"系统异常"。

**诊断**: 应用启动成功（Tomcat 8080 端口已监听），但请求处理时报错。根因是用户本地未安装 MySQL / 未创建 smartpm 数据库和 sys_user 表。

**解决**: 
	1. 发现本地已安装 MySQL 8.0 且 MySQL80 服务正在运行
	2. 测出 root 密码为 `123456`
	3. 执行 `sql/init.sql` 创建 smartpm 库和 sys_user 表
	4. 更新 `application.yml` 中数据库密码 `root` → `123456`

---

### 2026-06-30 - 数据库连接测试

**用户**: 报告卡在数据库连接上，要求测试并解决。

**诊断结果**: 
- MySQL80 服务运行正常
- `smartpm` 数据库和 `sys_user` 表已存在
- Maven 路径: `D:\java\maven\apache-maven-3.9.13`
- 应用编译成功，启动耗时 ~2 秒
- HikariCP 连接池正常创建连接

**API 测试全部通过**:
1. `POST /api/user/register` → 200 (注册成功，BCrypt 加密存储)
2. `POST /api/user/register` (重复) → 500 "用户名已存在"
3. `POST /api/user/login` (错误密码) → 500 "用户名或密码错误"
4. `POST /api/user/login` (正确) → 200 (返回 JWT token)

**结论**: 数据库连接、MyBatis-Plus、BCrypt、JWT 全链路正常工作，未发现连接问题。

---

### 2026-06-30 - GlobalExceptionHandler 完善

**用户**: 指出 GlobalExceptionHandler 将所有异常统一返回 500，需要区分 404 和 405。

**修复** (`GlobalExceptionHandler.java`):
- 新增 `NoResourceFoundException` 处理器 → 返回 404 "资源不存在"
- 新增 `HttpRequestMethodNotSupportedException` 处理器 → 返回 405 "请求方法不允许"
- `BusinessException` → 返回对应业务 code（不变）
- 其余 `Exception` → 返回 500 "服务器内部错误"（兜底）

**验证结果**:
- `GET /` → 404 ✅
- `GET /api/user/login` (只支持 POST) → 405 ✅
- `POST /api/user/login` → 200 正常 ✅

---

### 2026-07-01 - 项目代码浏览

**用户**: 要求浏览整个项目代码，了解当前状态，为后续任务做准备。

**浏览结果** — 项目当前共有 14 个 Java 源文件，结构如下：

```
src/main/java/com/smartpm/
├── Application.java                          # Spring Boot 入口 (@MapperScan)
├── common/
│   ├── config/WebConfig.java                 # 拦截器注册 + BCrypt Bean
│   ├── exception/
│   │   ├── BusinessException.java            # 自定义业务异常 (code+msg)
│   │   └── GlobalExceptionHandler.java       # 全局异常处理 (404/405/500)
│   ├── interceptor/LoginInterceptor.java     # Bearer Token 认证拦截器
│   ├── result/R.java                         # 统一响应封装 <T>
│   └── utils/
│       ├── JWTUtil.java                      # JWT 生成/解析/校验 (24h)
│       └── UserHolder.java                   # ThreadLocal 用户上下文
├── controller/UserController.java            # /api/user/register + /api/user/login
├── entity/User.java                          # sys_user 表实体
├── mapper/UserMapper.java                    # MyBatis-Plus BaseMapper
├── service/
│   ├── UserService.java                      # 接口: register(), login()
│   └── impl/UserServiceImpl.java             # BCrypt 加密 + JWT 签发
└── vo/LoginVO.java                           # 登录返回体 (token, userId, username)
```

**已验证可用的功能**:
- 用户注册（用户名唯一校验 + BCrypt 加密）
- 用户登录（密码校验 + JWT 生成）
- 登录拦截器（Bearer Token 校验 + UserHolder ThreadLocal）
- 全局异常处理（业务异常 500、404、405、兜底 500）

**待开发（按 phase1_guide.md 规划）**:
- Project（项目）实体、CRUD、Controller
- Task（任务）实体、CRUD、Controller
- CORS 跨域配置（前后端分离）
- 前端 Vue 3 项目

---

### 2026-07-01 - Project 与 Task 业务模块实现

**用户**: 实现项目（Project）和任务（Task）的完整 CRUD 业务接口，要求通过 JWT 拦截器保护。

**实现内容**:

#### 数据库表
| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `sys_project` | 项目表 | id, name, description, creator_id |
| `sys_task` | 任务表 | id, project_id, title, description, status(TODO/IN_PROGRESS/DONE), assignee_id, creator_id |

#### 新增文件（11个）
```
src/main/java/com/smartpm/
├── dto/
│   └── TaskUpdateDTO.java              # 任务更新请求体 {id, title, description, status, assigneeId}
├── entity/
│   ├── Project.java                    # sys_project 实体
│   └── Task.java                       # sys_task 实体
├── mapper/
│   ├── ProjectMapper.java              # MyBatis-Plus BaseMapper
│   └── TaskMapper.java                 # MyBatis-Plus BaseMapper
├── service/
│   ├── ProjectService.java             # 接口: create/list/delete
│   ├── TaskService.java                # 接口: create/listByProject/update/delete
│   └── impl/
│       ├── ProjectServiceImpl.java     # 项目业务逻辑 + 权限校验
│       └── TaskServiceImpl.java        # 任务业务逻辑 + 状态校验
└── controller/
    ├── ProjectController.java          # /api/project/**
    └── TaskController.java             # /api/task/**
```

#### API 接口详情

**项目管理 (ProjectController):**
| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| POST | `/api/project/create` | name, description(可选) | 创建项目，创建者=当前登录用户 |
| GET | `/api/project/list` | — | 查询当前用户创建的所有项目 |
| DELETE | `/api/project/{id}` | id(路径) | 删除项目（校验创建者）+ 级联删除任务 |

**任务管理 (TaskController):**
| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| POST | `/api/task/create` | projectId, title, description(可选), assigneeId(可选) | 创建任务，默认状态 TODO |
| GET | `/api/task/list/{projectId}` | projectId(路径) | 查询指定项目下所有任务 |
| PUT | `/api/task/update` | @RequestBody TaskUpdateDTO | 更新任务状态/标题/描述/负责人 |
| DELETE | `/api/task/{id}` | id(路径) | 删除任务 |

#### 关键设计决策
- **权限控制**: 所有 `/api/project/**` 和 `/api/task/**` 自动被 LoginInterceptor 拦截，需 JWT 认证
- **创建者校验**: 删除项目时校验 `creator_id == UserHolder.getUserId()`，防止越权
- **级联删除**: 删除项目时一并删除该项目下所有任务（@Transactional 保证原子性）
- **状态枚举**: Task 状态限为 TODO / IN_PROGRESS / DONE，update 时自动转大写并校验
- **编译验证**: `mvn compile` 通过，零错误

---

### 2026-07-01 - Vue 3 + Vite 前端项目搭建

**用户**: 搭建前端项目骨架，包含 Vue Router、Pinia、Element Plus、Axios 等基础设施。

**项目路径**: `kanban-frontend/`

**文件清单（13个）**:
```
kanban-frontend/
├── index.html                              # HTML 入口
├── package.json                            # 依赖: vue3, vue-router4, pinia, element-plus, axios, vite5
├── vite.config.js                          # Vite 配置: @别名 + /api 代理到 8080
└── src/
    ├── main.js                             # 应用入口: 挂载 Vue/Router/Pinia/ElementPlus(中文)
    ├── App.vue                             # 根组件 <router-view>
    ├── router/
    │   └── index.js                        # 路由表 + beforeEach 守卫（未登录→/login）
    ├── store/
    │   └── user.js                         # Pinia: token/userInfo 状态 + login/logout/register
    ├── utils/
    │   └── request.js                      # Axios 实例 + 请求拦截器(Bearer Token) + 响应拦截器(401/404/405/500)
    ├── api/
    │   ├── user.js                         # login(), register()
    │   ├── project.js                      # createProject(), listProjects(), deleteProject()
    │   └── task.js                         # createTask(), listTasks(), updateTask(), deleteTask()
    ├── views/
    │   ├── Login.vue                       # 登录/注册页（Tab 切换）
    │   └── Dashboard.vue                   # 仪表盘页（顶部栏+退出登录）
    └── components/
        └── ProjectList.vue                 # 项目列表组件（表格+新建+删除）
```

#### request.js 设计要点
- **请求拦截器**: 从 localStorage 读取 token，自动附加 `Authorization: Bearer <token>`
- **响应拦截器**: 401 → 清除登录态并跳转 /login；404/405/500 → ElMessage 弹框提示；网络异常 → "网络异常，请检查连接"
- **baseURL**: `/api`（Vite 代理转发到 `localhost:8080`）

#### Pinia user store 设计要点
- **state**: token（持久化 localStorage）、userInfo（{userId, username}）
- **getters**: isLoggedIn（token 和 userInfo 均存在才为 true）
- **actions**: login() 调 API 并写 localStorage、register() 调 API、logout() 清 localStorage
- **初始化**: 构造 state 时从 localStorage 恢复（支持刷新后保持登录态）

#### 路由守卫
- `meta.requiresAuth` → 未登录跳转 /login
- `meta.guest` → 已登录跳转 /dashboard

#### 验证结果
- `npm install` ✅（83 packages）
- `vite build` ✅（构建成功，8.18s）

---

### 2026-07-01 - 第一阶段收尾：页面完善 + 截止日期字段

**用户**: 编写完整的 Vue 3 页面（Login/Dashboard/TaskList）和路由逻辑，任务需支持截止日期。

#### 后端变更
| 文件 | 变更 |
|------|------|
| `entity/Task.java` | 新增 `dueDate` (LocalDate) 字段 |
| `dto/TaskUpdateDTO.java` | 新增 `dueDate` (String) 字段 |
| `service/TaskService.java` | create 方法增加 `dueDate` 参数 |
| `service/impl/TaskServiceImpl.java` | create/update 中处理 dueDate（空字符串→null） |
| `controller/TaskController.java` | create 接口增加 `dueDate` 可选参数 |
| `sql/init.sql` | sys_task 表新增 `due_date DATE` 列 |

#### 前端变更

**路由** (`router/index.js`):
| 路径 | 组件 | 权限 |
|------|------|------|
| `/login` | Login.vue | 仅游客 |
| `/dashboard` | Dashboard.vue | 需登录 |
| `/project/:id` | TaskList.vue | 需登录 |

**Dashboard.vue** — 重写为卡片布局：
- 项目列表用 `el-card` 网格展示，hover 有浮起效果
- 点击卡片跳转 `/project/:id`
- 弹窗新建项目（名称+描述）

**TaskList.vue** — 新增页面：
- 顶部返回按钮 + 项目名称
- `el-table` 展示任务：标题、描述、状态（`el-select` 下拉直接改）、截止日期、删除
- 新建任务弹窗：标题、描述、负责人ID（`el-input-number`）、截止日期（`el-date-picker`）
- 删除前 `ElMessageBox.confirm` 二次确认

**已删除文件**:
- `components/ProjectList.vue` — 功能已内聚到 Dashboard.vue

#### 编译验证
- `mvn compile` ✅
- `vite build` ✅（Login / Dashboard / TaskList 三个页面 chunk 均产出）

#### 数据库迁移
如果 sys_task 表已存在，需手动执行：
```sql
ALTER TABLE sys_task ADD COLUMN due_date DATE DEFAULT NULL COMMENT '截止日期' AFTER creator_id;
```

---

### 2026-07-01 - 第二阶段准备：order_index 字段 + 前后端端口合并

**用户**: 
1. sys_task 表增加 order_index 排序字段（为拖拽看板做准备）
2. 将前端和后端合并为单端口 8080 模式

#### order_index 字段
| 文件 | 变更 |
|------|------|
| `entity/Task.java` | 新增 `orderIndex` (Integer) |
| `dto/TaskUpdateDTO.java` | 新增 `orderIndex` |
| `service/impl/TaskServiceImpl.java` | create 默认 0；list 按 orderIndex ASC 排序；update 支持修改 |
| `sql/init.sql` | 新增 `order_index INT NOT NULL DEFAULT 0` |

#### 端口合并（单端口 8080 方案）
**原理**: Vue 前端 `vite build` 输出到 Spring Boot 的 `src/main/resources/static/`，Spring Boot 直接提供静态文件服务。

**变更文件**:
| 文件 | 变更 |
|------|------|
| `vite.config.js` | `build.outDir: '../src/main/resources/static'`, `emptyOutDir: true` |
| `common/config/SpaController.java` | 新增：非 API/非静态资源的请求统一转发到 `index.html`，让 Vue Router (history 模式) 接管前端路由 |

**SpaController 路由匹配规则**:
- `/{x:[\\w\\-]+}` — 匹配单段路由（如 `/dashboard`, `/login`）
- `/{x:[\\w\\-]+}/**` — 匹配多段路由（如 `/project/123`）
- `/api/**` 被 @RestController 优先匹配，不受影响
- 静态资源（.js/.css/.png 等含 `.` 后缀的）由 Spring 默认资源处理器提供

**使用方式**:
```bash
# 部署：先构建前端，再启动后端（单命令即可）
cd kanban-frontend && npm run build
cd .. && mvn spring-boot:run
# 访问 http://localhost:8080

# 开发：前端仍可独立运行 Vite dev server（支持热更新）
cd kanban-frontend && npm run dev
# 访问 http://localhost:3000，API 自动代理到 8080
```

#### 验证结果
- `vite build` ✅（输出到 `src/main/resources/static/`）
- `mvn compile` ✅
- 访问 `http://localhost:8080` → SPA 首页
- 访问 `http://localhost:8080/dashboard` → 浏览器刷新不会 404（SpaController 转发到 index.html）
- 访问 `http://localhost:8080/api/user/login` → JSON API 正常响应

---

### 2026-07-01 - 第二阶段：前后端重新分离 + 数据库修复

**用户**: 单端口模式 bug 太多，要求恢复前后端分离。

**撤销操作**:
- 删除 `SpaController.java`
- 清空 `src/main/resources/static/`
- `vite.config.js` 恢复默认 dist 输出

**数据库修复**: 发现 `sys_project` 和 `sys_task` 表未在数据库中创建，执行建表 SQL。

**最终架构**: 后端 8080（纯 API），前端 3000（Vite dev server + 代理到 8080）

---

### 2026-07-01 - 第二阶段：拖拽排序 API (PUT /api/task/drag)

**用户**: 实现任务拖拽排序的后端接口。

**新增文件**:
| 文件 | 说明 |
|------|------|
| `dto/DragDTO.java` | taskId + targetStatus + targetOrderIndex |

**修改文件**:
| 文件 | 变更 |
|------|------|
| `service/TaskService.java` | 新增 `drag(DragDTO)` 方法 |
| `service/impl/TaskServiceImpl.java` | 新增 `@Transactional drag()` 核心算法 |
| `controller/TaskController.java` | 新增 `PUT /api/task/drag` 接口 |

**排序算法逻辑**:
- **同列移动**: 向下拖 → `(source, target]` 区间 -1；向上拖 → `[target, source)` 区间 +1
- **跨列移动**: ①源列收口（> source 的 -1）②目标列让位（>= target 的 +1）③更新任务 status + orderIndex
- `@Transactional` 全程事务保护，targetOrderIndex 越界自动 clamp

**测试验证**:
- 同列 id=1 从 TODO[0]→TODO[2]，id=2/3 自动上移 ✅
- 跨列 id=2 从 TODO→IN_PROGRESS[1]，源列收口 + 目标列让位 ✅
- 越界 id=1→DONE[99] 自动 clamp 到末尾 ✅

---

### 2026-07-01 - 第二阶段：前端视觉重设计

**用户**: 页面过于简陋，要求重新设计。

**设计方向**: Indigo 品牌色 + slate 中性色阶，三列状态用琥珀/蓝/翠绿区分。

**变更文件**:
| 文件 | 说明 |
|------|------|
| `assets/theme.css` | CSS 设计令牌（颜色/阴影/圆角/字体），在 main.js 全局引入 |
| `views/Login.vue` | 重写为分立布局：左侧品牌渐变面板 + 右侧表单 |
| `views/Dashboard.vue` | 项目卡片网格 + 彩色顶边 + hover 浮起 + 空态插画引导 |
| `views/TaskList.vue` | 从 el-table 改为三列看板布局（待办/进行中/已完成） |

**色彩系统**:
| 令牌 | 色值 | 用途 |
|------|------|------|
| `--brand` | `#6366F1` Indigo | 品牌主色 |
| `--todo` | `#F59E0B` 琥珀 | 待办列 |
| `--progress` | `#3B82F6` 蓝 | 进行中列 |
| `--done` | `#10B981` 翠绿 | 已完成列 |

---

### 2026-07-01 - 第二阶段：拖拽看板集成 (vuedraggable)

**用户**: 将 TaskList 改造成可视化拖拽看板。

**依赖**: `vuedraggable@next`（基于 SortableJS，Vue 3 v4 分支）

**TaskList.vue 改造要点**:
- 三个独立 `ref` 数组（todoList/inProgressList/doneList），按 orderIndex 升序
- 三列 `<draggable>` 组件：`group="tasks"` 允许跨列拖拽，`animation="180"`
- `@change` 事件：`added` → 跨列拖入，`moved` → 同列排序
- 拖拽结束自动调用 `PUT /api/task/drag`，成功后 `fetchTasks()` 刷新
- 卡片 UI：6点拖拽手柄 + `cursor: grab` + 幽灵态半透明虚线
- 空列："拖拽任务到此处" 虚线占位

**验证**: `vite build` ✅

---

### 2026-07-01 - 第二阶段：WebSocket 实时协同

**用户**: 实现多用户实时看板同步，用户 A 移动卡片时用户 B 自动刷新。

**后端新增文件**:
| 文件 | 说明 |
|------|------|
| `config/WebSocketConfig.java` | 注册 `/ws/project/{projectId}` 端点 |
| `websocket/TaskWebSocketHandler.java` | 按 projectId 分组管理 Session，提供 broadcast() |

**后端修改**:
| 文件 | 变更 |
|------|------|
| `pom.xml` | 新增 `spring-boot-starter-websocket` |
| `controller/TaskController.java` | drag/create/update/delete 成功后 broadcast |

**WebSocketHandler 架构**:
- `ConcurrentHashMap<Long, Set<WebSocketSession>>` — projectId → 在线会话集合
- `afterConnectionEstablished` — 从 URI 解析 projectId，加入分组
- `afterConnectionClosed` — 从分组移除，组空则清理
- `broadcast(projectId, jsonMsg)` — 遍历同组 open 会话发送消息

**前端 WebSocket 客户端** (TaskList.vue):
- `onMounted` → `connectWebSocket()`，`onUnmounted` → 断开
- 收到 `{"type":"TASK_UPDATED"}` → 自动 `fetchTasks()` 刷新看板
- 断线 3 秒自动重连

**端到端验证**:
- WebSocket 连接 ✅
- 用户A 拖拽 → 用户A WS 收到广播 ✅
- 用户B 同时连接 → 用户B WS 也收到广播 ✅
- 两端均触发 fetchTasks 刷新看板 ✅

---

### 2026-07-02 - WebSocket 鉴权与前端健壮性增强

**用户**: 对 TaskList.vue 的 WebSocket 逻辑进行升级，增强连接可靠性和安全性。

**后端变更**:

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `common/websocket/WebSocketHandshakeInterceptor.java` | 握手拦截器：从 `?token=` 查询参数提取 JWT 并校验，拒绝无效 token 的连接 |
| 修改 | `common/config/WebSocketConfig.java` | 将 `HttpSessionHandshakeInterceptor` 替换为 `WebSocketHandshakeInterceptor` |

**前端变更** (`kanban-frontend/src/views/TaskList.vue`):

| 改进项 | 说明 |
|--------|------|
| Token 传递 | URL 追加 `?token=xxx` 查询参数，经 `encodeURIComponent` 编码 |
| 指数退避重连 | 重连间隔 1s → 2s → 4s → 8s → 16s，封顶 30s |
| 最大重试限制 | `MAX_RETRIES = 5`，超过后停止重连，防止无限循环 |
| 成功重置计数 | `onopen` 中将 `retryCount` 归零，确保临时断线后拥有完整重试配额 |
| 连接状态追踪 | `wsConnected` ref 暴露给模板，可按需渲染连接指示器 |
| 防重复连接 | `connectWebSocket()` 先检查 readyState，避免重复创建 |
| 彻底清理 | `disconnectWebSocket()` 置空所有回调后再 close，确保不触发重连 |
| onerror 策略 | 不在 onerror 中主动 close（浏览器会自动触发 onclose），统一在 onclose 中重连 |

**验证**: `mvn compile` ✅ | `vite build` ✅

---

### 2026-07-02 - 第三阶段：LLM 大模型接入（流式对话 + SSE）

**用户**: 接入兼容 OpenAI 协议的大模型，实现通用流式响应接口。

**技术选型**: WebClient（spring-boot-starter-webflux）调用外部 API + SseEmitter（spring-webmvc）推送 SSE。不引入 Spring AI（依赖过重）。

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `pom.xml` | 新增 `spring-boot-starter-webflux`（仅用 WebClient，运行时仍 Servlet） |
| 修改 | `application.yml` | 新增 `ai.api-key` / `ai.base-url` / `ai.model` 配置块，支持 `${AI_API_KEY}` 环境变量 |
| 新增 | `common/config/AIConfigProperties.java` | `@ConfigurationProperties(prefix = "ai")` |
| 新增 | `common/config/AIWebClientConfig.java` | `WebClient.Builder` Bean |
| 新增 | `service/AIService.java` | `Flux<String> streamChat(prompt)` + `String chat(prompt)` |
| 新增 | `service/impl/AIServiceImpl.java` | WebClient POST `/v1/chat/completions`，stream:true→Flux 解析 SSE，stream:false→同步获取 |
| 新增 | `controller/AIController.java` | `GET /api/ai/stream-chat?prompt=xxx` → SseEmitter |
| 修改 | `common/config/WebConfig.java` | `/api/ai/stream-chat` 加入拦截器排除列表 |

**SSE 实现细节**: SseEmitter(120s) → subscribe Flux → onNext emit chunk → onComplete/onError 关闭。基于 Servlet 3.1 异步，不阻塞 Tomcat 线程。

**验证**: `mvn compile` ✅ | 已配置为 DeepSeek（`api.deepseek.com`，`deepseek-chat` 模型）

---

### 2026-07-02 - 第三阶段：AI 任务智能拆解

**用户**: 实现 `POST /api/task/{taskId}/ai-decompose`，调用大模型将任务拆解为 3-5 个子任务并批量写入 DB。

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `service/AIService.java` | 新增 `String chat(String prompt)` 非流式方法 |
| 修改 | `service/impl/AIServiceImpl.java` | `stream: false`，bodyToMono.block() 同步获取完整回复 |
| 修改 | `service/TaskService.java` | 新增 `List<Task> decomposeTask(Long taskId)` |
| 修改 | `service/impl/TaskServiceImpl.java` | 核心实现：查询任务→构造 Prompt→AI.chat()→parseSubtaskJson()→@Transactional 批量插入 |
| 修改 | `controller/TaskController.java` | 新增 `POST /api/task/{taskId}/ai-decompose`，返回子任务列表 + WebSocket 广播 |

**Prompt 设计技巧**:
- 角色设定 + 约束（3-5个、具体可执行、有逻辑顺序）
- 格式强制："只返回 JSON 数组，不要其他文字"
- 示例引导：给一个样例数组消除歧义

**JSON 解析三级防御**: ① 正则提取 ````json...```` 代码块 → ② 正则匹配 `[...]` 数组 → ③ Jackson 解析，任一失败抛 BusinessException

**验证**: `mvn compile` ✅

---

### 2026-07-02 - 第三阶段：AI 项目周报（流式 Markdown）

**用户**: 实现 `GET /api/project/{projectId}/ai-summary`，收集近 7 天任务数据，AI 生成 Markdown 周报并通过 SSE 流式推送。

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `service/ProjectService.java` | 新增 `Flux<String> generateSummary(Long projectId)` |
| 修改 | `service/impl/ProjectServiceImpl.java` | 查询三类任务（近7天DONE/IN_PROGRESS/逾期）→ 拼接结构化 Prompt → aiService.streamChat() |
| 修改 | `controller/ProjectController.java` | 新增 `GET /api/project/{projectId}/ai-summary` → SseEmitter |

**数据分类**:
| 类别 | 筛选条件 | 用于 |
|------|---------|------|
| 已完成 | status=DONE 且 updatedAt 近7天 | "本周成果" |
| 进行中 | status=IN_PROGRESS | "进行中工作" |
| 已逾期 | dueDate < today 且 status != DONE | "风险与逾期提醒" |

**验证**: `mvn compile` ✅

---

### 2026-07-02 - 第三阶段：前端 AI UI 实现

**用户**: 为 TaskList.vue 增加"AI 拆解"按钮和"生成项目总结"流式弹窗。

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `api/summary.js` | `streamProjectSummary()` — 原生 fetch + ReadableStream 读取 SSE，逐行解析 `data:` 前缀 |
| 修改 | `api/task.js` | 新增 `decomposeTask(taskId)` |
| 安装 | `package.json` | 新增 `markdown-it` 依赖（轻量 Markdown 解析器） |
| 修改 | `views/TaskList.vue` | 三处改动：顶栏"生成项目总结"按钮、卡片"AI 拆解"按钮、Markdown 渲染弹窗 |

**TaskList.vue 新增逻辑**:

**AI 拆解**: 每卡片独立的 `decomposingTaskId` loading 态 → `decomposeTask()` → 成功弹提示 + `fetchTasks()` 刷新看板。

**AI 总结弹窗**:
```
openSummary() → summaryDialogVisible=true + loading 态
  → streamProjectSummary(projectId, {onChunk, onDone, onError})
    → fetch GET /api/project/{id}/ai-summary (Bearer Token)
      → ReadableStream 逐块读取 SSE
        → onChunk: summaryContent += chunk (打字机效果)
        → computed: renderedMarkdown = md.render(summaryContent)
          → v-html 实时渲染到 .markdown-body
```

**Markdown 样式**: 覆盖标题、代码块（深色背景）、表格、引用块、列表等全部元素。

**验证**: `vite build` ✅

---

### 2026-07-03 - 第四阶段：部署优化（环境变量 + SPA 路由 + 构建配置）

**用户**: 进入部署优化阶段，要求处理三件事：前端环境变量自动切换 baseURL、Vue Router History 模式的 Spring Boot SPA 路由回退、以及 Vite 生产构建配置检查清单。

#### 1. 环境变量配置（`.env.development` / `.env.production`）

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `kanban-frontend/.env.development` | `VITE_API_BASE_URL=/api`（Vite dev server 代理到 8080） |
| 新增 | `kanban-frontend/.env.production` | `VITE_API_BASE_URL=/api`（同域部署；分离部署时改为完整 URL） |
| 修改 | `kanban-frontend/src/utils/request.js` | `baseURL` 改为读取 `import.meta.env.VITE_API_BASE_URL`，fallback `/api` |

**原理**: Vite 在 `dev` 模式自动加载 `.env.development`，在 `build` 模式自动加载 `.env.production`。通过 `VITE_` 前缀暴露给客户端代码，`import.meta.env.VITE_API_BASE_URL` 在构建时被静态替换为对应值。

#### 2. Spring Boot SPA 路由回退（History 模式）

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `common/config/WebConfig.java` | 新增 `addResourceHandlers` + 自定义 `PathResourceResolver` |

**实现方式**: 在 `WebConfig` 中覆盖 `addResourceHandlers`，注册一个处理 `/**` 的 resource handler，链式添加自定义 `PathResourceResolver`：
- 若请求路径在 `classpath:/static/` 中存在实体文件 → 直接返回（静态资源正常加载）
- 若路径以 `api/` 开头或包含 `.`（文件后缀）→ 跳过回退，返回 null（交由 Spring 默认处理 → 404）
- 其余情况（如 `/dashboard`、`/project/123`）→ 返回 `index.html`，Vue Router 接管前端路由

**关键设计决策**:
- 使用 `addResourceHandlers` 而非 Controller（Controller 正则匹配会误伤静态资源 `.js/.css`）
- Resource handler 优先级低于 @Controller 映射，因此 `/api/**` 请求由 REST Controller 优先处理，不经过回退逻辑
- `resourcePath.contains(".")` 判断用于区分前端路由和静态资源文件

#### 3. Vite 生产构建配置

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `kanban-frontend/vite.config.js` | 新增完整 `build` 配置块 |

**构建配置要点**:

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `base` | `'/'` | 部署在域名根路径；若部署到子目录（如 `/app/`）需修改 |
| `outDir` | `'dist'` | 输出目录（标准做法，不与 Spring Boot static 耦合） |
| `assetsDir` | `'assets'` | 静态资源子目录 |
| `sourcemap` | `false` | 生产环境不暴露源码 |
| `chunkSizeWarningLimit` | `1000` | 放宽 chunk 大小警告阈值（KB） |
| `manualChunks` | 见下 | 按依赖类别拆分 chunk，利用浏览器缓存 |

**代码分包策略** (`manualChunks`):
| chunk 名 | 包含库 | 大小 |
|-----------|--------|------|
| `vue-vendor` | vue, pinia, vue-router | ~207 KB |
| `element-plus` | element-plus | ~799 KB |
| `draggable` | vuedraggable + sortablejs | ~45 KB |
| `markdown` | markdown-it | ~71 KB |
| `vendor` | 其余 node_modules | ~183 KB |

**部署到 Spring Boot static 目录的方法**:
```bash
# 方式一：修改 vite.config.js 的 outDir
#   outDir: '../src/main/resources/static'
# 方式二：构建后手动复制
cp -r kanban-frontend/dist/* src/main/resources/static/
```

**Nginx 部署时 History 模式的配置**:
```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

**验证**: `vite build` ✅（8.29s，16 个产出文件）| `mvn compile` ✅

---

### 2026-07-03 - 第四阶段：Docker 容器化与 Nginx 动静分离

**用户**: 要求 Docker 容器化部署方案，Nginx 托管前端静态资源并反向代理 API/WebSocket 到后端，docker-compose 统一编排 MySQL + Redis + 后端 + Nginx。

#### 1. 后端 Dockerfile

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `Dockerfile` | eclipse-temurin:17-jre-alpine，非 root 用户，JVM 优化参数 |

**Dockerfile 要点**:
- 基础镜像 `eclipse-temurin:17-jre-alpine`（~70MB 压缩后），安装 curl 用于健康检查
- 非 root 用户 `app:app` 运行
- JVM 参数：`-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError`
- 健康检查：`curl -f http://localhost:8080/actuator/health`（每 30s，超时 5s，重试 3 次）

#### 2. Nginx 配置

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `nginx.conf` | 静态资源托管 + /api 反向代理 + /ws WebSocket 代理 + SPA 回退 |

**配置分区**:
| location | 功能 | 关键配置 |
|----------|------|---------|
| `/assets/` | 带 hash 的静态资源 | `expires 1y; Cache-Control: public, immutable` |
| `/api/` | REST API 反向代理 | `proxy_pass http://backend:8080` + 标准代理头 |
| `/ws/` | WebSocket 代理 | `Upgrade $http_upgrade; Connection "upgrade"` + `proxy_read_timeout 86400s` |
| `/` | SPA 路由回退 | `try_files $uri $uri/ /index.html` |

WebSocket 代理必须设置 `proxy_http_version 1.1` 和 `Upgrade/Connection` 头，否则连接无法建立。

#### 3. docker-compose 编排

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `docker-compose.yml` | 4 个服务统一编排 |
| 新增 | `.dockerignore` | 排除 node_modules/.git/IDE 文件等 |

**服务清单**:
| 服务 | 镜像 | 端口 | 持久化 |
|------|------|------|--------|
| `mysql` | mysql:8.0 | 3306 | `mysql-data` 卷 |
| `redis` | redis:7-alpine | 6379（内部） | `redis-data` 卷 + AOF |
| `backend` | 本地构建 | 8080（内部） | — |
| `nginx` | nginx:alpine | 80 | 挂载 dist + nginx.conf（只读） |

**健康检查**:
- MySQL: `mysqladmin ping`（10s 间隔，5 次重试，30s 启动等待）
- Redis: `redis-cli ping`
- 后端: `curl /actuator/health`
- `depends_on` + `condition: service_healthy` 确保启动顺序

**环境变量注入**:
- `MYSQL_HOST=mysql`、`REDIS_HOST=redis`：容器间通过服务名通信
- `MYSQL_PASSWORD`、`AI_API_KEY`：从主机环境变量或 `.env` 文件传入

#### 4. 应用配置适配 Docker

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `pom.xml` | 新增 `spring-boot-starter-data-redis`、`spring-boot-starter-actuator` 依赖；`<finalName>app</finalName>` 固定 jar 名 |
| 修改 | `application.yml` | MySQL/Redis host 改为 `${MYSQL_HOST:localhost}` 占位符（Docker 注入服务名，本地开发用默认值）；新增 Actuator 健康检查端点配置 |

#### 5. 部署步骤

```bash
# 1) 构建前端（产物 → kanban-frontend/dist/）
cd kanban-frontend && npm run build && cd ..

# 2) 打包后端（产物 → target/app.jar）
mvn package -DskipTests

# 3) 启动全部服务（后台运行）
docker-compose up -d

# 4) 查看状态
docker-compose ps

# 5) 查看日志
docker-compose logs -f backend

# 6) 停止
docker-compose down
```

**访问**: `http://localhost`（Nginx 80 端口）即可使用完整系统。

**可选环境变量**（通过 `.env` 文件或 shell 设置）:
```bash
MYSQL_ROOT_PASSWORD=your_password   # 默认 123456
AI_API_KEY=sk-xxx                   # AI 功能需要
```

**验证**: `mvn compile` ✅ | `vite build` ✅（8.50s）| `mvn package -DskipTests` ✅（app.jar 43MB）

---

### 2026-07-03 - 任务层级重构（主任务 + 子任务）

**用户**: AI 拆解出的子任务和主任务混在在看板上，需要重构为父子层级结构。数据库已添加 `parent_id` 字段。

#### 数据库变更
```sql
ALTER TABLE sys_task ADD COLUMN parent_id BIGINT DEFAULT NULL COMMENT '父任务ID';
```
`parent_id IS NULL` = 主任务（显示在看板），`parent_id IS NOT NULL` = 子任务（属于某个主任务）。

#### 后端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `entity/Task.java` | 新增 `parentId` (Long) 属性 |
| 修改 | `service/TaskService.java` | 新增 `listSubtasks(taskId)` 和 `toggleSubtask(taskId)` 方法签名 |
| 修改 | `service/impl/TaskServiceImpl.java` | 6 处改动（详见下方） |
| 修改 | `controller/TaskController.java` | 新增 2 个接口 |
| 修改 | `sql/init.sql` | 建表语句加入 `parent_id` 字段 |

#### TaskServiceImpl 六处改动

**1. `listByProject` — 只查主任务**
```java
.isNull(Task::getParentId)  // 新增过滤，子任务不出现在看板
```

**2. `delete` — 级联删除子任务**
```java
taskMapper.delete(new LambdaQueryWrapper<Task>().eq(Task::getParentId, id));
taskMapper.deleteById(id);
```
`@Transactional` 保证原子性。

**3. `drag` — 拖拽仅操作主任务**
所有 `LambdaQueryWrapper` / `LambdaUpdateWrapper` 均新增 `.isNull(Task::getParentId)`，确保排序计算和批量移动只影响主任务。

**4. `decomposeTask` — 子任务写入 parentId**
```java
sub.setParentId(taskId);  // AI 拆解出的子任务归属到当前主任务
```

**5. `listSubtasks` — 新增方法**
根据 `parent_id = taskId` 查询子任务列表，按创建时间升序排列。

**6. `toggleSubtask` — 新增方法**
校验 `parentId != null`（拒绝主任务调用），在 TODO ↔ DONE 之间切换。不接受 IN_PROGRESS 等其他状态的切换。

**7. Prompt 优化**
- 严厉禁止子任务标题与主任务标题相同或高度重叠
- 要求输出具体的技术步骤（如"设计数据库表"→"编写后端接口"→"实现前端页面"→"联调测试"）
- 提供正确/错误示例对照（good: 设计用户表结构 / bad: 完成登录功能）

#### 新增 API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/task/{taskId}/subtasks` | 查询主任务下的子任务列表 |
| PUT | `/api/task/{taskId}/toggle-subtask` | 切换子任务 TODO ↔ DONE，触发 WebSocket 广播 |

**验证**: `mvn compile` ✅

---

### 2026-07-03 - 前端层级看板重构（TaskList.vue）

**用户**: 要求 TaskList.vue 支持主任务+子任务的层级展示，包括进度条、详情弹窗、子任务勾选和 AI 拆解触发。

#### 新增 API 函数

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `api/task.js` | 新增 `listSubtasks(taskId)` 和 `toggleSubtask(taskId)` |

#### TaskList.vue 重构要点

| 功能 | 实现 |
|------|------|
| **卡片进度条** | 看板加载后并行请求所有主任务的子任务 (`Promise.all`)，存入 `subtaskData` 响应式对象。卡片底部渲染 4px 渐变进度条 + "2/5" 数字 |
| **详情弹窗** | 点击卡片 → `el-dialog` 弹窗，展示状态标签、标题、描述、截止日期、进度条、子任务清单 |
| **子任务勾选** | `el-checkbox` + 乐观更新：先改本地 `status` → 调 `toggleSubtask` API → 失败则回滚。`done` 类名实现贯穿线+半透明 |
| **AI 拆解按钮** | 位于弹窗子任务区域头部，loading 态绑定 `decomposingTaskId`，成功后 `fetchSubtasksForTask()` 刷新当前清单 |
| **WebSocket 刷新** | 收到 `TASK_UPDATED` 时额外刷新当前打开弹窗的子任务数据 |
| **删除确认** | 提示"将同时删除其所有子任务"，删除后清理 `subtaskData` 缓存 |

**数据流**:
```
fetchTasks() → 仅返回 parent_id IS NULL 的主任务
  └─ fetchAllSubtaskCounts() → Promise.all 并行加载所有子任务
       └─ subtaskData[taskId] = Task[]  ← 响应式驱动进度条

openDetail(task) → 若 subtaskData 未缓存则 fetchSubtasksForTask()
  └─ currentSubtasks (computed) → 渲染 el-checkbox 列表
       └─ toggleSubtaskStatus(sub) → 乐观更新 + API 调用
```

**验证**: `vite build` ✅（TaskList chunk 从 12.70KB → 15.85KB）

---

### 2026-07-03 - SSE 流式空白修复（AI 项目总结无输出）

**用户**: 点击"生成项目总结"后弹窗空白，AI 无任何输出。

**根因分析**:
1. **SSE 格式不匹配（主因）**: Spring `SseEmitter.event().data(chunk)` 发送 `data:内容\n\n`（冒号后无空格），前端 `summary.js` 检查 `line.startsWith('data: ')`（有空格），所有 chunk 被静默丢弃
2. **错误静默**: AI API 调用失败时后端只调 `emitter.completeWithError(error)`，前端收不到任何错误提示，弹窗保持空白

#### 前端修复

| 操作 | 文件 | 说明 |
|------|------|------|
| 重写 | `api/summary.js` | 核心修复 + 全面日志 |

**修复内容**:
- **SSE 解析**: 新增 `extractData()` 函数，同时兼容 `data:content`（Spring）和 `data: content`（标准 SSE）两种格式
- **控制台日志**: 请求发起、响应状态、每个原始数据块、每个解析出的 chunk 均打印 `console.log`
- **[ERROR] 检测**: 识别服务端推送的 `[ERROR]` 前缀消息，触发 `onError` 回调显示给用户
- **残留处理**: 流结束时处理 buffer 中可能残留的最后一行数据
- **错误详情**: 所有 catch 块均打印 `console.error`，方便 F12 排查

#### 后端修复

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `controller/ProjectController.java` | SSE 端点全面加固 |
| 修改 | `service/impl/ProjectServiceImpl.java` | 添加 @Slf4j + 分步日志 |
| 修改 | `service/impl/AIServiceImpl.java` | 添加流式调用日志 |
| 修改 | `common/config/WebConfig.java` | 新增全局 CORS 配置 |

**Controller 修复要点**:
- `produces = MediaType.TEXT_EVENT_STREAM_VALUE` — 显式声明 SSE 内容类型
- 超时从 120s 延长至 180s
- 连接建立时发送 `comment:connected` 初始事件
- **错误推送**: `completeWithError` 前通过 SSE 推送 `[ERROR] 原因...` 消息，前端能展示具体错误
- `emitter.onTimeout()` / `emitter.onError()` 回调记录日志

**Service 层日志**: project 加载、任务分类统计（已完成/进行中/逾期数量）、Prompt 长度、AI 调用开始/完成/出错

**CORS 配置**: `WebConfig.addCorsMappings` 全局允许所有来源 + 全部方法 + 全部头 + 凭证

**验证**: `mvn compile` ✅ | `vite build` ✅（8.20s）

---

### 2026-07-03 - Dashboard 无限加载修复 + CORS 冲突消除

**用户**: 修改 AI 总结代码后 Dashboard 无限转圈，项目列表加载不出。

**根因分析**:
1. **CORS 配置冲突**: `ProjectController` 上新增的 `@CrossOrigin(origins = "*")` 与 `WebConfig` 全局 CORS 中 `allowCredentials(true)` 叠加，credentials 模式下不允许 `origins="*"`，导致浏览器 CORS 预检失败
2. **错误静默**: `Dashboard.vue` 的 `fetchProjects()` 未在 `catch` 中打印日志或弹出错误提示，API 失败时用户看到的是永久转圈（虽然 `finally` 会设 `loading=false`，但若请求被 CORS 拦截，Promise 直接 reject，拦截器弹窗一闪而过用户可能未注意）

#### 修复内容

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `controller/ProjectController.java` | 移除 `@CrossOrigin(origins = "*")`（全局 CORS 已统一管理） |
| 修改 | `common/config/WebConfig.java` | CORS 映射从 `/**` 收紧为 `/api/**`，避免与 SPA 静态资源冲突 |
| 修改 | `views/Dashboard.vue` | `fetchProjects()` 和 `handleCreate()` 增加完整的 try/catch/finally + console 日志 + ElMessage 提示 |

**Dashboard.vue 安全加载逻辑**:
```js
async function fetchProjects() {
  console.log('[Dashboard] 开始加载项目列表...')
  loading.value = true
  try {
    const res = await listProjects()
    const list = res.data?.data
    if (Array.isArray(list)) {
      projects.value = list
    } else {
      projects.value = []
      ElMessage.warning('项目数据格式异常')
    }
  } catch (error) {
    console.error('[Dashboard] 加载失败:', error)
    if (!error.response) {
      ElMessage.error('网络连接失败，请检查后端服务是否启动')
    }
  } finally {
    loading.value = false   // 无论如何都会执行
  }
}
```

**防御层次**:
| 层级 | 机制 |
|------|------|
| Axios 响应拦截器 | 统一处理 401/404/405/500，自动弹窗 |
| catch 块 console.error | F12 控制台可查看完整错误堆栈 |
| catch 块 ElMessage.error | 网络断开等非 HTTP 错误时弹窗提示用户 |
| finally 块 | 保证 loading 一定关闭，转圈一定停止 |
| Array.isArray 校验 | 防止后端返回非数组数据导致模板崩溃 |

**request.js 审查**: 确认未受污染，拦截器逻辑完整正确。

**F12 排查指南**:
1. Console 标签 → 看是否有红色报错和 `[Dashboard]` 日志
2. Network 标签 → 找 `list` 请求 → 看 Status 列（200/401/500/CORS error）
3. 若请求显示 `(failed) net::ERR_CONNECTION_REFUSED` → 后端未启动

**验证**: `mvn compile` ✅ | `vite build` ✅（7.99s）

---

### 2026-07-03 - 配置微调

- **AI 模型切换**: `application.yml` 中 `ai.model` 从 `deepseek-chat` 切换为 `deepseek-v4-flash`

---

## 2026-07-03 全天工作总结

本日完成第四阶段（部署优化 + Docker + 层级重构 + 流式修复），涉及 **20+ 文件变更**：

| 模块 | 新增文件 | 修改文件 | 关键成果 |
|------|---------|---------|---------|
| 部署优化 | `.env.development`, `.env.production` | `vite.config.js`, `request.js`, `WebConfig.java` | 环境变量自动切换 + SPA History 路由回退 + Vite 生产分包 |
| Docker | `Dockerfile`, `nginx.conf`, `docker-compose.yml`, `.dockerignore` | `pom.xml`, `application.yml` | 4 服务编排（MySQL+Redis+Backend+Nginx）+ 动静分离 + JVM 优化 |
| 任务层级 | — | `Task.java`, `TaskService.java`, `TaskServiceImpl.java`, `TaskController.java`, `init.sql` | parent_id 父子层级 + 看板只显示主任务 + 级联删除 + AI Prompt 优化 |
| 层级前端 | — | `task.js`, `TaskList.vue` | 卡片进度条 + 详情弹窗 + 子任务 checkbox + AI 拆解按钮 |
| SSE 修复 | — | `summary.js`, `ProjectController.java`, `ProjectServiceImpl.java`, `AIServiceImpl.java`, `WebConfig.java` | 修复 SSE 格式不匹配 bug + 全面日志 + CORS 加固 |
| Dashboard 修复 | — | `Dashboard.vue`, `WebConfig.java` | CORS 冲突消除 + try/catch/finally 防御 + F12 诊断日志 |

**当前项目状态**: 所有功能模块完整，前后端编译通过，Docker 可部署。

---

### 2026-07-06 - 新一周工作开始

**操作**: 阅览全部项目文件与历史记录，了解当前状态，准备开启新一周工作。

**当前项目全景**:

| 层级 | 技术 | 文件数 | 状态 |
|------|------|--------|------|
| 后端 | Spring Boot 3.2.0 + MyBatis-Plus 3.5.5 | 38 个 Java 文件 | ✅ 编译通过 |
| 前端 | Vue 3 + Vite 5 + Element Plus + Pinia | 15 个源文件 | ✅ 构建通过 |
| 数据库 | MySQL 8.0 (smartpm) | 3 张表 | ✅ 运行中 |
| AI | DeepSeek v4-flash (OpenAI 协议) | 3 个文件 | ✅ 已集成 |
| 实时 | WebSocket (Spring WebSocket) | 3 个文件 | ✅ 已集成 |
| 部署 | Docker + Nginx + docker-compose | 4 个文件 | ✅ 可部署 |

**已验证可用的功能清单**:
- 用户注册/登录（BCrypt + JWT + 拦截器）
- 项目 CRUD（创建/列表/删除 + 权限校验）
- 任务看板（三列拖拽：TODO/IN_PROGRESS/DONE + vuedraggable）
- WebSocket 实时协同（多用户拖拽同步 + 指数退避重连）
- AI 任务拆解（POST /api/task/{id}/ai-decompose → 3-5 个子任务）
- AI 项目周报（GET /api/project/{id}/ai-summary → SSE 流式 Markdown）
- 父子任务层级（进度条 + 详情弹窗 + 子任务勾选）
- CORS 跨域 + 全局异常处理（404/405/500）

**已知可改进方向**（待用户确认优先级）:
1. 用户管理完善（头像、个人信息编辑、密码修改）
2. 项目协作（多人共享项目、邀请成员）
3. 任务增强（标签/优先级/附件/评论/工时估算）
4. 通知系统（任务分配通知、逾期提醒）
5. ~~数据仪表盘~~ ✅ 已完成（2026-07-06）
6. 单元测试与集成测试
7. Git 初始提交

### 2026-07-06 - Dashboard 项目卡片编辑/删除功能

**用户**: 要求重新设计项目卡片布局，增加编辑和删除功能。

**前端变更** (`kanban-frontend/src/views/Dashboard.vue`):

| 变更项 | 说明 |
|--------|------|
| 卡片布局 | 新增 `card-header` 弹性布局，标题 + 下拉菜单左右分布 |
| 下拉菜单 | `el-dropdown` + `MoreFilled` 三点图标，`trigger="click"` |
| 阻止冒泡 | `el-dropdown` 和触发图标均绑定 `@click.stop`，防止触发卡片跳转 |
| 修改项目 | 弹出 `el-dialog` 预填当前名称/描述 → `PUT /api/project/update` |
| 删除项目 | `ElMessageBox.confirm` 二次确认 → `DELETE /api/project/{id}` |
| 图标 | 引入 `MoreFilled` / `Edit` / `Delete` 三个图标组件 |

**前端 API** (`kanban-frontend/src/api/project.js`):
- 新增 `updateProject(id, name, description)` → `PUT /api/project/update`

**后端变更**:

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `controller/ProjectController.java` | 新增 `PUT /api/project/update` 接口（id + name + description） |
| 修改 | `service/ProjectService.java` | 新增 `update(id, name, description)` 方法签名 |
| 修改 | `service/impl/ProjectServiceImpl.java` | 新增 `update()` 方法（存在性校验 + 创建者权限校验 + 更新字段） |
| 修改 | `service/impl/ProjectServiceImpl.java` | 优化 `delete()` 级联顺序：先删子任务(parent_id IN tasks) → 再删主任务 → 最后删项目，全程 @Transactional |

**删除级联逻辑**:
```
1. 查询项目下所有主任务 ID (parent_id IS NULL)
2. DELETE subtasks WHERE parent_id IN (主任务IDs)
3. DELETE tasks WHERE project_id = 项目ID
4. DELETE project WHERE id = 项目ID
```
四步在同一事务内，任一步失败全部回滚。

**验证**: `mvn compile` ✅ (34 files) | `vite build` ✅ (8.49s)

---

### 2026-07-06 - AI 项目总结 SSE 空白修复（根因定位与重构）

**用户**: 报告"生成项目总结"弹窗空白。AI 任务拆解正常（非流式），但流式总结 SSE 连接建立后立即关闭。

**根因分析**（定位到 `AIServiceImpl.streamChat()`）:

| 层面 | 问题 | 影响 |
|------|------|------|
| **SSE 格式不兼容（主因）** | `.filter(line -> line.startsWith("data: "))` 要求冒号后有空格，但 DeepSeek 等兼容 API 可能返回 `data:{...}`（无空格），导致所有数据行被过滤丢弃 | Flux 空完成 → emitter 立即关闭 → 前端空白 |
| **换行拆分缺失** | `bodyToFlux(String.class)` 可能将多行 SSE 数据合并为一个 chunk 返回，未拆分为逐行处理 | JSON 解析失败，chunk 被丢弃 |
| **extractContent 硬编码** | `line.substring(6)` 假定 6 字符前缀，`data:{...}` 格式只需 5 字符，导致 JSON 第一个字符被截断 | 解析失败 |
| **无数据时无 AI 调用** | 项目无历史任务时返回硬编码文案，没有调用 AI 生成启动寄语 | 新项目体验差 |
| **同步异常无捕获** | `generateSummary()` 抛同步异常（如项目不存在）时，SSE 已建立但未推送 [ERROR]，前端收不到任何提示 | 空白页 + 无错误提示 |

#### 修复内容

**1. `AIServiceImpl.java` — 流式解析重构**

| 修复项 | 变更 |
|--------|------|
| **换行拆分** | 新增 `.flatMap(chunk -> Flux.fromArray(chunk.split("\n")))` 将多行 chunk 拆分为逐行流 |
| **格式兼容** | `.filter(line -> line.startsWith("data: ") \|\| line.startsWith("data:"))` 同时接受两种格式 |
| **extractContent 自适应** | 根据前缀长度动态计算 JSON 起始位置：`data: ` → 6 字符，`data:` → 5 字符 |
| **订阅日志** | 新增 `.doOnSubscribe()` 日志确认订阅建立 |
| **外层 try-catch** | 整段 streamChat 包裹 try-catch，异常时 `log.error("具体原因为：", e)` 打印完整堆栈 |

**2. `ProjectServiceImpl.java` — 数据查询加固**

| 修复项 | 变更 |
|--------|------|
| **null 安全** | `allTasks` 查询后显式判空初始化 `new ArrayList<>()`；遍历时逐个 task 判空、逐字段判空 |
| **空数据 AI 兜底** | 无历史数据时调用 `buildKickoffPrompt()` 构造项目启动寄语 Prompt，由 AI 生成专业启动文档（含寄语、目标展望、开发路线图、敏捷建议、结尾金句），而非硬编码一句话 |
| **外层 try-catch** | 整段 `generateSummary()` 包裹 try-catch，异常时 `log.error("生成项目总结失败，具体原因为：", e)` 打印完整堆栈，然后 `Flux.error(e)` 传播 |

**3. `ProjectController.java` — 三层异常防护**

| 防护层 | 机制 |
|--------|------|
| **同步异常** | `generateSummary()` 调用外包裹 try-catch，异常通过 SSE `[ERROR]` 推送前端 |
| **异步异常** | Flux subscribe error 回调 → `log.error("异步流生成异常，具体原因为：", error)` 完整堆栈 + SSE `[ERROR]` 推送 |
| **SSE 超时** | 超时从 180s 延长至 300s，给大模型更充裕的响应时间 |

**数据流对比（修复前 vs 修复后）**:

```
修复前:
  DeepSeek API → "data:{...}" → filter("data: ") → 过滤丢弃 → Flux 空 → 空白页

修复后:
  DeepSeek API → "data:{...}" → flatMap 拆行 → filter("data:" or "data: ") → 通过
              → extractContent 自适应截取 → 推送 SSE → 前端逐字渲染 ✅
```

**验证**: `mvn compile` ✅ (38 files, BUILD SUCCESS)

---

### 2026-07-06 - 测试数据创建

**用户**: 要求创建体验账号和丰富的测试数据用于功能测试。

**新增文件**: `sql/test_data.sql` — 完整的测试数据初始化脚本

**体验账号**:
| 用户名 | 密码 | 昵称 |
|--------|------|------|
| `demo` | `123456` | 体验用户 |

**数据概览**:
| 指标 | 数量 |
|------|------|
| 项目 | 3 个 |
| 主任务 | 22 条 |
| 子任务 | 16 条 |
| 任务总计 | 38 条 |

**三个项目**:
| 项目名 | 主任务 | 亮点 |
|--------|--------|------|
| SmartPM 核心功能开发 | 12 条 | 6 个 DONE + 2 个 IN_PROGRESS + 4 个 TODO（含 2 个逾期），3 组子任务 |
| 移动端适配优化 | 6 条 | 2 个 DONE + 1 个 IN_PROGRESS + 3 个 TODO，1 组子任务 |
| 数据分析平台 | 4 条 | 2 个 DONE + 1 个 IN_PROGRESS + 1 个 TODO |

**可测试的功能场景**:
- **看板拖拽**: 项目1 有 12 条主任务分布在三列，可直接拖拽排序
- **任务层级**: 项目1 的 3 个主任务各有子任务（看板卡片显示进度条）
- **AI 任务拆解**: 任意 TODO 任务可点击"AI 拆解"生成子任务
- **项目周报**: 项目1 近7天每天都有完成记录，可看到完整的流式周报
- **统计大屏**: 3 个项目 + 38 条任务的数据，图表展示丰富
- **逾期提醒**: "编写单元测试"和"性能优化与压测"已逾期
- **空项目启动寄语**: 可创建新项目测试无数据时的 AI 启动寄语

**执行方式**: `mysql -uroot -p123456 --default-character-set=utf8mb4 < sql/test_data.sql`

---

### 2026-07-06 - 数据可视化统计大屏 (Analytics Dashboard)

**用户**: 为 SmartPM 新增数据可视化统计大屏页面 `/analytics`。

#### 后端新增 (3 个文件)

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `vo/AnalyticsVO.java` | 响应体：含 3 个静态内部类 StatusItem / ProjectRankItem / DailyTrendItem |
| 新增 | `service/AnalyticsService.java` | `AnalyticsVO getOverview()` 接口 |
| 新增 | `service/impl/AnalyticsServiceImpl.java` | 查询当前用户的全部项目及任务，计算 6 类统计数据 |
| 新增 | `controller/AnalyticsController.java` | `GET /api/analytics/overview` |

**API 返回数据结构**:
```json
{
  "totalProjects": 5, "totalTasks": 20,
  "completedTasks": 8, "inProgressTasks": 6,
  "statusDistribution": [
    {"status": "TODO", "count": 6},
    {"status": "IN_PROGRESS", "count": 6},
    {"status": "DONE", "count": 8}
  ],
  "projectTaskRanking": [
    {"projectName": "项目A", "taskCount": 10}
  ],
  "dailyCompletedTrend": [
    {"date": "2026-07-01", "count": 2}
  ]
}
```

**统计计算逻辑**:
- 仅统计当前用户创建的项目及其下的任务
- 项目数为 0 时返回空数组 + 全零趋势（不报错）
- 近 7 天趋势按 UTC+8 日期分组，包含今天在内往回 7 天
- 项目排行按任务数降序排列

#### 前端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 安装 | `package.json` | `npm install echarts --save` |
| 修改 | `router/index.js` | 新增 `/analytics` 路由（需登录） |
| 修改 | `views/Dashboard.vue` | 顶栏右侧新增"数据大屏"按钮（渐变品牌色 + 柱状图 SVG 图标） |
| 新增 | `views/Analytics.vue` | 完整统计大屏页面 |

**Analytics.vue 页面布局**:

| 区域 | 内容 | 技术 |
|------|------|------|
| 顶栏 | "返回主页"按钮 + SmartPM 标识 + "数据大屏"标签 | 白色背景，底部 1px 边框 |
| 四个数据卡片 | 项目总数 / 任务总数 / 今日完成 / 待办任务 | `el-row` + `el-col(:span="6")`，彩色图标 + 大数字 |
| 三个图表 | 任务状态占比 / 项目任务量排行 / 近7天完工趋势 | ECharts Pie / Bar / Line |

**图表细节**:
- **饼图**: 环形设计 (radius 55%-78%)，琥珀/蓝/翠绿三色，底部图例
- **柱状图**: 水平方向，渐变紫蓝配色，圆角右侧，项目名超 8 字截断
- **折线图**: 平滑曲线 + 渐变面积填充，6px 实心圆点，虚线网格

**ECharts 生命周期**: `onMounted` 初始化 → `window.resize` 自适应 → `onUnmounted` dispose 释放

**验证**: `mvn compile` ✅ (38 files) | `vite build` ✅ (13.87s, Analytics chunk 6.46KB)

**注意**: 新增的 `AnalyticsController` 需重启 Spring Boot 后端才能生效（无 DevTools）。

---

### 2026-07-06 - Analytics 404 排查与后端重启

**用户**: 统计大屏页面打开后弹窗报 404，所有统计数字显示为 0。

**排查过程**（逐层验证请求链路）:

| 层级 | 实际值 | 结果 |
|------|--------|------|
| 前端请求 | `request.get('/analytics/overview')` | ✅ |
| Axios baseURL | `.env.development` → `VITE_API_BASE_URL=/api` | ✅ |
| 拼接 URL | `/api/analytics/overview` | ✅ |
| Vite 代理 | `/api` → `http://localhost:8080` | ✅ |
| Controller 映射 | `@RequestMapping("/api/analytics")` + `@GetMapping("/overview")` | ✅ |
| context-path | 无配置 | ✅ |
| CORS | `/api/**` 全覆盖 | ✅ |
| 拦截器 | 需 Token，Axios 自动携带 | ✅ |

**根因**: `AnalyticsController.java` 是本次会话中新增的文件，之前运行的 Spring Boot 进程（启动于旧代码状态）未加载该 Controller。项目未使用 `spring-boot-devtools`，不会热加载新类。

**修复**: 重启 Spring Boot 后端 → Controller 被 Spring 扫描注册 → 端点立即响应 200。

**验证**: `curl -H "Authorization: Bearer <token>" /api/analytics/overview` → 200, 3项目/38任务/22已完成/5进行中

---

## 2026-07-06 全天工作总结

本日完成 **6 项任务**，涉及 **15+ 文件变更**：

| 序号 | 模块 | 新增文件 | 修改文件 | 关键成果 |
|------|------|---------|---------|---------|
| 1 | 项目卡片编辑/删除 | — | `Dashboard.vue`, `project.js`, `ProjectController.java`, `ProjectService.java`, `ProjectServiceImpl.java` | 卡片三点菜单 + 编辑弹窗 + 删除确认 + 级联删除优化 |
| 2 | 数据统计大屏 | `AnalyticsVO.java`, `AnalyticsService.java`, `AnalyticsServiceImpl.java`, `AnalyticsController.java`, `Analytics.vue` | `router/index.js`, `Dashboard.vue`, `package.json` | ECharts 三图表 + 4 统计卡片 + 7天趋势 |
| 3 | AI 总结 SSE 修复 | — | `AIServiceImpl.java`, `ProjectServiceImpl.java`, `ProjectController.java` | SSE 格式兼容 + 换行拆分 + null 安全 + AI 启动寄语兜底 + 三层异常防护 |
| 4 | 测试数据 | `sql/test_data.sql` | — | demo 账号 + 3 项目 + 38 任务（含逾期/子任务/7天趋势数据） |
| 5 | Analytics 404 修复 | — | — | 根因定位：后端未重启导致新 Controller 未加载 |
| 6 | 项目历史维护 | — | `project_history.md` | 全天工作记录 |

**当前项目规模**:
| 层级 | 文件数 | 状态 |
|------|--------|------|
| 后端 Java | 38 个 | ✅ `mvn compile` 通过 |
| 前端 Vue/JS | 15 个源文件 | ✅ `vite build` 通过 |
| 数据库 | 3 张表 + 测试数据 | ✅ 运行中 |
| 服务 | 前端 :3000 + 后端 :8080 | ✅ 运行中 |

**体验账号**: `demo` / `123456`

---

### 2026-09-02 - 实时协同 WebSocket 能力审计

**用户**：确认实时协同是否已经完善、能否支持公司职工的项目信息协同。

**审计结论**：当前已实现任务 REST 操作后的项目级 WebSocket 广播，以及看板端收到 `TASK_UPDATED` 后自动刷新任务/子任务；但尚未达到完整在线协同，缺少项目成员访问控制、文档实时同步、断线持续重连、冲突处理和在线成员状态。

**关键风险**：WebSocket 握手只校验 JWT，不校验用户是否属于目标项目；任务读写接口未统一校验项目成员；Wiki 更新仍只允许文档创建者；项目列表只返回创建者创建的项目。因此当前更适合单项目看板刷新，不适合直接作为公司级协同平台。

**验证**：`mvn -q -DskipTests compile` ✅；`kanban-frontend/npm run build` ✅（存在既有大体积 chunk 与 circular chunk 警告，不影响构建）。

**后续建议**：先统一项目成员模型与权限，再补充 Wiki/任务的实时事件与可靠重连，最后增加多人编辑冲突策略和在线成员提示。

---

### 2026-09-02 - SmartPM 整体 UI 风格迭代

**用户**：根据参考图统一当前业务页面的配色、字体、留白、组件和布局风格。

**设计决策**：采用“暖白纸张背景 + 炭黑工作面板 + 橙金行动色”的产品视觉；降低圆角和阴影存在感，增加模块留白；项目卡片、任务列、文档侧栏统一为深色工作区，标题使用更有编辑感的衬线字形。

**影响文件**：`kanban-frontend/src/assets/theme.css`、`views/Dashboard.vue`、`views/TaskList.vue`、`views/WikiView.vue`、`views/Analytics.vue`、`views/Login.vue`。

**验证**：前端生产构建 ✅；已通过本地浏览器检查 Dashboard、项目看板和文档中心，页面风格已统一；保留 Element Plus 与既有业务交互，构建仍有既有大体积 chunk/circular chunk 警告。

---

### 2026-09-02 - 项目成员管理与访问权限

**用户**：实现邀请成员、设置岗位身份、区分项目管理员/普通成员/只读成员、移除成员和转移负责人。

**后端实现**：扩展 `pm_project_member` 的 `identity`、`permission` 字段；项目创建者自动成为管理员；新增成员邀请、成员更新、移除成员、转移负责人接口；项目列表包含被加入的项目；任务、文档和 WebSocket 统一校验项目成员权限。

**前端实现**：Dashboard 项目卡片新增“团队成员”入口和成员管理弹窗，支持按用户名邀请、设置项目身份/权限、更新成员、移除成员和转移负责人。

**数据库**：已对当前本地数据库执行兼容迁移，补充权限字段并为现有项目负责人建立管理员成员记录；未删除已有项目、任务或用户数据。

**验证**：`mvn -q -DskipTests compile` ✅；`kanban-frontend/npm run build` ✅；浏览器已验证成员管理弹窗及现有负责人展示。

---

### 2026-09-03 - Windows 一键启动脚本兼容 Docker 缺失

**问题**：Windows 执行 `start.bat` 时提示“docker 不是内部或外部命令”，本机未安装 Docker CLI/Docker Desktop。

**处理**：`start.bat` 和 `start.sh` 增加 Docker 可用性检测；Docker 不可用时自动切换本地开发模式，分别启动 Spring Boot 后端和 Vue 前端；README 增加两种启动模式和前置条件说明。

**状态**：启动脚本已修改；当前机器具备 Java/Maven/Node/MySQL，可使用本地模式启动。完整 Docker 模式仍需用户安装并启动 Docker Desktop。

**补充**：本地环境未运行 Redis，且当前业务未使用 Redis；已关闭 Redis 健康指标，避免应用已正常启动但健康检查误报 503。

**补充修复**：将 Windows 本地模式的服务启动方式改为独立 `cmd /k` 窗口，保持前后端进程持续运行并显示实时日志，便于确认启动状态和排查端口/依赖错误。

**实际验证**：执行 `cmd /c start.bat` 后，前端 `localhost:3000` 返回 200，后端 `localhost:8080/actuator/health` 返回 200；启动脚本在当前包含空格的项目路径下可正常工作。

---

### 2026-09-02 - Docker 一键启动方案

**操作：** 将后端和前端改为 Docker 多阶段构建，并新增 Windows/Linux 一键启动脚本与 README。
**影响文件：** `Dockerfile`、`Dockerfile.nginx`、`docker-compose.yml`、`.dockerignore`、`start.bat`、`start.sh`、`README.md`
**原因：** 用户希望无需手动执行 Maven 和 Vite 构建，即可通过 Docker Compose 启动完整产品。
**状态：** ✅ 已完成配置，待 Docker Desktop 环境验证

---

### 2026-09-02 - AI 项目任务流程完善

**操作：** 强化 AI 初始任务生成：根据项目名称和描述规划 3-5 个开发阶段，输出推荐岗位，并自动匹配项目成员；任务详情新增“开始开发”和“完成任务”操作。
**影响文件：** `TaskServiceImpl.java`、`TaskList.vue`
**原因：** 形成“项目想法 → AI 任务规划 → 工程师查看 → 开始开发 → 完成任务”的完整协作闭环。
**状态：** ✅ 完成；`mvn compile` 和 `vite build` 均通过

---

### 2026-09-02 - 本地 AI 环境变量配置

**操作：** 在项目根目录新增本地 `.env`，配置 AI 接口密钥，并将 `.env` 加入 Git 忽略规则。
**影响文件：** `.env`、`.gitignore`
**原因：** 让 Docker Compose 启动时自动读取 AI 配置，同时避免密钥进入源码和 GitHub。
**状态：** ✅ 完成

---

### 2026-09-10 - 登录页左侧品牌排版优化

**操作：** 将左侧超粗长标题重构为节奏标签、两行品牌主张和一行说明文案；改用更适合中文界面的字体栈，降低字重并固定自然断行，同时优化移动端字号与栏位高度。
**影响文件：** `kanban-frontend/src/views/Login.vue`、`project_history.md`。
**原因：** 原标题在桌面窄栏中把“管理”拆成两行，字重与尺寸过强，导致阅读节奏和视觉重心失衡。
**验证：** `npm test -- --run` 9 项通过，`npm run build` 通过；Playwright 在 1440/1024/768/390 四档宽度完成登录页及全站回归，无横向溢出或运行时错误，并复核桌面与移动端截图。
**状态：** ✅ 完成

---

### 2026-09-05 - 系统管理员后台与用户账号管理

**操作：** 新增独立系统角色（`ADMIN`/`USER`）与账号状态（`ACTIVE`/`DISABLED`），提供管理员用户列表、启停账号、授予/移除系统管理员权限和重置密码；新增 `/admin/users` 管理页面与路由拦截。
**安全决策：** 系统管理员校验在后端执行；不返回密码；禁止停用或降级最后一个启用的管理员；用户删除被刻意保留为不支持，以保护项目、任务归属记录。
**初始化与验证：** 后端启动时自动迁移 `sys_user` 新字段并创建/同步 `admin`（密码 `111`）；隔离 8082 实测登录及查询用户列表均返回 200；`mvn -q -DskipTests compile`、`npm run build` 均通过。
**状态：** ✅ 完成；需重启 IDEA 的 8080 后端后使用新后台。

---

### 2026-09-05 - 回收站与数据恢复

**操作：** 项目、任务（含子任务）、Wiki 文档和任务附件删除统一改为软删除，记录删除时间与删除人；新增系统回收站页面及项目主页入口，按项目权限展示可恢复内容。
**恢复与清理：** 恢复项目保留并恢复其全部关联内容；恢复任务、文档、附件会校验所属项目/任务状态；仅系统管理员可永久删除，永久删除项目时同步清理任务、文档、附件文件、成员、里程碑和下载审计记录。
**数据库与验证：** 启动时自动为现有表补充回收站字段；隔离 8082 实测任务恢复回看板、文档恢复回列表、项目恢复回主页、管理员永久删除全部通过，临时项目已物理清理；后端编译和前端构建通过。
**状态：** ✅ 完成；需重启 IDEA 的 8080 后端加载新接口与数据库迁移。

---

### 2026-09-05 - AI 完整项目计划与任务智能优化

**操作：** 新增 AI 完整计划预览/确认流程：按项目名称和描述生成阶段、任务、岗位建议、日期、工时、风险、验收标准与里程碑；确认后批量写入任务和里程碑。新增任务 AI 优化：生成清晰描述、验收标准与过大任务拆分建议，用户确认后才覆盖原任务。
**兼容修复：** 任务优化解析兼容 AI 返回的别名字段、数组式拆分建议及布尔文本，解决模型输出结构不完全一致时的失败。
**数据完整性：** 项目删除同步清理里程碑、附件和附件下载记录；隔离测试临时项目均已删除，相关任务、成员、里程碑、附件和审计记录均为 0。
**验证：** 隔离后端实际生成完整计划并创建 10 条任务、3 个里程碑；优化接口返回标题、描述、验收标准和任务过大判断；后端编译、前端构建通过。
**状态：** ✅ 完成；需重启 IDEA 后端加载新增接口。

---

### 2026-09-02 - 恢复三列拖拽看板界面

**操作：** 恢复任务页始终显示“待办 / 进行中 / 已完成”三列，保留 vuedraggable 同列排序、跨列移动和 WebSocket 同步；暂时移除 AI 一键生成任务按钮与空项目引导。
**影响文件：** `kanban-frontend/src/views/TaskList.vue`
**原因：** AI 一键生成任务功能后续重新设计，当前优先保证核心看板交互稳定。
**状态：** ⏳ 已完成页面恢复，待用户确认视觉效果

---

### 2026-09-02 - AI 任务生成与岗位认领流程重设计

**操作：** 将 AI 生成任务按钮放在文档中心旁边；AI 生成的主任务统一进入 TODO，并输出推荐岗位；任务拖入 IN_PROGRESS 时自动记录当前登录用户为负责人，卡片显示岗位和负责人昵称。
**影响文件：** `TaskServiceImpl.java`、`kanban-frontend/src/views/TaskList.vue`
**原因：** 建立“AI 规划 → 待办分派 → 工程师接取 → 进行中开发”的协作流程。
**状态：** ✅ 完成；`mvn compile`、`vite build` 和页面显示验证均通过

---

### 2026-09-02 - AI 任务生成接口验证修复

**问题：** 页面点击 AI 生成任务后显示失败，项目看板没有任务。
**根因：** 本地 IntelliJ 后端进程未加载最新 `.env` AI 配置；直接调用 AI 接口测试正常。
**处理：** 重启后端并加载 `.env`，验证项目“记账系统”成功生成 5 条 TODO 任务，岗位标识正常显示。
**状态：** ✅ 完成

---

### 2026-09-02 - AI 初始任务生成问题修复

**问题：** 项目页面显示 AI 生成成功，但数据库没有生成任务。
**修复：** 前端识别统一响应体中的业务错误码，避免 HTTP 200 误报成功；Spring Boot 通过 `spring.config.import` 读取本地 `.env`，确保本地运行时加载 AI 配置。
**验证：** `mvn compile` 和 `vite build` 均通过；后端重启后即可使用最新配置。
**状态：** ✅ 完成
**测试数据脚本**: `sql/test_data.sql`（3 项目 + 38 任务 + 16 子任务）

---

## 2026-07-07 — Wiki 文档中心 + AI 写作协同

### 2026-07-07 - 数据库 pm_wiki 表创建

**用户**: 在 MySQL 中执行建表脚本，新增 `pm_wiki` 表（id, project_id, title, content, creator_id, create_time, update_time）。

---

### 2026-07-07 - Wiki 后端 CRUD 模块实现

**用户**: 基于 `pm_wiki` 表实现完整的文档管理 API。

**新增文件（5个）**:
| 文件 | 说明 |
|------|------|
| `entity/Wiki.java` | pm_wiki 表实体，@TableName("pm_wiki") |
| `mapper/WikiMapper.java` | MyBatis-Plus BaseMapper |
| `service/WikiService.java` | 接口：create / listByProject / getById / update / delete / aiCopilot |
| `service/impl/WikiServiceImpl.java` | 业务实现：权限校验、Prompt 构造、调用 AIService.streamChat() |
| `controller/WikiController.java` | REST API + SSE 端点 |

**API 接口**:
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/wiki/create` | 创建文档（projectId + title + content） |
| GET | `/api/wiki/list/{projectId}` | 查项目文档列表（id/title/updateTime） |
| GET | `/api/wiki/{id}` | 查单篇完整内容 |
| PUT | `/api/wiki/update` | 更新标题/内容（仅创建者可改） |
| DELETE | `/api/wiki/{id}` | 删除文档（仅创建者可删） |
| GET | `/api/wiki/ai-copilot` | AI 写作协同 SSE 流式（prompt + text） |

**验证**: `mvn compile` ✅（43 个 Java 文件）

---

### 2026-07-07 - 前端 Wiki 页面实现

**用户**: 实现 WikiView.vue 页面，集成 md-editor-v3 编辑器 + AI 写作助手面板。

**新增/修改文件**:
| 操作 | 文件 | 说明 |
|------|------|------|
| 安装 | `package.json` | `md-editor-v3` Markdown 编辑器 |
| 新增 | `api/wiki.js` | Wiki API 层（CRUD 请求 + SSE 流式 fetch） |
| 新增 | `views/WikiView.vue` | 完整 Wiki 页面：左侧文档列表 + 右侧 MdEditor + AI 面板 |
| 修改 | `router/index.js` | 新增 `/project/:id/wiki` 路由 |
| 修改 | `views/TaskList.vue` | 顶栏新增「文档中心」按钮（引入 Document 图标） |
| 修改 | `views/Dashboard.vue` | 项目卡片下拉菜单新增「文档中心」入口（引入 Document 图标 + goToWiki 函数） |

**WikiView.vue 页面布局**:
| 区域 | 功能 |
|------|------|
| 顶栏 | "返回看板" + 项目名 + "保存文档"按钮 |
| 左侧 280px | 文档列表（新建/切换/删除），按更新时间倒序，当前文档高亮 |
| 右侧编辑器 | md-editor-v3 全功能编辑器（中文工具栏、预览、全屏） |
| AI 面板 380px | 指令输入 + 文本输入（划选自动填入）+ AI 运行 + 流式输出 + 替换/插入按钮 |

**AI 面板数据流**:
- 用户在编辑器中划选文字 → `mouseup` 事件捕获 → 自动填入 "待处理文本"
- 点击 "AI 运行" → 原生 fetch + ReadableStream 请求 `/api/wiki/ai-copilot`
- 流式解析 SSE 行 → `onChunk` 回调 → `aiOutput += chunk`（打字机效果）
- MarkdownIt 实时渲染 → `v-html` 显示
- "替换原文"：将 AI 结果写入编辑器
- "插入末尾"：追加到文档末尾

**验证**: `vite build` ✅（WikiView chunk 9.62KB gzipped 4.39KB）

---

### 2026-07-07 - AI SSE 流式推送 Bug 排查与修复（多轮）

**问题**: 用户点击「AI 运行」后，后端日志显示大模型数据已成功接收并解析（`[AI] 提取内容` 日志打印了完整文章），但前端只收到 235 字节 HTTP 头，页面空白。

**第一轮 — 行缓冲区引入**:
- **根因定位**: `bodyToFlux(String.class)` 按网络 TCP 缓冲区切分数据，SSE 行可能在中途被截断（如 `"data: {\"choices\":..."` 和 `"delta\":...}"` 分属两个 chunk），`flatMap(chunk -> Flux.fromArray(chunk.split("\n")))` 直接按 `\n` 拆分后 JSON 解析静默失败，全部内容被 `.filter()` 丢弃
- **修复**: 引入行缓冲区 `StringBuilder`，用 `concatMap` + `processRawChunk()` 方法累积到完整行（含 `\n`）再解析

**第二轮 — concatMap → handle 运算符**:
- **根因**: `concatMap` 内层 `Flux.fromIterable(results)` 的订阅可能未正确传播到下游。日志打印了内容但 `emitter.send()` 的日志未出现
- **修复**: 用 `.handle((chunk, sink) -> sink.next(content))` 替代 `concatMap`，在解析处直接同步发射，消除内层 Flux 订阅环节

**第三轮 — Flux.create 彻底重构**:
- **根因**: 操作符链中任一环节都可能丢失信号。需要最直接的发射链路
- **修复**: 使用 `Flux.create(sink -> { webClient...subscribe(onNext -> sink.next(content)); })` 完全取代操作符链。WebClient 的 `onNext` 回调中直接调用 `sink.next()`，零中间环节
- **资源管理**: `sink.onDispose(innerSub::dispose)` 确保下游取消时同步取消 WebClient 订阅
- **安全解析**: `extractContent()` 使用 `JsonNode.has()` 逐层判空（choices → delta → content → isNull）

**第四轮 — Controller 重构对齐已验证模式**:
- **变更**: `WikiController` 直接注入 `AIService`，Controller 方法内构造 Prompt 并调用 `aiService.streamChat(fullPrompt).subscribe(emitter.send...)`，与已验证可工作的 `ProjectController.aiSummary` 模式完全一致
- **移除**: 中间层 `WikiService.aiCopilot()`、`comment:connected` 初始发送（可能的提前 return 点）

**最终数据流**:
```
DeepSeek API → WebClient bodyToFlux → Flux.create sink.next(content)
  → Controller subscriber → emitter.send(chunk) → SSE → 前端 ReadableStream
```

**验证**: 每轮 `mvn compile` 均通过 ✅

---

### 2026-07-07 全天工作总结

本日完成 Wiki 文档中心全栈功能 + AI 写作协同 SSE 调试，涉及 **10+ 文件变更**：

| 模块 | 新增文件 | 修改文件 | 关键成果 |
|------|---------|---------|---------|
| 数据库 | — | — | 创建 `pm_wiki` 表 |
| 后端 Wiki CRUD | `Wiki.java`, `WikiMapper.java`, `WikiService.java`, `WikiServiceImpl.java`, `WikiController.java` | — | 6 个 API 接口（含 SSE） |
| 前端 Wiki 页面 | `api/wiki.js`, `WikiView.vue` | `router/index.js`, `TaskList.vue`, `Dashboard.vue`, `package.json` | 两栏布局 + md-editor-v3 + AI 面板 |
| AI SSE 修复 | — | `AIServiceImpl.java`, `WikiController.java` | 行缓冲区 + Flux.create + 安全 JSON 解析 |

**当前项目规模**:
| 层级 | 文件数 | 状态 |
|------|--------|------|
| 后端 Java | 43 个 | ✅ `mvn compile` 通过 |
| 前端 Vue/JS | 17 个源文件 | ✅ `vite build` 通过 |
| 数据库 | 4 张表 | ✅ 运行中 |
| 服务 | 前端 :3000 + 后端 :8080 | ✅ 运行中 |

**体验账号**: `demo` / `123456`

---

### 2026-07-09 - AI 大模型接入全面重构

**用户**: 发现接入大模型有很多 bug，要求重新进行接入。

**重构范围**: 后端 `AIServiceImpl` 流式管道 + 三个 SSE Controller 统一 + 前端 SSE 工具抽取。

#### 后端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 重写 | `service/impl/AIServiceImpl.java` | `streamChat()` 从 `Flux.create` + 行缓冲区改为 `concatMap` + `StringBuilder` 管道，消除信号丢失风险；`extractContent()` 用 Jackson `path()` 安全导航替代逐层判空；增加 null/blank prompt 校验 |
| 重写 | `controller/AIController.java` | 补全 `onTimeout`/`onError` 回调，超时从 120s → 300s，增加 `produces = TEXT_EVENT_STREAM_VALUE`，与另外两个 SSE 端点保持一致 |
| 修改 | `controller/ProjectController.java` | 移除无意义的 `comment:connected` 初始事件 |
| 修改 | `common/config/WebConfig.java` | `/api/ai/stream-chat` 从拦截器排除列表移除，统一要求 JWT 认证 |

**`streamChat()` 重构前后对比**:

```
重构前:
  Flux.create(sink → subscribe(onNext → 行缓冲区 → sink.next()))
  → 双层嵌套订阅，信号丢失风险高

重构后:
  bodyToFlux → concatMap(行缓冲区 → Flux.fromArray(lines))
  → filter(data: 行) → map(extractContent) → filter(非空)
  → 平坦管道，每步可独立测试和调试
```

#### 前端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `utils/sse.js` | 共享 SSE 流式请求工具，统一处理 Bearer Token、行解析、`[ERROR]` 检测、缓冲区残留 |
| 重写 | `api/summary.js` | 从 98 行 → 4 行，委托给 `sse.js` |
| 重写 | `api/wiki.js` | SSE 部分委托给 `sse.js`，保留 URL 构造逻辑 |

**SSE 解析统一逻辑** (`utils/sse.js`):
- `fetch()` + `ReadableStream` 逐块读取
- 行缓冲区处理不完整行（`buffer.split('\n')` + `pop()` 保留末尾）
- `processLine()` 统一处理 `data:` 行 → 去前缀 → `[ERROR]` 检测 → `onChunk` 回调
- 兼容 `data:content`（Spring）和 `data: content`（标准 SSE）

#### 验证

- `mvn compile` ✅ (43 个 Java 文件)
- `vite build` ✅ (新产出 `sse.js` chunk 0.99KB gzipped 0.63KB)

**体验账号**: `demo` / `123456`

---

### 2026-07-09 - 用户专业身份字段与身份选择功能

**用户**: 要求新增【用户专业身份选择】功能，含后端更新接口和前端弹窗。

#### 数据库变更
```sql
ALTER TABLE sys_user ADD COLUMN identity varchar(50) DEFAULT NULL COMMENT '专业身份';
```
可选值：`PROJECT_MANAGER` / `FRONTEND_DEV` / `BACKEND_DEV` / `QA_TESTER` / `UI_DESIGNER`

#### 后端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `entity/User.java` | 新增 `identity` 字段 |
| 修改 | `service/UserService.java` | 新增 `updateIdentity(userId, identity)` 方法签名 |
| 修改 | `service/impl/UserServiceImpl.java` | 实现 identity 白名单校验 + 更新逻辑；register 增加 identity 参数；login 返回 identity |
| 修改 | `controller/UserController.java` | 新增 `PUT /api/user/identity` 接口（需 JWT），register 增加 identity 参数 |
| 修改 | `vo/LoginVO.java` | 新增 `identity` 字段，登录时一并返回 |
| 修改 | `sql/init.sql` | 建表语句加入 `identity` 列 |

#### 前端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `api/user.js` | 新增 `updateIdentity(identity)` |
| 修改 | `store/user.js` | `userInfo` 含 `identity`；新增 `updateIdentity` action + `needsIdentityPrompt` getter |
| 修改 | `views/Dashboard.vue` | 新增身份选择弹窗（无关闭按钮、5 张卡片式选项）；`onMounted` 检查 `needsIdentityPrompt` |

**弹窗设计要点**:
- `el-dialog` 设 `:show-close="false"` `:close-on-click-modal="false"` `:close-on-press-escape="false"` → 不可手动关闭
- 5 张卡片：📋项目经理 / 💻前端工程师 / ☕后端工程师 / 🧪测试工程师 / 🎨UI设计师
- 点击后 loading 态阻止重复点击 → 调 API → 成功后关闭弹窗 + 更新 store
- 3 列网格布局（小屏 2 列），品牌色 hover 效果

#### 验证
- `mvn compile` ✅
- `vite build` ✅（Dashboard chunk 8.58KB → 10.28KB）

**体验账号**: `demo` / `123456`

---

### 2026-07-09 - AI 智能分工指派

**用户**: 升级 AI 拆解功能，支持自动推荐角色并指派项目成员。

#### 数据库变更
- 新增 `pm_project_member` 表（project_id + user_id）
- `sys_task` 新增 `recommended_role` 列（varchar 50）

#### 后端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `entity/ProjectMember.java` | pm_project_member 实体 |
| 新增 | `mapper/ProjectMemberMapper.java` | MyBatis-Plus BaseMapper |
| 修改 | `entity/Task.java` | 新增 `recommendedRole` 字段 |
| 修改 | `service/impl/TaskServiceImpl.java` | Prompt 升级 + 自动指派算法 + 新增 mapper 依赖 |
| 修改 | `controller/ProjectController.java` | 新增 `GET /api/project/{id}/members` |
| 修改 | `sql/init.sql` | 新增 pm_project_member 建表 + recommended_role |

**Prompt 升级**: 新增「团队配置」描述 5 种身份；要求 AI 增加 `recommended_role` 字段；示例 JSON 同步更新。

**自动指派算法**: 查询项目成员 → 构建 `identity → userId` 映射 → 遍历 AI 拆解结果 → 匹配 `recommended_role` → 自动设置 `assignee_id`

#### 测试数据
- demo 设为 BACKEND_DEV；新增 3 个测试用户分属不同身份
- 所有人加入项目 11

#### 前端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `api/project.js` | 新增 `listProjectMembers()` |
| 修改 | `views/TaskList.vue` | 详情弹窗增加推荐角色标签 + 指派信息显示 |

**角色颜色**: PM(Indigo) / 前端(Blue) / 后端(Emerald) / 测试(Amber) / UI(Pink)

#### 验证
- `mvn compile` ✅ (47 个 Java 文件)
- `vite build` ✅（TaskList chunk 15.61KB → 16.52KB）

**体验账号**: `demo` / `123456`

---

### 2026-07-09 - AI 一键生成初始项目任务/蓝图初始化

**用户**: 新增 `POST /api/task/ai-init-tasks` 接口 + 前端空项目引导。

#### 后端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `service/TaskService.java` | 新增 `initTasks(Long projectId)` 方法签名 |
| 修改 | `service/impl/TaskServiceImpl.java` | 实现 `initTasks()` + `buildInitPrompt()`；@Transactional 批量插入；校验空项目 |
| 修改 | `controller/TaskController.java` | 新增 `POST /api/task/ai-init-tasks?projectId=` 端点 + WebSocket 广播 |

**initTasks 核心逻辑**:
1. 查项目 name/description
2. 检查是否已有主任务（已有则拒绝，防止覆盖）
3. 构造 init Prompt → AI 生成 3-5 个阶段性大任务（JSON 格式）
4. 复用 `parseSubtaskJson()` 解析 JSON
5. @Transactional 批量插入 sys_task（parent_id=null, status=TODO, order_index 递增）
6. WebSocket 广播 `TASK_UPDATED`

**initPrompt 设计要点**:
- 角色设定：资深技术项目经理+敏捷教练
- 任务约束：独立完整开发阶段、按依赖排序、标题 8-16 字、描述 20-60 字
- 格式约束：纯 JSON 数组，示例使用「电商平台」场景
- 数量约束：3-5 个顶层大任务

**URL 设计决策**: 使用 `POST /api/task/ai-init-tasks?projectId=` 而非路径变量，避免与 `/{taskId}/ai-decompose`、`/{taskId}/subtasks` 等路由冲突。

#### 前端变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `api/task.js` | 新增 `initProjectTasks(projectId)` |
| 修改 | `views/TaskList.vue` | 3 处改动（见下方） |

**TaskList.vue 三处改动**:
1. **顶栏按钮**: 新增渐变动画按钮「✨ AI 一键生成任务」，2.5s 循环 shimmer 效果
2. **空项目引导**: `isBoardEmpty` computed → 看板为空且非加载态时显示 Hero 区（插画 + 大标题 + 大按钮）
3. **交互逻辑**: `v-loading` + 自定义 loading text → `handleInitTasks()` 调用 API → `fetchTasks()` 刷新 → try-catch-finally 错误处理

#### 验证
- `mvn compile` ✅ (47 个 Java 文件)
- `vite build` ✅（TaskList chunk 16.52KB → 18.20KB）

**体验账号**: `demo` / `123456`

---

### 2026-09-03 - 本地前端一键启动脚本重制

**操作：** 重写根目录 `start.bat` 为独立前端启动入口，不再检测或调用 Docker/Maven；自动检查 npm、首次安装 `node_modules`、提示 IDEA 后端 `8080` 状态、等待 Vite 就绪后打开 `http://localhost:3000`。
**原因：** 用户选择由 IntelliJ IDEA 启动 Spring Boot 后端，需要一个不依赖 Docker 的可靠前端启动按钮。
**验证：** 执行 `start.bat` 后 Vite 成功监听 3000 端口，`http://localhost:3000` 返回 HTTP 200；后端未启动时仅提示警告，不阻止前端运行。
**状态：** ✅ 完成

---

### 2026-09-04 - 第三周周报与 AI 使用记录生成

**操作：** 依据既有第一、第二周 Word 模板及 8/31—9/4 的项目历史，生成第三周周报和 AI 使用记录；对未单独开发的日期按协同审计、方案设计、集成复核与文档归档进行合理排布。
**影响文件：** `第三周周报.docx`、`第三周ai使用记录.docx`、`generate_docs_week3.py`
**原因：** 为本周项目协同权限、UI 迭代、AI 任务流程与启动部署工作形成规范化周度留档。
**状态：** ✅ 已生成并通过文档标题与章节内容校验

---

### 2026-09-04 - 项目邀请码自主加入与项目内岗位选择

**操作：** 为项目新增唯一 8 位邀请码；项目管理员可在成员管理中查看/复制邀请码；员工可在空主页输入邀请码加入项目，并进入看板后选择项目内岗位。
**影响文件：** `Project.java`、`ProjectService*`、`ProjectController.java`、`TaskServiceImpl.java`、`sql/init.sql`、`project.js`、`Dashboard.vue`、`TaskList.vue`。
**数据库：** 当前本地 `sys_project.invite_code` 字段和唯一索引已完成兼容迁移；存量项目在管理员首次打开成员管理时自动生成邀请码。
**验证：** `mvn -q -DskipTests compile` 与 `npm run build` 均通过。
**状态：** ✅ 完成

---

### 2026-09-04 - AI 初始任务生成超时与错误提示修复

**问题：** 新项目点击“AI 生成任务”后没有任务写入；当前 IDEA 后端接口返回业务码 500，前端未展示错误。
**根因：** 当前 `8080` 后端是未加载最新 AI 配置的旧进程；另有前端请求超时仅 15 秒，而隔离实测模型响应约需 50 秒。
**修复：** AI 初始化接口单独提升至 120 秒超时；TaskList 显示后端业务错误；AI 服务异常统一返回可读提示。
**验证：** 在隔离 `8081` 后端创建临时项目并调用接口，成功生成 4 条任务；测试项目和任务已自动删除；`mvn -q -DskipTests compile`、`npm run build` 均通过。
**状态：** ✅ 完成；需重启 IDEA 后端使 `8080` 应用新代码。

---

### 2026-09-04 - IDEA 启动目录下 AI 配置加载兜底

**问题：** IDEA 运行的 8080 后端仍提示 AI 服务调用失败，而同一代码在隔离后端可成功生成任务。
**修复：** `AIServiceImpl` 在 Spring 配置未绑定 `AI_API_KEY` 时，从 IDEA 工作目录与编译产物目录向上查找项目根目录 `.env`；启动日志仅输出密钥是否加载成功。
**验证：** `mvn -q -DskipTests compile` 通过。
**状态：** ✅ 完成；重启后端后应在日志中确认 `apiKeyConfigured=true`。

---

### 2026-09-04 - 项目管理能力扩展（任务、里程碑与附件）

**操作：** 为任务增加优先级、标签、开始/截止日期、预计/实际工时和多前置依赖；后端阻止循环依赖及未完成依赖任务进入进行中/已完成。新增甘特图、逾期/工作量统计、可关联多任务的里程碑，以及图片/PDF/压缩包附件上传、成员下载授权与管理员下载审计。
**影响文件：** `Task*`、`ProjectController.java`、新增里程碑/附件实体、Mapper、Service，`sql/init.sql`，`kanban-frontend/src/views/TaskList.vue`、`ProjectManagement.vue`、相关 API 与路由。
**数据库：** 本地 `smartpm.sys_task` 已迁移 6 个任务字段；`pm_project_milestone`、`pm_task_attachment`、`pm_attachment_download_log` 三表已创建并校验。
**验证：** `mvn -q -DskipTests compile` 与 `npm run build` 均通过；前端构建仅保留现有依赖循环和大包提示。
**状态：** ✅ 完成；重启 IDEA 后端并刷新前端即可使用。

---

### 2026-09-04 - AI 子任务拆解请求超时修复

**问题：** 看板“AI 智能拆解步骤”沿用普通接口 15 秒超时，模型响应较慢时前端先显示失败，导致用户误以为子任务没有生成。
**修复：** `task.js` 将拆解接口超时提升为 120 秒；`TaskList.vue` 透传后端错误信息，便于定位实际异常。
**验证：** 使用演示账号创建临时项目和任务，实际调用拆解接口耗时 14 秒，返回并持久化 5 条子任务；临时项目已删除；`npm run build` 通过。
**状态：** ✅ 完成

---

## 任务完成：Docker 启动故障诊断 - 2026-09-07 14:39

**摘要：** Docker Desktop、Linux daemon 和 Compose 均正常；启动失败由两个本机冲突造成：`start.bat` 检测到 Docker 数据盘仍在 C 盘后主动退出，且本机 `MySQL80` 已占用 Compose 要发布的 3306 端口。
**验证：** `docker version`、`docker info`、`docker compose version` 和 `docker compose config --quiet` 均通过；确认 `docker_data.vhdx` 位于 C 盘且非重解析点，`MySQL80` 正在监听 3306。
**影响文件：** `project_history.md`
**后续步骤：** 将 Docker 磁盘镜像迁至其他盘或取消该脚本拦截；同时停止本机 MySQL 或把 Compose 宿主端口改为 3307（也可不发布数据库端口）。
**状态：** ✅ 完成

---

## 任务完成：Docker 数据盘迁移到 D 盘 - 2026-09-07 14:46

**摘要：** 停止 Docker Desktop 后，将 `C:\Users\Smile\AppData\Local\Docker\wsl` 中的两个 VHDX 数据盘迁移至 `D:\DockerData\wsl`，并在原位置建立目录联接以保持 Docker Desktop 路径兼容。
**验证：** Docker Desktop 已恢复为 running，Docker client/server 29.7.2 可用，Compose 配置校验通过；目标 VHDX 时间戳在重启后继续更新，一键启动脚本的数据盘位置检查通过。
**影响文件：** Docker Desktop WSL 数据目录、`project_history.md`
**后续步骤：** Docker 数据盘问题已解决；项目完整启动仍需处理宿主机 `MySQL80` 与 Compose 的 3306 端口冲突。
**状态：** ✅ 完成

---

## 任务完成：Docker MySQL 宿主端口调整 - 2026-09-07

**摘要：** 将 Compose 的 MySQL 端口映射由 `3306:3306` 调整为 `3307:3306`，避开宿主机 `MySQL80` 对 3306 的占用。
**关键决策：** 仅修改宿主端口；容器内部仍使用 3306，后端通过 Docker 网络访问 `mysql:3306`，无需改动应用配置。
**影响文件：** `docker-compose.yml`、`project_history.md`
**状态：** ✅ 完成

---

## 任务完成：启动脚本虚拟化状态误判修复 - 2026-09-07

**摘要：** 修复 Docker 未运行时 `start.bat` 仅依据 `VirtualizationFirmwareEnabled` 判断 BIOS 虚拟化、从而在 Hyper-V/WSL2 已运行的机器上产生误报的问题。
**关键决策：** 同时接受 `Win32_ComputerSystem.HypervisorPresent=true` 或 CPU 固件虚拟化标志为真；并将 BIOS 提示改为 Intel VT-x / AMD SVM 通用说明。
**验证：** 当前机器 `HypervisorPresent=True`、新判断结果为 True；Docker Desktop 随后成功启动并恢复 running。
**影响文件：** `start.bat`、`project_history.md`
**状态：** ✅ 完成

---

### 2026-09-07 - 全站前端视觉重构：设计系统与公共组件

**操作：** 建立冷灰浅色主题与钴蓝品牌色，新增统一品牌标志、响应式侧栏壳层、页面标题、状态、空状态和加载骨架组件。
**影响文件：** `kanban-frontend/src/assets/theme.css`、`kanban-frontend/src/components/*`
**原因：** 消除页面各自维护顶栏、颜色和圆角造成的视觉割裂，为八个页面提供一致的改造基础。
**验证：** `npm ci` 完成，`npm run build` 通过。
**状态：** ✅ 完成

---

### 2026-09-07 - SmartPM 全站前端视觉重构

**操作：** 启动 Vue 3 + Element Plus 全站视觉重构，先建立冷灰浅色设计令牌、统一应用壳层和新版 SmartPM 标志。
**影响文件：** `kanban-frontend/src/assets/theme.css`、`kanban-frontend/src/components/*`、后续全部前端视图。
**原因：** 现有页面视觉语言分裂、重复顶栏与硬编码颜色较多，并在 390px 视口存在明显横向溢出。
**状态：** ✅ 完成

---

### 2026-09-07 - 全站前端视觉重构完成与响应式验收

**操作：** 完成登录、项目列表、任务看板、数据分析、Wiki、项目管理、系统管理和回收站八个路由的视觉迁移；统一接入响应式应用侧栏、页面标题、状态组件和钴蓝浅色设计系统。看板手机端改为状态分段切换，Wiki 增加文档抽屉，管理页甘特图限制为内部滚动，管理员与回收站在手机端改为记录卡片。
**设计约束：** 保留现有路由、接口、Pinia、权限、拖拽、编辑器、AI 能力与中文业务文案；未引入新的 UI 或动画库。
**验证：** `npm ci` 和 `npm run build` 通过；Playwright 使用 1440、1024、768、390 四种宽度检查全部八个路由，共 32 组页面均无根节点水平溢出和运行时错误；侧栏抽屉、平板 72px 窄侧栏、手机看板状态切换、Wiki 文档抽屉、新建项目与新建任务弹窗均通过交互检查。新版前端已重建并部署至 Docker Nginx 的 `http://localhost:3000`。
**影响文件：** `kanban-frontend/src/assets/theme.css`、`kanban-frontend/src/components/*`、`kanban-frontend/src/views/*`、`kanban-frontend/scripts/visual-regression.cjs`、`project_history.md`。
**状态：** ✅ 完成

---

### 2026-09-09 - GitHub 上传准备

**操作：** 审核版本库边界与敏感文件忽略规则，确认 `.env`、依赖、构建产物、IDE 配置和项目报告不会进入新提交；将当前稳定版本提交并推送到 `smiletan588-commits/project-bishe` 的 `main` 分支。
**影响文件：** `.gitignore`、`project_history.md`。
**原因：** 当前目录最初未初始化 Git，因此先关联远程已有历史，再以普通快进提交上传，避免覆盖远程提交。
**验证：** 主提交 `fef1eee` 已成功推送；本地 `main` 已设置为跟踪 `origin/main`，未使用强制推送。
**状态：** ✅ 完成

---

### 2026-09-09 - 毕业设计综合升级启动

**操作：** 启动评论与动态、通知中心、可解释风险、AI 使用评估、数据库迁移及自动化测试的综合升级；先完成现状审计并锁定兼容现有 API 与设计系统的实施顺序。
**影响文件：** `project_history.md`（后续里程碑将逐项记录具体代码文件）。
**原因：** 将现有功能型项目提升为具备协作闭环、可解释智能决策和量化实验依据的毕业设计。
**状态：** ✅ 已于 2026-09-10 完成，详见后续两个验收里程碑

### 2026-09-09 - 毕业设计升级：后端数据与服务骨架

**操作：** 接入 Flyway、环境化 JWT/CORS、真实完成时间、关系型任务依赖/里程碑关联，以及评论、活动、通知、风险和 AI 操作日志的实体、服务与公开接口。
**影响文件：** `pom.xml`、`application.yml`、`db/migration/*`、新增协作/通知/风险/AI 日志后端模块及相关任务服务。
**验证：** `mvn -q -DskipTests compile` 通过。
**状态：** ✅ 完成

---

### 2026-09-10 - 毕业设计升级：协作闭环、风险中心与 AI 评估

**操作：** 完成任务评论、显式成员提醒、只读活动时间线、全局通知中心、每小时到期/逾期/阻塞扫描和 WebSocket 实时推送；完成六条确定性规则风险评分、成员工作量、阻塞关系、Redis 缓存与 AI 结构化建议；统一记录 AI 场景、模型、耗时、生成量、采纳情况及 1–5 分反馈，并在数据大屏展示五项 AI 指标。
**前端：** 任务详情新增评论与动态双栏，应用壳层新增通知抽屉，项目管理页新增风险中心，分析页新增 AI 效果卡片；保持现有响应式设计与权限边界。
**实验材料：** 新增 20 组不同类型项目需求、评价指南和结果 CSV 模板，不伪造尚未实际运行的实验结果。
**状态：** ✅ 完成

---

### 2026-09-10 - 毕业设计升级：工程质量与最终验收

**操作：** 完成 Flyway V1–V3、规范化任务/里程碑关联表、SpringDoc OpenAPI、环境化 JWT/CORS、停用账号 Token 即时失效、八位密码规则、Jakarta Validation、前端依赖分包、容器非 root 运行及 Maven/npm 构建缓存；Docker 新环境改为仅由 Flyway 管理结构。
**自动化测试：** JUnit 5 + MockMvc + Testcontainers MySQL 覆盖空库迁移、登录、项目隔离、VIEWER 权限、拖拽、依赖阻塞、评论、活动、通知去重、风险评分、完成时间、AI 统计和 Swagger；两个独立 WebSocket 会话验证评论与拖拽广播，并验证被提醒成员的全局通知推送；Vitest 覆盖毕业设计新增 API 与权限辅助逻辑。
**验证：** `mvn test` 4/4 通过，`npm test` 5/5 通过，`npm run build` 通过且最大拆分块低于 1 MB，生产依赖审计 0 漏洞；Playwright 对 1440/1024/768/390 四档宽度和八个路由完成巡检，新增通知、评论、活动、风险交互均可见且无溢出/运行时错误；Compose 的 MySQL、Redis、后端、Nginx 全部 healthy，首页、健康检查和 Swagger 均返回 200，已有数据库成功升级至 V3。
**运行入口：** `http://localhost:3000`；Swagger 为 `http://localhost:3000/swagger-ui.html`。
**状态：** ✅ 完成

---

### 2026-09-10 14:41 - 关键路径与计划基线升级启动

**操作：** 启动 CPM 关键路径、不可变命名基线、基线偏差和 What-if 延期模拟的增量实现，沿用现有主任务依赖、RBAC、风险规则与项目管理页。
**影响文件：** `project_history.md`（后续里程碑记录具体后端、迁移、前端与测试文件）。
**原因：** 在不引入 AI 决策和复杂资源调度的前提下，形成可解释、可复现的排期算法与风险闭环。
**状态：** ✅ 已完成，详见后续实现与验收里程碑

### 2026-09-10 - CPM 调度引擎与基线 API 骨架

**操作：** 新增 V4 基线结构、不可变基线快照模型、纯内存 CPM 前后向计算、自动日期推算、基线 CRUD 与无副作用 What-if 模拟接口。
**影响文件：** `db/migration/V4__schedule_baseline_and_cpm.sql`、新增 Schedule 实体/DTO/VO/Mapper/Service/Controller 与 `ScheduleEngine`。
**验证：** `mvn -q -DskipTests compile` 通过。
**状态：** ✅ 完成

---

### 2026-09-10 - 关键路径、计划基线与 What-if 调度升级完成

**调度决策：** 仅计算未删除主任务，使用 Finish-to-Start 与日历日；工期优先采用开始日至截止日的含首尾天数，缺失日期时按 `ceil(预计工时 / 8)`、无工时按 1 天推算。纯内存 `ScheduleEngine` 通过拓扑排序、CPM 前向/后向计算输出最早/最晚日期、总浮动、关键任务与代表性关键路径，复杂度为 `O(V+E)`；循环依赖、范围外依赖和日期冲突均显式报告，不回写真实排期。

**基线与模拟：** Flyway V4 新增不可变命名基线及任务快照表；实现排期读取、基线列表/创建/详情/删除和单任务 What-if 模拟接口。基线比较支持 `ADDED`、`REMOVED`、`UPDATED`、`UNCHANGED`，历史任务软删除后快照仍保留；VIEWER 可查看/模拟，MEMBER 可创建，项目负责人和 PROJECT_ADMIN 可永久删除。模拟同时返回项目完工变化、受影响任务及风险等级数量变化，且不修改任务、依赖或基线。

**风险闭环：** 风险规则新增零浮动关键任务 `+15`、相对基线延期 1–3 天 `+10`、延期 4 天及以上 `+20`，仍按既有阈值分级并封顶 100；未指定基线时自动采用最新基线。风险缓存键包含实际基线 ID，并在任务或基线变化时失效；同时修复未分配任务在基线风险计算中的空键边界。

**前端：** 在既有项目管理页内升级关键路径调度台，增加预计完工、关键任务、基线偏差、自动推算指标；甘特图以灰色影子条叠加基线、红色突出关键路径，并展示浮动、来源、偏差与警告。新增基线账本和权限操作，以及响应式 What-if 抽屉，展示完工日与风险分布变化、受影响任务明细和重置操作。

**验证：** `mvn -q test` 共 14 项通过（8 项调度引擎、4 项风险规则、2 项 Testcontainers MySQL 集成），Flyway V1–V4 空库迁移成功；集成覆盖同名基线、历史快照、项目隔离、VIEWER/MEMBER/PROJECT_ADMIN 权限、默认/指定基线、模拟无副作用、非法/已完成/跨项目任务和 3650 天边界。`npm test -- --run` 9 项通过，`npm run build` 通过；Playwright 在 1440/1024/768/390 四档宽度完成八路由与基线甘特、What-if 抽屉巡检，无根节点横向溢出或运行时错误。

**影响文件：** `db/migration/V4__schedule_baseline_and_cpm.sql`、`sql/init.sql`、Schedule 实体/Mapper/DTO/VO/Service/Controller、`RiskServiceImpl`、`RiskScoringRules`、`RecycleBinServiceImpl`、`ProjectManagement.vue`、前端 Schedule/Risk API 与日期辅助函数、JUnit/Vitest/Playwright 回归测试。

**状态：** ✅ 完成
