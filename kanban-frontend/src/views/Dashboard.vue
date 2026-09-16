<template>
  <AppShell>
  <div class="dashboard">
    <main class="main">
      <PageHeader eyebrow="个人工作台" title="今天从这里开始" :description="`${workspace.todayCount || 0} 项今天到期 · ${workspace.overdueCount || 0} 项已经逾期`">
        <template #actions>
          <el-button size="large" @click="joinDialogVisible = true">加入项目</el-button>
          <el-button type="primary" size="large" @click="dialogVisible = true">+ 新建项目</el-button>
        </template>
      </PageHeader>

      <section class="workspace-search">
        <el-input v-model="searchKeyword" size="large" clearable placeholder="搜索项目、任务或文档" @input="queueSearch" @focus="searchOpen = true" @blur="closeSearchLater">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div v-if="searchOpen && searchKeyword.trim()" class="search-results" @mousedown.prevent>
          <template v-if="searchLoading"><p class="search-empty">正在搜索…</p></template>
          <template v-else-if="searchCount">
            <div v-for="group in searchGroups" :key="group.key" class="search-group">
              <small>{{ group.label }}</small>
              <button v-for="item in group.items" :key="`${item.type}-${item.id}`" @click="visitSearch(item)">
                <strong>{{ item.title }}</strong><span>{{ item.description || '打开查看详情' }}</span>
              </button>
            </div>
          </template>
          <p v-else class="search-empty">没有找到匹配内容</p>
        </div>
      </section>

      <section v-if="roleView.identity" class="role-workbench">
        <div class="role-intro">
          <span>{{ identityLabel(roleView.identity) }} · ROLE VIEW</span>
          <h2>{{ roleView.title }}</h2>
          <p>{{ roleView.subtitle }}</p>
          <el-button v-if="roleView.primaryAction === 'OPEN_PRODUCT_LAB' && projects.length" type="primary" @click="$router.push(`/project/${projects[0].id}/product-lab`)">进入产品共创</el-button>
        </div>
        <button v-for="item in roleView.sections || []" :key="item.key" class="role-signal" @click="openRoleSignal(item)">
          <span>{{ item.label }}</span><strong>{{ item.count }}</strong><small>查看相关工作 <b>→</b></small>
        </button>
      </section>

      <section class="work-summary" aria-label="我的工作摘要">
        <button v-for="item in summaryCards" :key="item.scope" :class="{ danger: item.scope === 'OVERDUE' && item.value }" @click="openScope(item.scope)">
          <span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.hint }}</small>
        </button>
      </section>

      <section class="work-queue">
        <header><div><small>行动队列</small><h2>{{ scopeTitle }}</h2></div><el-button type="primary" plain @click="quickTaskVisible = true">快速新建</el-button></header>
        <LoadingSkeleton v-if="workspaceLoading" :rows="3" />
        <StatePanel v-else-if="workspaceError" tone="error" title="工作队列暂时无法加载" :description="workspaceError">
          <template #actions><el-button type="primary" plain @click="loadWorkspace">重新加载</el-button></template>
        </StatePanel>
        <div v-else-if="visibleTasks.length" class="work-task-list">
          <button v-for="task in visibleTasks" :key="task.id" @click="openTask(task)">
            <span class="task-state" :class="task.status.toLowerCase()">{{ statusLabel(task.status) }}</span>
            <span class="task-copy"><strong>{{ task.title }}</strong><small>{{ task.projectName }}<template v-if="task.blocked"> · 被前置任务阻塞</template></small></span>
            <time :class="{ overdue: isOverdue(task.dueDate) }">{{ task.dueDate || '未设置日期' }}</time><span class="task-arrow">→</span>
          </button>
        </div>
        <StatePanel v-else title="当前队列已经清空" description="没有需要你立即处理的任务，可以继续推进本周计划。" />
      </section>

      <div class="section-heading"><div><small>项目空间</small><h2>我的项目</h2></div><span>{{ projects.length }} 个项目</span></div>

      <LoadingSkeleton v-if="loading" :rows="4" />
      <section v-else class="project-list" aria-label="项目列表">
        <article
          v-for="item in projects"
          :key="item.id"
          class="project-card"
          @click="$router.push(`/project/${item.id}`)"
        >
          <div class="project-main">
            <div class="project-glyph" aria-hidden="true">{{ item.name?.slice(0, 1) }}</div>
            <div class="project-copy">
              <h4>{{ item.name }}</h4>
              <p>{{ item.description || '暂无描述' }}</p>
            </div>
          </div>
          <span class="project-date">{{ item.createdAt?.slice(0, 10) }}</span>
          <el-dropdown trigger="click" @click.stop>
            <button class="card-more-btn" aria-label="项目操作" @click.stop><el-icon><MoreFilled /></el-icon></button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item @click.stop="openMembersDialog(item)"><el-icon><UserFilled /></el-icon> 团队成员</el-dropdown-item>
              <el-dropdown-item @click.stop="goToWiki(item)"><el-icon><Document /></el-icon> 文档中心</el-dropdown-item>
              <el-dropdown-item :disabled="!capabilitiesFor(item).canManageProject" @click.stop="openEditDialog(item)" divided><el-icon><Edit /></el-icon> {{ capabilitiesFor(item).canManageProject ? '修改项目信息' : '修改项目信息（仅管理员）' }}</el-dropdown-item>
              <el-dropdown-item :disabled="!capabilitiesFor(item).canDeleteProject" :title="capabilitiesFor(item).canDeleteProject ? '' : permissionReason('deleteProject')" @click.stop="handleDelete(item)" divided><el-icon><Delete /></el-icon> {{ capabilitiesFor(item).canDeleteProject ? '移入回收站' : '移入回收站（仅负责人）' }}</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
          <span class="project-arrow" aria-hidden="true">→</span>
        </article>

        <StatePanel v-if="projectsError" tone="error" title="项目列表暂时无法加载" :description="projectsError">
          <template #actions><el-button type="primary" plain @click="fetchProjects">重新加载</el-button></template>
        </StatePanel>
        <StatePanel v-else-if="projects.length === 0" title="还没有项目" description="输入团队提供的邀请码，加入后即可参与协作">
          <template #actions>
            <el-button @click="joinDialogVisible = true">输入邀请码加入</el-button>
            <el-button type="primary" @click="dialogVisible = true">创建项目</el-button>
          </template>
        </StatePanel>
      </section>
    </main>

    <el-dialog v-model="dialogVisible" title="新建项目" width="440px" :close-on-click-modal="false">
      <div class="dialog-form">
        <div class="input-group">
          <label>项目名称</label>
          <el-input v-model="form.name" placeholder="例如：官网改版" size="large" />
        </div>
        <div class="input-group">
          <label>项目描述 <span class="optional">选填</span></label>
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="简短描述项目目标" />
        </div>
        <div class="input-group">
          <label>项目模板 <span class="optional">选填</span></label>
          <el-select v-model="form.templateId" clearable placeholder="从空项目开始" style="width:100%">
            <el-option v-for="item in templates" :key="item.id" :value="item.id" :label="`${item.name} · ${item.taskCount} 项任务`" />
          </el-select>
        </div>
        <div v-if="form.templateId" class="input-group">
          <label>项目起始日期</label><el-date-picker v-model="form.startDate" value-format="YYYY-MM-DD" type="date" style="width:100%" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建项目</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="quickTaskVisible" title="快速新建任务" width="440px" :close-on-click-modal="false">
      <div class="dialog-form">
        <div class="input-group"><label>所属项目</label><el-select v-model="quickTask.projectId" placeholder="选择项目" style="width:100%"><el-option v-for="item in projects" :key="item.id" :label="item.name" :value="item.id" /></el-select></div>
        <div class="input-group"><label>任务标题</label><el-input v-model="quickTask.title" maxlength="255" placeholder="写下下一步行动" @keyup.enter="createQuickTask" /></div>
        <div class="input-group"><label>截止日期 <span class="optional">选填</span></label><el-date-picker v-model="quickTask.dueDate" value-format="YYYY-MM-DD" type="date" style="width:100%" /></div>
      </div>
      <template #footer><el-button @click="quickTaskVisible=false">取消</el-button><el-button type="primary" :loading="quickTaskSaving" @click="createQuickTask">创建任务</el-button></template>
    </el-dialog>

    <el-dialog v-model="joinDialogVisible" title="加入项目" width="420px" :close-on-click-modal="false">
      <div class="dialog-form">
        <div class="input-group">
          <label>项目邀请码</label>
          <el-input v-model="joinForm.inviteCode" maxlength="8" placeholder="请输入 8 位邀请码" clearable @keyup.enter="handleJoin">
            <template #prefix>⌘</template>
          </el-input>
          <p class="form-hint">邀请码由项目负责人在“团队成员”中生成并分享给你。</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="joinDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="joining" @click="handleJoin">加入并选择岗位</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="修改项目信息" width="440px" :close-on-click-modal="false">
      <div class="dialog-form">
        <div class="input-group">
          <label>项目名称</label>
          <el-input v-model="editForm.name" placeholder="项目名称" size="large" />
        </div>
        <div class="input-group">
          <label>项目描述 <span class="optional">选填</span></label>
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="简短描述项目目标" />
        </div>
      </div>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleUpdate">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="membersDialogVisible" :title="`${memberProject?.name || ''} · 团队成员`" width="720px" class="members-dialog" :close-on-click-modal="false">
      <div class="member-invite" v-if="memberManageable">
        <div class="member-invite-title">项目邀请码</div>
        <div class="invite-code-row">
          <code>{{ inviteCode || '正在生成…' }}</code>
          <el-button size="small" :disabled="!inviteCode" @click="copyInviteCode">复制邀请码</el-button>
        </div>
        <p class="form-hint">成员注册后可在主页输入邀请码，自行加入项目并选择岗位。</p>
      </div>
      <p v-if="!memberLoading && !memberManageable" class="form-hint">你可以查看项目成员；只有项目负责人或项目管理员可以查看邀请码、邀请或管理成员。</p>
      <div class="member-invite" v-if="memberManageable">
        <div class="member-invite-title">按用户名邀请成员</div>
        <div class="member-invite-form">
          <el-input v-model="inviteForm.username" placeholder="输入对方用户名" clearable />
          <el-select v-model="inviteForm.identity" placeholder="项目身份" style="width: 150px">
            <el-option v-for="item in identityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="inviteForm.permission" placeholder="权限" style="width: 140px">
            <el-option label="普通成员" value="MEMBER" />
            <el-option label="项目管理员" value="PROJECT_ADMIN" />
            <el-option label="只读成员" value="VIEWER" />
          </el-select>
          <el-button type="primary" :loading="inviteSubmitting" @click="handleInvite">邀请</el-button>
        </div>
      </div>

      <el-table :data="members" v-loading="memberLoading" class="members-table">
        <el-table-column label="成员" min-width="160">
          <template #default="{ row }">
            <div class="member-person"><span class="member-avatar">{{ (row.nickname || row.username)?.[0]?.toUpperCase() }}</span><span>{{ row.nickname || row.username }}</span><small>@{{ row.username }}</small></div>
          </template>
        </el-table-column>
        <el-table-column label="项目身份" width="150">
          <template #default="{ row }">
            <el-select v-if="memberManageable && !row.owner" :model-value="row.identity" size="small" @change="value => changeMember(row, value, row.permission)">
              <el-option v-for="item in identityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <span v-else>{{ identityLabel(row.identity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="权限" width="140">
          <template #default="{ row }">
            <el-select v-if="memberManageable && !row.owner" :model-value="row.permission" size="small" @change="value => changeMember(row, row.identity, value)">
              <el-option label="普通成员" value="MEMBER" /><el-option label="项目管理员" value="PROJECT_ADMIN" /><el-option label="只读成员" value="VIEWER" />
            </el-select>
            <el-tag v-else :type="row.owner ? 'warning' : 'info'">{{ row.owner ? '负责人' : permissionLabel(row.permission) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" v-if="memberManageable">
          <template #default="{ row }">
            <el-button v-if="!row.owner" text type="warning" size="small" @click="handleTransfer(row)">转移负责人</el-button>
            <el-button v-if="!row.owner" text type="danger" size="small" @click="handleRemoveMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 身份选择弹窗 -->
    <el-dialog
      v-model="showIdentityDialog"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      width="520px"
      class="identity-dialog"
    >
      <template #header>
        <div class="identity-dialog-header">
          <h3>请选择您的专业身份</h3>
          <p class="identity-subtitle">这将帮助团队了解您的专业技能方向</p>
        </div>
      </template>
      <div class="identity-grid">
        <div
          v-for="item in identityOptions"
          :key="item.value"
          class="identity-card"
          :class="{ selected: selecting === item.value }"
          @click="handleSelectIdentity(item.value)"
        >
          <span class="identity-short">{{ item.short }}</span>
          <span class="identity-label">{{ item.label }}</span>
          <span class="identity-code">{{ item.value }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
  </AppShell>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Edit, Delete, Document, UserFilled, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import AppShell from '@/components/AppShell.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { listProjects, createProject, updateProject, deleteProject, listProjectMembers, inviteProjectMember, updateProjectMember, removeProjectMember, transferProjectOwner, getProjectInviteCode, joinProjectByInviteCode } from '@/api/project'
import { createTask } from '@/api/task'
import { getRoleView, getWorkspaceOverview, getWorkspaceTasks, globalSearch } from '@/api/workspace'
import { listProjectTemplates, createProjectFromTemplate } from '@/api/template'
import { verifyEmail } from '@/api/email'
import { permissionReason, projectCapabilities } from '@/utils/permissions'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const projects = ref([])
const loading = ref(false)
const projectsError = ref('')
const workspaceError = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ name: '', description: '', templateId: null, startDate: new Date().toISOString().slice(0, 10) })
const joinDialogVisible = ref(false)
const joining = ref(false)
const joinForm = reactive({ inviteCode: '' })

const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editForm = reactive({ id: null, name: '', description: '' })

const membersDialogVisible = ref(false)
const memberProject = ref(null)
const members = ref([])
const memberLoading = ref(false)
const memberManageable = ref(false)
const inviteSubmitting = ref(false)
const inviteForm = reactive({ username: '', identity: 'FRONTEND_DEV', permission: 'MEMBER' })
const inviteCode = ref('')

const workspace = ref({})
const roleView = ref({})
const workspaceTasks = ref([])
const workspaceLoading = ref(false)
const activeScope = ref('MY')
const searchKeyword = ref('')
const searchOpen = ref(false)
const searchLoading = ref(false)
const searchResults = ref({ projects: [], tasks: [], wikis: [] })
let searchTimer
const quickTaskVisible = ref(false)
const quickTaskSaving = ref(false)
const quickTask = reactive({ projectId: null, title: '', dueDate: '' })
const templates = ref([])

const summaryCards = computed(() => [
  { scope: 'MY', label: '我的待办', value: workspace.value.myCount || 0, hint: '最近需要推进' },
  { scope: 'TODAY', label: '今天到期', value: workspace.value.todayCount || 0, hint: '优先完成' },
  { scope: 'OVERDUE', label: '已经逾期', value: workspace.value.overdueCount || 0, hint: '需要立即处理' },
  { scope: 'WEEK', label: '本周到期', value: workspace.value.weekCount || 0, hint: '安排本周节奏' },
  { scope: 'BLOCKED', label: '被阻塞', value: workspace.value.blockedCount || 0, hint: '先解除依赖' },
  { scope: 'MENTIONED', label: '提及我的', value: workspace.value.mentionedCount || 0, hint: '查看协作消息' }
])
const scopeTitle = computed(() => ({ MY: '近期需要推进', TODAY: '今天到期', OVERDUE: '逾期任务', WEEK: '本周计划', BLOCKED: '被阻塞任务', MENTIONED: '提及我的任务' }[activeScope.value] || '任务'))
const visibleTasks = computed(() => workspaceTasks.value)
const searchGroups = computed(() => [
  { key: 'projects', label: '项目', items: searchResults.value.projects || [] },
  { key: 'tasks', label: '任务', items: searchResults.value.tasks || [] },
  { key: 'wikis', label: '文档', items: searchResults.value.wikis || [] }
].filter(group => group.items.length))
const searchCount = computed(() => searchGroups.value.reduce((sum, group) => sum + group.items.length, 0))

const showIdentityDialog = ref(false)
const selecting = ref(null)

const identityOptions = [
  { value: 'PROJECT_MANAGER', label: '项目经理', short: 'PM' },
  { value: 'PRODUCT_MANAGER', label: '产品经理', short: 'PD' },
  { value: 'FRONTEND_DEV',   label: '前端工程师', short: 'FE' },
  { value: 'BACKEND_DEV',    label: '后端工程师', short: 'BE' },
  { value: 'QA_TESTER',      label: '测试工程师', short: 'QA' },
  { value: 'UI_DESIGNER',    label: 'UI设计师',   short: 'UI' }
]

async function handleSelectIdentity(identity) {
  if (selecting.value) return
  selecting.value = identity
  try {
    await userStore.updateIdentity(identity)
    ElMessage.success('身份设置成功')
    showIdentityDialog.value = false
  } finally {
    selecting.value = null
  }
}

async function fetchProjects() {
  console.log('[Dashboard] 开始加载项目列表...')
  loading.value = true
  projectsError.value = ''
  try {
    const res = await listProjects({ errorMode: 'silent' })
    console.log('[Dashboard] 接口返回:', res)
    const list = res.data?.data
    if (Array.isArray(list)) {
      projects.value = list
      console.log('[Dashboard] 成功加载 ' + list.length + ' 个项目')
    } else {
      console.error('[Dashboard] 返回数据格式异常:', res.data)
      projects.value = []
      ElMessage.warning('项目数据格式异常，请联系管理员')
    }
  } catch (error) {
    console.error('[Dashboard] 加载项目列表失败:', error)
    projects.value = []
    projectsError.value = error.message || '暂时无法连接服务器，请检查网络或稍后重试'
  } finally {
    loading.value = false
    console.log('[Dashboard] 加载完成, loading=', loading.value)
  }
}

async function loadWorkspace() {
  workspaceLoading.value = true
  workspaceError.value = ''
  try {
    const res = await getWorkspaceOverview({ errorMode: 'silent' })
    workspace.value = res.data?.data || {}
    workspaceTasks.value = workspace.value.tasks || []
    activeScope.value = 'MY'
  } catch (error) {
    workspace.value = {}
    workspaceTasks.value = []
    workspaceError.value = error.message || '暂时无法连接服务器，请检查网络或稍后重试'
  } finally {
    workspaceLoading.value = false
  }
}

async function loadRoleView() {
  try { roleView.value = (await getRoleView()).data?.data || {} }
  catch { roleView.value = {} }
}

function openRoleSignal(item) {
  if (item.route?.startsWith('/')) router.push(item.route)
}

async function openScope(scope) {
  activeScope.value = scope
  workspaceLoading.value = true
  try {
    const res = await getWorkspaceTasks({ scope, page: 1, size: 20 })
    workspaceTasks.value = res.data?.data?.items || []
  } finally {
    workspaceLoading.value = false
  }
}

function statusLabel(status) {
  return { TODO: '待办', IN_PROGRESS: '进行中', DONE: '已完成' }[status] || status
}

function isOverdue(date) {
  return Boolean(date && date < new Date().toISOString().slice(0, 10))
}

function openTask(task) {
  router.push({ path: `/project/${task.projectId}`, query: { task: String(task.id) } })
}

function queueSearch() {
  clearTimeout(searchTimer)
  searchOpen.value = true
  if (!searchKeyword.value.trim()) {
    searchResults.value = { projects: [], tasks: [], wikis: [] }
    return
  }
  searchTimer = setTimeout(runSearch, 220)
}

function closeSearchLater() {
  setTimeout(() => { searchOpen.value = false }, 160)
}

async function runSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) return
  searchLoading.value = true
  try {
    const res = await globalSearch(keyword)
    if (keyword === searchKeyword.value.trim()) searchResults.value = res.data?.data || { projects: [], tasks: [], wikis: [] }
  } finally {
    searchLoading.value = false
  }
}

