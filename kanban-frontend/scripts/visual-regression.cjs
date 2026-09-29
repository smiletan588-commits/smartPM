const { chromium } = require('playwright')
const path = require('path')
const fs = require('fs')

const baseUrl = process.env.SMARTPM_PREVIEW_URL || 'http://127.0.0.1:4173'
const outputDir = process.env.SMARTPM_SCREENSHOT_DIR || path.join(process.cwd(), 'visual-output')
fs.mkdirSync(outputDir, { recursive: true })
const routes = ['/login', '/dashboard', '/project/1', '/project/1/product-lab', '/analytics', '/project/1/wiki?projectName=毕业设计协作平台', '/project/1/manage', '/project/1/manage?section=risk', '/project/1/manage?section=schedule', '/project/1/manage?section=milestones', '/project/1/manage?section=team', '/admin/users', '/recycle-bin']
const widths = [1440, 1024, 768, 390]

const projects = [{ id: 1, name: '毕业设计协作平台', description: '面向软件项目团队的任务、文档与进度协作平台', createTime: '2026-09-01T09:30:00', ownerId: 1, memberCount: 6 }]
const tasks = [
  { id: 11, projectId: 1, title: '完成移动端任务看板适配', description: '保证窄屏下筛选和任务操作可用', status: 'TODO', priority: 'HIGH', assigneeId: 2, assigneeName: '林悦', dueDate: '2026-09-12', startDate: '2026-09-07', estimatedHours: 8, actualHours: 0 },
  { id: 12, projectId: 1, title: '联调项目成员权限接口', description: '覆盖管理员和普通成员路径', status: 'IN_PROGRESS', priority: 'MEDIUM', assigneeId: 3, assigneeName: '陈川', dueDate: '2026-09-10', startDate: '2026-09-05', estimatedHours: 12, actualHours: 7 },
  { id: 13, projectId: 1, title: '整理毕业答辩演示数据', description: '准备项目列表、进度分析与里程碑示例', status: 'DONE', priority: 'LOW', assigneeId: 1, assigneeName: '张明', dueDate: '2026-09-06', startDate: '2026-09-01', estimatedHours: 5, actualHours: 5 },
  { id: 14, projectId: 1, title: '实现一个用于验证超长任务标题在任务卡片和甘特图中是否能够正确截断而不会撑破页面布局的测试任务', description: '', status: 'TODO', priority: 'MEDIUM', assigneeName: '林悦', dueDate: '2026-09-18', startDate: '2026-09-08', estimatedHours: 10, actualHours: 0 }
]
const planningDraft = {
  id: 501, projectId: 1, mode: 'DECOMPOSE', parentTaskId: 11, version: 1, status: 'DRAFT', updatedAt: '2026-09-16T10:00:00',
  input: { mode: 'DECOMPOSE', parentTaskId: 11, projectType: 'SOFTWARE', goal: tasks[0].description, deliverable: '可用的移动端看板', scope: tasks[0].title, wikiIds: [] },
  content: { overview: '只拆分独立、可验收的适配工作', assumptions: ['需确认最低支持的手机宽度'], tasks: [
    { title: '适配移动端看板布局', description: '调整三列看板在窄屏下的布局', deliverable: '可操作的移动端看板', acceptanceCriteria: '390px 宽度下无横向溢出', fitReason: '直接满足父任务的窄屏操作目标', recommendedRole: 'FRONTEND_DEV', recommendedSkill: null, priority: 'MEDIUM', tags: 'DEVELOPMENT', startDate: '2026-09-07', dueDate: '2026-09-10', dependencyIndexes: [] },
    { title: '检查移动端筛选与拖拽', description: '验证筛选和任务状态操作', deliverable: '移动端验收记录', acceptanceCriteria: '关键操作均能通过触控完成', fitReason: '确认父任务交付可用', recommendedRole: 'QA_TESTER', recommendedSkill: null, priority: 'MEDIUM', tags: 'TESTING', startDate: '2026-09-11', dueDate: '2026-09-12', dependencyIndexes: [0] }
  ] }, issues: []
}
const members = [{ userId: 1, username: 'admin', nickname: '张明', identity: 'PROJECT_MANAGER', permission: 'ADMIN' }, { userId: 2, username: 'linyue', nickname: '林悦', identity: 'FRONTEND_DEV', permission: 'MEMBER' }, { userId: 3, username: 'chenchuan', nickname: '陈川', identity: 'BACKEND_DEV', permission: 'MEMBER' }]
const riskOverview = {
  highCount: 1, mediumCount: 1, lowCount: 1,
  risks: [
    { taskId: 12, title: '联调项目成员权限接口', level: 'HIGH', score: 60, factors: ['三天内到期 +20', '被前置任务阻塞 +30', '未指定负责人 +10'], suggestion: '先完成前置接口并明确负责人。', actionStatus: 'IN_PROGRESS', riskOwnerId: 1, riskOwnerName: '张明', responsePlan: '先完成接口联调', actionDueDate: '2026-09-12' },
    { taskId: 11, title: '完成移动端任务看板适配', level: 'MEDIUM', score: 35, factors: ['三天内到期 +20', '超过七天没有更新 +15'], suggestion: '拆分剩余工作并每日更新进度。' }
  ],
  workloads: [{ userId: 2, userName: '林悦', pendingHours: 44 }],
  blockedEdges: [{ taskId: 12, taskTitle: '联调项目成员权限接口', prerequisiteTaskId: 11, prerequisiteTitle: '完成移动端任务看板适配' }]
}
const baselines = [{ id: 71, name: '需求确认版', description: '评审后的初始交付计划', projectStartDate: '2026-09-01', projectFinishDate: '2026-09-18', taskCount: 4, criticalTaskCount: 2, createdAt: '2026-09-02T10:00:00' }]
const schedule = {
  projectStartDate: '2026-09-01', projectFinishDate: '2026-09-20', durationDays: 20, criticalTaskCount: 2, inferredTaskCount: 1,
  baselineId: 71, baselineName: '需求确认版', finishVarianceDays: 2, criticalPathTaskIds: [11, 12],
  warnings: ['任务“整理毕业答辩演示数据”缺少完整日期，已根据预计工时推算。'],
  tasks: [
    { taskId: 11, title: tasks[0].title, status: 'TODO', plannedStartDate: '2026-09-07', plannedFinishDate: '2026-09-12', baselineStartDate: '2026-09-05', baselineFinishDate: '2026-09-10', latestStartDate: '2026-09-07', latestFinishDate: '2026-09-12', durationDays: 6, totalSlackDays: 0, critical: true, source: 'EXPLICIT', finishVarianceDays: 2, changeType: 'CHANGED' },
    { taskId: 12, title: tasks[1].title, status: 'IN_PROGRESS', plannedStartDate: '2026-09-13', plannedFinishDate: '2026-09-20', baselineStartDate: '2026-09-11', baselineFinishDate: '2026-09-18', latestStartDate: '2026-09-13', latestFinishDate: '2026-09-20', durationDays: 8, totalSlackDays: 0, critical: true, source: 'EXPLICIT', finishVarianceDays: 2, changeType: 'CHANGED' },
    { taskId: 13, title: tasks[2].title, status: 'DONE', plannedStartDate: '2026-09-01', plannedFinishDate: '2026-09-01', baselineStartDate: '2026-09-01', baselineFinishDate: '2026-09-01', latestStartDate: '2026-09-19', latestFinishDate: '2026-09-19', durationDays: 1, totalSlackDays: 18, critical: false, source: 'INFERRED', finishVarianceDays: 0, changeType: 'UNCHANGED' }
  ]
}
const simulatedSchedule = {
  current: { projectFinishDate: '2026-09-20', criticalTaskCount: 2 },
  simulated: { projectFinishDate: '2026-09-24', criticalTaskCount: 2 },
  projectFinishDeltaDays: 4,
  riskBefore: { highCount: 1, mediumCount: 1, lowCount: 1 },
  riskAfter: { highCount: 2, mediumCount: 0, lowCount: 1 },
  changedTasks: [
    { taskId: 11, title: tasks[0].title, currentStartDate: '2026-09-07', currentFinishDate: '2026-09-12', simulatedStartDate: '2026-09-07', simulatedFinishDate: '2026-09-16', finishDeltaDays: 4, riskBefore: 'MEDIUM', riskAfter: 'HIGH', wasCritical: true, critical: true },
    { taskId: 12, title: tasks[1].title, currentStartDate: '2026-09-13', currentFinishDate: '2026-09-20', simulatedStartDate: '2026-09-17', simulatedFinishDate: '2026-09-24', finishDeltaDays: 4, riskBefore: 'HIGH', riskAfter: 'HIGH', wasCritical: true, critical: true }
  ]
}
const notifications = [{ id: 31, type: 'COMMENT_MENTIONED', title: '评论中提到了你', content: '请验证风险中心', projectId: 1, taskId: 12, isRead: false, createdAt: '2026-09-10T09:20:00' }]
const comments = [{ id: 41, taskId: 11, userId: 1, authorName: '张明', content: '移动端请重点验证评论区布局。', mentionedUserIds: [2], createdAt: '2026-09-10T09:10:00' }]
const activities = [{ id: 51, taskId: 11, actorId: 1, actorName: '张明', actionType: 'STATUS_CHANGED', summary: '将任务从待办移动到进行中', createdAt: '2026-09-10T09:12:00' }]