function visitSearch(item) {
  searchOpen.value = false
  router.push(item.route || `/project/${item.projectId}`)
}

async function createQuickTask() {
  if (!quickTask.projectId || !quickTask.title.trim()) {
    ElMessage.warning('请选择项目并填写任务标题')
    return
  }
  quickTaskSaving.value = true
  try {
    await createTask(quickTask.projectId, quickTask.title.trim(), '', null, quickTask.dueDate || null)
    ElMessage.success('任务已创建')
    quickTaskVisible.value = false
    quickTask.title = ''
    quickTask.dueDate = ''
    await loadWorkspace()
  } finally {
    quickTaskSaving.value = false
  }
}

async function loadTemplates() {
  try {
    const res = await listProjectTemplates()
    templates.value = res.data?.data || []
  } catch {
    templates.value = []
  }
}

async function handleCreate() {
  if (!form.name.trim()) { ElMessage.warning('请输入项目名称'); return }
  submitting.value = true
  try {
    if (form.templateId) {
      await createProjectFromTemplate({ templateId: form.templateId, name: form.name.trim(), description: form.description, startDate: form.startDate })
    } else {
      await createProject(form.name.trim(), form.description)
    }
    ElMessage.success('项目创建成功')
    dialogVisible.value = false
    form.name = ''
    form.description = ''
    form.templateId = null
    await Promise.all([fetchProjects(), loadWorkspace()])
  } finally {
    submitting.value = false
  }
}