function dataFor(url) {
  const pathname = new URL(url).pathname.replace(/^\/api/, '')
  if (pathname === '/project/list') return projects
  if (pathname === '/workspace/overview') return { myCount: 3, todayCount: 1, overdueCount: 1, weekCount: 3, blockedCount: 1, mentionedCount: 1, unreadCount: 1, tasks: tasks.slice(0, 3).map(task => ({ ...task, projectName: projects[0].name, blocked: task.id === 12 })), projects }
  if (pathname === '/workspace/role-view') return { identity: 'PROJECT_MANAGER', title: '项目决策工作台', subtitle: '聚焦延期、风险、容量和待决策事项', primaryAction: 'OPEN_MY_TASKS', sections: [{ key: 'overdue', label: '逾期任务', count: 1, route: '/dashboard?scope=overdue' }, { key: 'unassigned', label: '未分配任务', count: 2, route: '/dashboard?roleScope=unassigned' }, { key: 'milestones', label: '延期里程碑', count: 1, route: '/dashboard?roleScope=milestones' }] }
  if (pathname === '/workspace/tasks') return { items: tasks.slice(0, 3).map(task => ({ ...task, projectName: projects[0].name })), total: 3, page: 1, size: 20 }
  if (pathname === '/search') return { projects: [], tasks: [], wikis: [] }
  if (pathname === '/project/templates') return []
  if (pathname === '/user/notification-preferences') return { email: '', emailEnabled: false, assignmentEnabled: true, mentionEnabled: true, deadlineEnabled: true, riskEnabled: true }
  if (pathname.endsWith('/members')) return members
  if (pathname === '/task/list/1') return tasks
  if (pathname === '/project/1/planning/drafts') return [planningDraft]
  if (pathname === '/project/1/planning/drafts/501') return planningDraft
  if (pathname.includes('/subtasks') || pathname.includes('/attachments')) return []
  if (/^\/task\/\d+\/acceptance$/.test(pathname)) return { taskId: 11, reviewRequired: true, status: 'PENDING', checklist: [{ id: 1, content: '移动端流程通过', checked: true }], reviews: [] }
  if (/^\/task\/\d+\/time-entries$/.test(pathname)) return [{ id: 1, userName: '林悦', workDate: '2026-09-10', hours: 3.5, note: '完成适配' }]
  if (pathname.endsWith('/comments')) return comments
  if (pathname.endsWith('/activities')) return activities
  if (pathname === '/notifications/unread-count') return { count: 1 }
  if (pathname === '/notifications') return notifications
  if (pathname === '/project/1/risks') return riskOverview
  if (pathname === '/project/1/schedule/baselines') return baselines
  if (pathname === '/project/1/schedule/simulate') return simulatedSchedule
  if (pathname === '/project/1/schedule') return schedule
  if (pathname === '/project/1/capacity') return [{ userId: 1, nickname: '张明', weeklyHours: 40, availableHours: 40, remainingHours: 24, utilizationPercent: 60, overloaded: false }, { userId: 2, nickname: '林悦', weeklyHours: 40, availableHours: 32, remainingHours: 44, utilizationPercent: 137.5, overloaded: true }]
  if (pathname === '/project/1/executive-summary') return { projectName: projects[0].name, health: { level: 'AMBER', reasons: ['1 名成员负载超过 100%'] }, schedule: { finishDate: '2026-09-20', finishVarianceDays: 2, criticalTaskCount: 2 }, risks: { high: 1, medium: 1, low: 1 }, overdueMilestoneCount: 1, overloadedMemberCount: 1, unassignedTaskCount: 2, blockedTaskCount: 1, capacity: dataFor('http://local/api/project/1/capacity'), openDecisions: [{ id: 1, title: '确认移动端 MVP 范围' }], recentActivities: activities, milestones: [] }
  if (pathname === '/project/1/product-lab/conversations') return [{ id: 81, title: '移动端审批 MVP', stage: 'REQUIREMENT', mode: 'PRD_DRAFT', status: 'ACTIVE', messageCount: 2 }]
  if (pathname === '/project/1/product-lab/conversations/81') return { id: 81, title: '移动端审批 MVP', stage: 'REQUIREMENT', mode: 'PRD_DRAFT', messages: [{ id: 1, role: 'USER', content: '需要一套移动端审批流程', status: 'COMPLETE' }, { id: 2, role: 'ASSISTANT', content: '## 待确认假设\n\n审批链路需要支持驳回与重新提交。', status: 'COMPLETE' }], artifacts: [{ id: 91, conversationId: 81, type: 'PRD', title: '移动端审批 MVP PRD', content: '# 目标\n形成可验证的审批闭环', status: 'DRAFT', versionNo: 2, updatedAt: '2026-09-10T10:00:00' }] }
  if (pathname === '/project/1/product-lab/artifacts') return dataFor('http://local/api/project/1/product-lab/conversations/81').artifacts
  if (pathname === '/project/1/product-lab/artifacts/91') return { ...dataFor('http://local/api/project/1/product-lab/conversations/81').artifacts[0], versions: [{ id:2, versionNo:2, title:'移动端审批 MVP PRD', content:'# 目标\n形成可验证的审批闭环', status:'DRAFT', editorName:'张明', createdAt:'2026-09-10T10:00:00' }, { id:1, versionNo:1, title:'移动端审批草稿', content:'初版流程与范围', status:'DRAFT', editorName:'张明', createdAt:'2026-09-09T10:00:00' }] }
  if (pathname.endsWith('/milestones')) return [{ id: 1, name: '功能验收', description: '完成核心流程验收', targetDate: '2026-09-16', status: 'PLANNED', taskIds: '11,12,13' }]
  if (pathname === '/analytics/overview') return { totalProjects: 4, totalTasks: 27, completedTasks: 13, inProgressTasks: 8, onTimeCompletionRate: 84.6, averageDelayDays: 1.8, averageCycleHours: 42.5, completionSampleSize: 13, statusDistribution: [{ status: 'TODO', count: 6 }, { status: 'IN_PROGRESS', count: 8 }, { status: 'DONE', count: 13 }], projectTaskRanking: [{ projectId: 1, projectName: '毕业设计协作平台', taskCount: 14 }, { projectId: 2, projectName: '课程管理系统', taskCount: 8 }, { projectId: 3, projectName: '移动端原型', taskCount: 5 }], dailyCompletedTrend: ['09-01','09-02','09-03','09-04','09-05','09-06','09-07'].map((date, index) => ({ date: `2026-${date}`, count: [1, 3, 2, 4, 2, 5, 3][index] })) }
  if (pathname === '/analytics/ai-overview') return { totalCalls: 28, successRate: 92.9, averageDurationMs: 2380, adoptionRate: 71.4, averageRating: 4.3, aiGeneratedTaskRate: 26.8, durationSampleCount: 28, ratingSampleCount: 12, adoptionSampleCount: 14 }
  if (pathname === '/admin/users') return members.map((member, index) => ({ id: member.userId, username: member.username, nickname: member.nickname, identity: member.identity, systemRole: index === 0 ? 'ADMIN' : 'USER', status: 'ACTIVE', createdAt: '2026-08-20T10:00:00' }))
  if (pathname === '/admin/system-overview') return { users: 8, activeUsers: 8, projects: 3, tasks: 42, pendingEmails: 2, failedEmails: 1, loginFailures24h: 3, migrationVersion: '11', openOperations: [] }
  if (pathname === '/admin/audit-events') return [{ id: 1, actorName: '张明', category: 'PRODUCT_LAB', action: 'ARTIFACT_PUBLISHED', summary: '产品成果已发布到 Wiki', createdAt: '2026-09-10T10:00:00' }]
  if (pathname === '/admin/login-events') return [{ id: 1, username: 'admin', success: true, createdAt: '2026-09-15T08:30:00' }, { id: 2, username: 'unknown', success: false, reason: '用户名或密码错误', createdAt: '2026-09-15T08:20:00' }]
  if (pathname === '/recycle-bin') return [{ id: 7, type: 'TASK', title: '旧版登录页视觉调整', projectName: '毕业设计协作平台', deletedByName: '张明', deletedAt: '2026-09-06T15:20:00' }]
  if (pathname === '/wiki/list/1') return [{ id: 21, title: '需求说明与验收标准', updateTime: '2026-09-07T11:30:00' }, { id: 22, title: '后端接口联调记录', updateTime: '2026-09-06T17:20:00' }]
  if (pathname === '/wiki/21' || pathname === '/wiki/22') return { id: Number(pathname.slice(6)), title: '需求说明与验收标准', content: '# 项目目标\n\n建立清晰、稳定的团队协作工作流。' }
  return []
}