function goToWiki(project) {
  router.push({ path: `/project/${project.id}/wiki`, query: { projectName: project.name } })
}

const identityLabels = Object.fromEntries(identityOptions.map(item => [item.value, item.label]))
function identityLabel(value) { return identityLabels[value] || '未设置' }
function permissionLabel(value) { return { PROJECT_ADMIN: '项目管理员', MEMBER: '普通成员', VIEWER: '只读成员' }[value] || '普通成员' }
function capabilitiesFor(project) {
  return projectCapabilities({
    ...project,
    owner: project.owner ?? project.creatorId === userStore.userInfo?.userId
  })
}

async function openMembersDialog(project) {
  memberProject.value = project
  membersDialogVisible.value = true
  await fetchMembersForManagement()
  if (memberManageable.value) {
    const res = await getProjectInviteCode(project.id)
    inviteCode.value = res.data?.data?.inviteCode || ''
  }
}

async function copyInviteCode() {
  try {
    await navigator.clipboard.writeText(inviteCode.value)
    ElMessage.success('邀请码已复制')
  } catch {
    ElMessage.info(`邀请码：${inviteCode.value}`)
  }
}

async function handleJoin() {
  const code = joinForm.inviteCode.trim().toUpperCase()
  if (!code) { ElMessage.warning('请输入项目邀请码'); return }
  joining.value = true
  try {
    const res = await joinProjectByInviteCode(code)
    const project = res.data?.data
    joinDialogVisible.value = false
    joinForm.inviteCode = ''
    ElMessage.success('已加入项目，请选择你在该项目中的岗位')
    router.push({ path: `/project/${project.id}`, query: { setupRole: '1' } })
  } finally {
    joining.value = false
  }
}

async function fetchMembersForManagement() {
  if (!memberProject.value) return
  memberLoading.value = true
  try {
    const res = await listProjectMembers(memberProject.value.id)
    members.value = res.data?.data || []
    memberManageable.value = capabilitiesFor(memberProject.value).canManageMembers
  } finally { memberLoading.value = false }
}

async function handleInvite() {
  if (!inviteForm.username.trim()) { ElMessage.warning('请输入用户名'); return }
  inviteSubmitting.value = true
  try {
    await inviteProjectMember(memberProject.value.id, inviteForm.username.trim(), inviteForm.identity, inviteForm.permission)
    ElMessage.success('成员已加入项目')
    inviteForm.username = ''
    await fetchMembersForManagement()
  } finally { inviteSubmitting.value = false }
}

async function changeMember(row, identity, permission) {
  try {
    await updateProjectMember(memberProject.value.id, row.userId, identity, permission)
    row.identity = identity; row.permission = permission
    ElMessage.success('成员设置已更新')
  } catch { await fetchMembersForManagement() }
}

async function handleRemoveMember(row) {
  try { await ElMessageBox.confirm(`移除后，${row.nickname || row.username} 将无法再访问本项目；其历史评论、工时和操作记录会继续保留。`, '移除项目成员', { type: 'warning', confirmButtonText: '确认移除', cancelButtonText: '取消' }) } catch { return }
  try { await removeProjectMember(memberProject.value.id, row.userId); ElMessage.success('成员已移出项目，历史记录已保留'); await fetchMembersForManagement() }
  catch { /* 请求错误已由统一反馈处理 */ }
}