;(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' })
  const results = []
  for (const width of widths) {
    const context = await browser.newContext({ viewport: { width, height: width <= 390 ? 844 : 900 }, deviceScaleFactor: 1 })
    const page = await context.newPage()
    const pageErrors = []
    page.on('pageerror', error => pageErrors.push(error.message))
    await page.route('**/*', route => {
      const pathname = new URL(route.request().url()).pathname
      if (!pathname.startsWith('/api/')) return route.continue()
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data: dataFor(route.request().url()) }) })
    })
    await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' })
    for (const routePath of routes) {
      await page.evaluate(routePath => {
        if (routePath === '/login') localStorage.clear()
        else {
          localStorage.setItem('token', 'visual-check-token')
          localStorage.setItem('userInfo', JSON.stringify({ userId: 1, username: 'admin', identity: 'PROJECT_MANAGER', systemRole: 'ADMIN' }))
          localStorage.removeItem('smartpm.sidebar.collapsed')
        }
      }, routePath)
      await page.goto(`${baseUrl}${routePath}`, { waitUntil: 'networkidle' })
      await page.waitForTimeout(150)
      const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth, textLength: document.body.innerText.length, htmlLength: document.body.innerHTML.length }))
      results.push({ width, route: routePath, url: page.url(), overflow: Math.max(dimensions.document, dimensions.body) - dimensions.viewport, textLength: dimensions.textLength, htmlLength: dimensions.htmlLength, errors: [...pageErrors] })
      pageErrors.length = 0
      const screenshotRoutes = width === 1440 ? ['/login', '/dashboard', '/project/1/product-lab', '/analytics', '/project/1/wiki?projectName=毕业设计协作平台', '/project/1/manage'] : width === 390 ? ['/login', '/dashboard', '/project/1', '/project/1/product-lab', '/project/1/wiki?projectName=毕业设计协作平台', '/project/1/manage', '/admin/users', '/recycle-bin'] : routePath === '/project/1/manage' ? ['/project/1/manage'] : []
      if (screenshotRoutes.includes(routePath)) {
        const name = routePath.replaceAll('/', '_').replaceAll('?', '_').replace(/^_/, '') || 'home'
        await page.screenshot({ path: path.join(outputDir, `${name}-${width}.png`), fullPage: true })
      }
    }
    await page.goto(`${baseUrl}/project/1/product-lab`, { waitUntil: 'networkidle' })
    if (width <= 767) await page.locator('.mobile-lab-tabs button').filter({ hasText: '成果' }).click()
    results.push({ width, route: 'product-lab', visible: await page.getByText('成果与决策').isVisible() })
    await page.goto(`${baseUrl}/project/1/manage?section=schedule`, { waitUntil: 'networkidle' })
    await page.getByRole('button', { name: 'What-if 模拟' }).click()
    await page.getByRole('heading', { name: 'What-if 排期模拟' }).waitFor({ state: 'visible' })
    await page.getByRole('button', { name: '运行模拟' }).click()
    await page.getByText('项目完工变化').waitFor({ state: 'visible' })
    results.push({ width, route: 'schedule-simulation', visible: true })
    await page.screenshot({ path: path.join(outputDir, `schedule-simulation-${width}.png`), fullPage: true })
    await page.keyboard.press('Escape')
    await page.goto(`${baseUrl}/project/1`, { waitUntil: 'networkidle' })
    await page.locator('.task-card').first().click()
    await page.locator('.detail-dialog').waitFor({ state: 'visible' })
    await page.waitForTimeout(250)
    const detailLayout = await page.evaluate(() => {
      const dialog = document.querySelector('.detail-dialog')
      const title = document.querySelector('.detail-title')
      const dialogBox = dialog?.getBoundingClientRect()
      const titleBox = title?.getBoundingClientRect()
      return {
        dialogWidth: Math.round(dialogBox?.width || 0),
        titleWidth: Math.round(titleBox?.width || 0),
        titleHeight: Math.round(titleBox?.height || 0),
        horizontalOverflow: dialog ? Math.max(0, dialog.scrollWidth - dialog.clientWidth) : 0
      }
    })
    results.push({
      width,
      route: 'task-detail',
      visible: true,
      ...detailLayout,
      layoutBroken: detailLayout.horizontalOverflow > 0 || detailLayout.titleWidth < Math.min(220, width - 80)
    })
    await page.screenshot({ path: path.join(outputDir, `task-detail-${width}.png`), fullPage: true })
    await page.getByRole('button', { name: 'AI 辅助拆解' }).click()
    await page.getByRole('heading', { name: '拆解当前任务' }).waitFor({ state: 'visible' })
    await page.locator('.draft-history button').first().click()
    await page.getByRole('heading', { name: '逐项确认工作内容' }).waitFor({ state: 'visible' })
    await page.waitForFunction(() => document.querySelector('.planning-drawer .el-drawer__body')?.scrollTop === 0)
    const planningOverflow = await page.locator('.planning-drawer .el-drawer__body').evaluate(element => Math.max(0, element.scrollWidth - element.clientWidth))
    results.push({ width, route: 'ai-planning-review', visible: true, overflow: planningOverflow })
    await page.screenshot({ path: path.join(outputDir, `ai-planning-review-${width}.png`), fullPage: true })
    await page.keyboard.press('Escape')
    await page.keyboard.press('Escape')
    if (width === 1440) {
      await page.goto(`${baseUrl}/dashboard`, { waitUntil: 'networkidle' })
      await page.getByRole('button', { name: '打开通知中心' }).click()
      results.push({ width, route: 'notification-center', visible: await page.getByRole('heading', { name: '通知中心' }).isVisible() })
      await page.keyboard.press('Escape')
      await page.getByRole('button', { name: '新建项目' }).click()
      results.push({ width, route: 'create-project-dialog', visible: await page.locator('.el-dialog').isVisible() })
      await page.keyboard.press('Escape')
      await page.goto(`${baseUrl}/project/1`, { waitUntil: 'networkidle' })
      await page.getByRole('button', { name: '新建任务' }).first().click()
      results.push({ width, route: 'create-task-dialog', visible: await page.locator('.el-dialog').isVisible() })
      await page.keyboard.press('Escape')
      await page.locator('.task-card').first().click()
      results.push({ width, route: 'task-comments', visible: await page.getByRole('tab', { name: '评论协作' }).isVisible() })
      await page.getByRole('tab', { name: '活动时间线' }).click()
      results.push({ width, route: 'task-activities', visible: await page.getByText('将任务从待办移动到进行中').isVisible() })
      await page.keyboard.press('Escape')
      await page.goto(`${baseUrl}/project/1/manage?section=risk`, { waitUntil: 'networkidle' })
      results.push({ width, route: 'risk-center', visible: await page.locator('.risk-row').first().isVisible() })
    }
    if (width === 1024) {
      await page.goto(`${baseUrl}/dashboard`, { waitUntil: 'networkidle' })
      results.push({ width, route: 'tablet-sidebar', sidebarWidth: await page.locator('.shell-sidebar').evaluate(element => Math.round(element.getBoundingClientRect().width)) })
    }
    if (width === 390) {
      await page.goto(`${baseUrl}/dashboard`, { waitUntil: 'networkidle' })
      const drawerButton = page.getByRole('button', { name: '打开导航' })
      if (await drawerButton.count()) {
        await drawerButton.click()
        results.push({ width, route: 'mobile-drawer', visible: await page.locator('.shell-sidebar.is-mobile-open').isVisible() })
        await page.getByRole('button', { name: '关闭导航' }).first().click()
      } else results.push({ width, route: 'mobile-drawer', visible: false, url: page.url() })
      await page.goto(`${baseUrl}/project/1`, { waitUntil: 'networkidle' })
      await page.locator('.mobile-status-tabs button').filter({ hasText: '进行中' }).click()
      results.push({ width, route: 'mobile-board-segment', active: (await page.locator('.column.mobile-active .column-header').innerText()).includes('进行中') })
      await page.goto(`${baseUrl}/project/1/wiki?projectName=毕业设计协作平台`, { waitUntil: 'networkidle' })
      await page.getByRole('button', { name: '文档列表' }).click()
      results.push({ width, route: 'mobile-wiki-drawer', visible: await page.locator('.wiki-sidebar.is-open').isVisible() })
    }
    await context.close()
  }
  await browser.close()
  const failures = results.filter(item => (item.overflow || 0) > 0 || (item.errors || []).length || item.visible === false || item.active === false || item.layoutBroken === true)
  console.log(JSON.stringify(results, null, 2))
  if (failures.length) throw new Error(`页面巡检失败：${JSON.stringify(failures)}`)
})().catch(error => { console.error(error); process.exit(1) })