async function handleTransfer(row) {
  try { await ElMessageBox.confirm(`转移后你将成为项目管理员，确定将负责人转给 ${row.nickname || row.username}？`, '转移项目负责人', { type: 'warning' }) } catch { return }
  try { await transferProjectOwner(memberProject.value.id, row.userId); ElMessage.success('项目负责人已转移'); await fetchMembersForManagement() }
  catch { /* 请求错误已由统一反馈处理 */ }
}

function openEditDialog(project) {
  if (!capabilitiesFor(project).canManageProject) return ElMessage.warning(permissionReason('manageProject'))
  editForm.id = project.id
  editForm.name = project.name
  editForm.description = project.description || ''
  editDialogVisible.value = true
}

async function handleUpdate() {
  if (!editForm.name.trim()) { ElMessage.warning('项目名称不能为空'); return }
  editSubmitting.value = true
  try {
    await updateProject(editForm.id, editForm.name, editForm.description)
    ElMessage.success('项目信息已更新')
    editDialogVisible.value = false
    await fetchProjects()
  } finally {
    editSubmitting.value = false
  }
}

async function handleDelete(project) {
  if (!capabilitiesFor(project).canDeleteProject) return ElMessage.warning(permissionReason('deleteProject'))
  try {
    await ElMessageBox.confirm(
      `“${project.name}”及其任务、文档和附件会移入回收站，数据将完整保留，可由负责人或项目管理员恢复。`,
      `移入回收站：${project.name}`,
      { confirmButtonText: '移入回收站', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteProject(project.id)
    ElMessage.success('项目已移入回收站')
    await fetchProjects()
  } catch { /* 请求错误已由统一反馈处理 */ }
}

onMounted(async () => {
  await Promise.all([fetchProjects(), loadWorkspace(), loadRoleView(), loadTemplates()])
  if (['MY', 'TODAY', 'OVERDUE', 'WEEK', 'BLOCKED', 'MENTIONED'].includes(route.query.scope)) await openScope(route.query.scope)
  if (route.query.verifyEmail) {
    try {
      await verifyEmail(route.query.verifyEmail)
      ElMessage.success('邮箱验证成功')
      const query = { ...route.query }
      delete query.verifyEmail
      router.replace({ query })
    } catch { /* 请求错误已由统一反馈处理 */ }
  }
  if (userStore.needsIdentityPrompt && projects.value.length > 0) {
    showIdentityDialog.value = true
  }
})
</script>

<style scoped media="not all">
.dashboard { min-height: 100vh; }
.topbar {
  display: flex; justify-content: space-between; align-items: center;
  height: 56px; padding: 0 24px;
  background: #242321; border-bottom: 1px solid #3A3732;
}
.topbar-left { display: flex; align-items: center; gap: 10px; }
.brand { font-size: 16px; font-weight: 700; color: #F7F1E7; letter-spacing: .02em; }
.topbar-right { display: flex; align-items: center; gap: 8px; }
.avatar-dot {
  width: 28px; height: 28px; border-radius: 50%;
  background: var(--brand-gradient); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
}
.username { font-size: 13px; color: #B9B1A5; }
.logout-btn {
  background: none; border: none; color: #B9B1A5;
  font-size: 13px; cursor: pointer; padding: 4px 8px; border-radius: 4px;
}
.logout-btn:hover { color: #E2A43A; background: rgba(226,164,58,.12); }
.analytics-btn {
  display: inline-flex; align-items: center; gap: 5px;
  background: #D58A22; color: #241D14; border: none;
  font-size: 13px; font-weight: 500; cursor: pointer;
  padding: 6px 14px; border-radius: var(--radius-sm);
  transition: opacity 0.15s; margin-right: 6px;
}
.analytics-btn:hover { opacity: 0.88; }
.admin-btn { background: transparent; border: 1px solid rgba(226,164,58,.6); color: #E2A43A; }
.recycle-btn { background: transparent; border: 1px solid #5A554D; color: #D0C8BC; }

.main { max-width: 1200px; margin: 0 auto; padding: 52px 30px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 28px; }
.page-actions { display: flex; gap: 10px; }
.page-header h2 { margin: 0; font-family: var(--font-display); font-size: 30px; font-weight: 700; letter-spacing: -.03em; }
.page-desc { margin: 4px 0 0; font-size: 13px; color: var(--text-tertiary); }
.page-header :deep(.el-button--primary) {
  background: var(--brand-gradient); border: none; border-radius: var(--radius-sm);
  font-weight: 600;
}

.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 22px; }
.project-card {
  background: #242321; border-radius: var(--radius);
  box-shadow: 0 12px 24px rgba(42,34,24,.12); border: 1px solid #393632;
  cursor: pointer; transition: transform 0.18s, box-shadow 0.18s; overflow: hidden;
}
.project-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.card-accent { height: 4px; }
.card-body { padding: 20px 22px 18px; }
.card-header {
  display: flex; justify-content: space-between; align-items: flex-start;
  gap: 8px; margin-bottom: 6px;
}
.card-header h4 { margin: 0; font-size: 17px; font-weight: 650; color: #F7F1E7; flex: 1; }
.card-more-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 6px;
  color: var(--text-tertiary); cursor: pointer;
  transition: background 0.15s, color 0.15s; flex-shrink: 0;
}
.card-more-btn:hover { background: #3A3732; color: #F7F1E7; }
.card-desc {
  margin: 8px 0 24px; font-size: 13px; color: #B9B1A5;
  line-height: 1.5; min-height: 20px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.card-meta { display: flex; justify-content: space-between; align-items: center; }
.card-date { font-size: 12px; color: #8F887E; }
.card-arrow { font-size: 16px; color: #D58A22; transition: transform 0.15s; }
.project-card:hover .card-arrow { color: #E2A43A; transform: translateX(3px); }

.empty-state {
  grid-column: 1 / -1; text-align: center; padding: 64px 24px;
}
.empty-icon { margin-bottom: 16px; }
.empty-state h3 { margin: 0; font-size: 18px; color: var(--text-primary); }
.empty-state p { margin: 8px 0 20px; color: var(--text-tertiary); font-size: 14px; }
.empty-actions { display: flex; justify-content: center; gap: 10px; }

.dialog-form .input-group { margin-bottom: 16px; }
.dialog-form label { display: block; font-size: 13px; font-weight: 500; color: var(--text-secondary); margin-bottom: 6px; }
.optional { font-weight: 400; color: var(--text-tertiary); font-size: 12px; }
.dialog-form :deep(.el-input__wrapper) { border-radius: var(--radius-sm); }

.members-dialog :deep(.el-dialog__body) { padding-top: 8px; }
.member-invite { padding: 16px; margin-bottom: 18px; background: #F4EFE6; border: 1px solid var(--border); border-radius: var(--radius); }
.member-invite-title { margin-bottom: 10px; font-size: 13px; font-weight: 700; color: var(--text-primary); }
.member-invite-form { display: flex; gap: 8px; align-items: center; }
.member-invite-form .el-input { flex: 1; }
.invite-code-row { display: flex; align-items: center; gap: 10px; }
.invite-code-row code { flex: 1; padding: 9px 12px; border-radius: 6px; background: #242321; color: #F3C66D; font-size: 18px; font-weight: 700; letter-spacing: .14em; text-align: center; }
.form-hint { margin: 8px 0 0; font-size: 12px; line-height: 1.5; color: var(--text-tertiary); }
.member-person { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.member-person small { color: var(--text-tertiary); font-size: 11px; font-weight: 400; }
.member-avatar { width: 28px; height: 28px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--brand); color: #fff; font-size: 12px; }
.members-table :deep(.el-table__header th) { background: #F4EFE6; color: var(--text-secondary); }

@media (max-width: 640px) {
  .card-grid { grid-template-columns: 1fr; }
  .page-header { flex-direction: column; gap: 12px; }
  .page-actions { width: 100%; }
  .page-actions .el-button { flex: 1; }
}

/* ── 身份选择弹窗 ── */
.identity-dialog :deep(.el-dialog) {
  border-radius: 16px;
}
.identity-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding-bottom: 0;
}
.identity-dialog-header {
  text-align: center;
  padding: 8px 0 4px;
}
.identity-icon-wrapper {
  margin-bottom: 12px;
  display: inline-block;
}
.identity-dialog-header h3 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.identity-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--text-tertiary);
}

.identity-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 8px 0 4px;
}
.identity-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 10px 14px;
  border-radius: 12px;
  border: 2px solid var(--border);
  cursor: pointer;
  transition: all 0.18s;
  background: var(--bg-surface);
}
.identity-card:hover {
  border-color: var(--brand);
  background: #EEF2FF;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.15);
}
.identity-card.selected {
  border-color: var(--brand);
  background: #EEF2FF;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
}
.identity-emoji {
  font-size: 28px;
  line-height: 1;
}
.identity-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}
.identity-code {
  font-size: 11px;
  color: var(--text-tertiary);
  font-family: monospace;
}

@media (max-width: 500px) {
  .identity-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 680px) {
  .member-invite-form { flex-wrap: wrap; }
  .member-invite-form .el-input { flex-basis: 100%; }
}
</style>

<style scoped>
.dashboard { min-height: 100dvh; }
.main { width: min(1180px, 100%); margin: 0 auto; padding: 46px clamp(20px, 4vw, 54px) 72px; }
.workspace-search { position: relative; z-index: 6; width: min(680px, 100%); margin: -6px 0 20px; }
.workspace-search :deep(.el-input__wrapper) { min-height: 48px; padding-inline: 16px; border-radius: 13px; box-shadow: 0 0 0 1px var(--border-light), var(--shadow-xs); }
.search-results { position: absolute; top: calc(100% + 8px); left: 0; right: 0; max-height: 420px; overflow: auto; padding: 10px; border: 1px solid var(--border-light); border-radius: 13px; background: var(--surface); box-shadow: var(--shadow-lg); }
.search-group { display: grid; gap: 3px; padding: 6px 0; }
.search-group + .search-group { border-top: 1px solid var(--border-light); }
.search-group small { padding: 5px 9px; color: var(--text-tertiary); font-size: 11px; font-weight: 700; letter-spacing: .08em; }
.search-group button { display: grid; gap: 3px; width: 100%; padding: 9px; border: 0; border-radius: 8px; color: var(--text-primary); background: transparent; text-align: left; cursor: pointer; }
.search-group button:hover { background: var(--brand-soft); }
.search-group strong { font-size: 13px; }
.search-group span,.search-empty { color: var(--text-tertiary); font-size: 12px; }
.search-empty { margin: 0; padding: 20px; text-align: center; }
.role-workbench { display:grid; grid-template-columns:minmax(260px,1.45fr) repeat(3,minmax(130px,.7fr)); gap:10px; margin:0 0 14px; padding:10px; border:1px solid #dce2ee; border-radius:16px; background:#edf1f7; }
.role-intro { position:relative; min-height:142px; padding:20px; overflow:hidden; border-radius:11px; color:#f8faff; background:#1b2d53; }
.role-intro::after { content:''; position:absolute; right:-34px; bottom:-70px; width:170px; height:170px; border:1px solid rgba(255,255,255,.14); border-radius:50%; box-shadow:0 0 0 28px rgba(255,255,255,.035); }
.role-intro>span { color:#9fb7ff; font-size:9px; font-weight:800; letter-spacing:.15em; }
.role-intro h2 { margin:7px 0 4px; font-size:20px; letter-spacing:-.03em; }
.role-intro p { max-width:360px; margin:0 0 13px; color:#c6d0e2; font-size:11px; }
.role-intro .el-button { position:relative; z-index:1; min-height:30px; }
.role-signal { display:grid; align-content:center; gap:8px; min-width:0; padding:16px; border:1px solid var(--border-light); border-radius:11px; color:var(--text-primary); background:var(--surface); text-align:left; cursor:pointer; }
.role-signal:hover { border-color:#91a7ee; box-shadow:var(--shadow-xs); }
.role-signal span { color:var(--text-secondary); font-size:11px; font-weight:650; }
.role-signal strong { font-size:28px; letter-spacing:-.04em; }
.role-signal small { display:flex; justify-content:space-between; color:var(--text-tertiary); font-size:9px; }
.role-signal small b { color:var(--brand); font-size:14px; }
.work-summary { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; margin-bottom: 24px; }
.work-summary button { display: grid; gap: 6px; min-height: 112px; padding: 16px; border: 1px solid var(--border-light); border-radius: var(--radius); color: var(--text-primary); background: var(--surface); text-align: left; cursor: pointer; transition: border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease; }
.work-summary button:hover { border-color: var(--brand); transform: translateY(-2px); box-shadow: var(--shadow-sm); }
.work-summary button span { color: var(--text-secondary); font-size: 12px; font-weight: 650; }
.work-summary button strong { font-family: var(--font-display); font-size: 27px; letter-spacing: -.03em; }
.work-summary button small { color: var(--text-tertiary); font-size: 10px; white-space: nowrap; }
.work-summary button.danger strong { color: var(--danger); }
.work-queue { margin-bottom: 42px; overflow: hidden; border: 1px solid var(--border-light); border-radius: var(--radius); background: var(--surface); box-shadow: var(--shadow-xs); }
.work-queue > header,.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.work-queue > header { padding: 18px 20px; border-bottom: 1px solid var(--border-light); }
.work-queue header small,.section-heading small { color: var(--brand); font-size: 10px; font-weight: 760; letter-spacing: .1em; text-transform: uppercase; }
.work-queue h2,.section-heading h2 { margin: 4px 0 0; color: var(--text-primary); font-size: 18px; letter-spacing: -.02em; }
.work-task-list { display: grid; }
.work-task-list > button { display: grid; grid-template-columns: 72px minmax(0, 1fr) auto 20px; align-items: center; gap: 14px; min-height: 68px; padding: 12px 20px; border: 0; border-bottom: 1px solid var(--border-light); color: var(--text-primary); background: transparent; text-align: left; cursor: pointer; }
.work-task-list > button:last-child { border-bottom: 0; }
.work-task-list > button:hover { background: var(--surface-subtle); }
.task-state { padding: 4px 6px; border-radius: 5px; color: var(--text-secondary); background: var(--surface-muted); font-size: 10px; font-weight: 700; text-align: center; }
.task-state.in_progress { color: var(--brand-deep); background: var(--brand-soft); }
.task-state.done { color: var(--success); background: color-mix(in srgb, var(--success) 12%, transparent); }
.task-copy { display: grid; min-width: 0; gap: 4px; }
.task-copy strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.task-copy small { overflow: hidden; color: var(--text-tertiary); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.work-task-list time { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 11px; }
.work-task-list time.overdue { color: var(--danger); font-weight: 700; }
.task-arrow { color: var(--brand); }
.section-heading { margin: 0 0 13px; }
.section-heading > span { color: var(--text-tertiary); font-size: 12px; }
.project-list { overflow: hidden; border: 1px solid var(--border-light); border-radius: var(--radius); background: var(--surface); box-shadow: var(--shadow-xs); }
.project-card { display: grid; grid-template-columns: minmax(0, 1fr) auto 36px 24px; align-items: center; gap: 18px; min-height: 92px; padding: 17px 20px; border-bottom: 1px solid var(--border-light); color: var(--text-primary); background: var(--surface); cursor: pointer; transition: background-color 160ms ease; }
.project-card:last-of-type { border-bottom: 0; }
.project-card:hover { background: var(--brand-soft); }
.project-main { display: flex; align-items: center; min-width: 0; gap: 15px; }
.project-glyph { display: grid; place-items: center; flex: 0 0 auto; width: 42px; height: 42px; border-radius: 11px; color: var(--brand-deep); background: var(--brand-light); font-size: 15px; font-weight: 720; }
.project-copy { min-width: 0; }
.project-copy h4 { margin: 0; overflow: hidden; color: var(--text-primary); font-size: 15px; font-weight: 680; text-overflow: ellipsis; white-space: nowrap; }
.project-copy p { margin: 5px 0 0; overflow: hidden; color: var(--text-tertiary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.project-date { color: var(--text-tertiary); font-size: 12px; font-variant-numeric: tabular-nums; }
.card-more-btn { display: inline-grid; place-items: center; width: 34px; height: 34px; padding: 0; border: 0; border-radius: 8px; color: var(--text-tertiary); background: transparent; cursor: pointer; }
.card-more-btn:hover { color: var(--text-primary); background: var(--bg-hover); }
.project-arrow { color: var(--brand); font-size: 18px; transition: transform 160ms ease; }
.project-card:hover .project-arrow { transform: translateX(3px); }
.dialog-form { display: grid; gap: 17px; }
.member-invite { padding: 16px; margin-bottom: 16px; border: 1px solid var(--border-light); border-radius: var(--radius); background: var(--surface-subtle); }
.member-invite-title { margin-bottom: 10px; color: var(--text-primary); font-size: 13px; font-weight: 680; }
.member-invite-form,.invite-code-row { display: flex; align-items: center; gap: 9px; }
.member-invite-form .el-input,.invite-code-row code { flex: 1; }
.invite-code-row code { padding: 9px 12px; border: 1px solid var(--border); border-radius: 8px; color: var(--brand-deep); background: var(--brand-soft); font-family: var(--font-mono); font-size: 17px; font-weight: 700; letter-spacing: .12em; text-align: center; }
.form-hint { margin: 8px 0 0; }
.member-person { display: flex; align-items: center; gap: 8px; font-weight: 620; }
.member-person small { color: var(--text-tertiary); font-size: 11px; font-weight: 400; }
.member-avatar { display: inline-grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; color: #f8faff; background: var(--brand); font-size: 11px; }
.identity-dialog-header { padding: 4px 0 8px; text-align: left; }
.identity-icon-wrapper { display: none; }
.identity-dialog-header h3 { margin: 0; font-size: 21px; letter-spacing: -.02em; }
.identity-subtitle { margin: 6px 0 0; color: var(--text-tertiary); font-size: 13px; }
.identity-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.identity-card { display: flex; flex-direction: column; align-items: flex-start; gap: 7px; min-height: 112px; padding: 16px; border: 1px solid var(--border); border-radius: var(--radius); color: var(--text-primary); background: var(--surface); cursor: pointer; transition: border-color 160ms ease, background-color 160ms ease, transform 120ms ease; }
.identity-card:hover,.identity-card.selected { border-color: var(--brand); background: var(--brand-soft); }
.identity-card:active { transform: translateY(1px); }
.identity-short { color: var(--brand); font-family: var(--font-mono); font-size: 19px; font-weight: 760; }
.identity-label { font-size: 13px; font-weight: 650; }
.identity-code { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 9px; word-break: break-all; }
@media (max-width: 1100px) {
  .role-workbench { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .role-intro { grid-column: 1 / -1; min-height: 126px; }
}
@media (max-width: 720px) {
  .main { padding: 28px 16px 52px; }
  .role-workbench { display:flex; overflow-x:auto; }
  .role-intro { flex:0 0 86%; min-height:126px; }
  .role-signal { flex:0 0 46%; }
  .work-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .work-summary button { min-height: 98px; }
  .work-task-list > button { grid-template-columns: 58px minmax(0, 1fr) 14px; gap: 10px; padding-inline: 14px; }
  .work-task-list time { display: none; }
  .project-list { overflow: visible; border: 0; background: transparent; box-shadow: none; }
  .project-card { grid-template-columns: minmax(0, 1fr) 34px; gap: 12px; margin-bottom: 12px; padding: 16px; border: 1px solid var(--border-light); border-radius: var(--radius); background: var(--surface); }
  .project-date,.project-arrow { display: none; }
  .project-glyph { width: 38px; height: 38px; }
  .member-invite-form { flex-wrap: wrap; }
  .member-invite-form .el-input { flex-basis: 100%; }
  .identity-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 420px) {
  .work-summary { gap: 8px; }
  .work-summary button { padding: 13px; }
  .work-queue > header { padding: 15px; }
  .project-main { align-items: flex-start; }
  .project-copy p { white-space: normal; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
  .identity-grid { grid-template-columns: 1fr 1fr; }
}
</style>
