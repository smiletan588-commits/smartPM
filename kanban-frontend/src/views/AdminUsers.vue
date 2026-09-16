<template>
  <AppShell>
    <main class="content">
      <PageHeader eyebrow="系统管理" title="用户与访问权限" description="统一管理平台账号、系统管理员权限与登录状态。">
        <template #actions><el-button type="primary" :loading="loading" @click="fetchUsers">刷新用户列表</el-button></template>
      </PageHeader>

      <section class="admin-notice"><span>管理员提示</span><p>为保护项目归属记录，用户账号不提供直接删除；可停用账号、修改权限或重置密码。</p></section>

      <section class="ops-grid">
        <article><span>活跃账号</span><strong>{{ system.activeUsers || 0 }}</strong><small>/ {{ system.users || 0 }} 个账号</small></article>
        <article><span>项目 / 任务</span><strong>{{ system.projects || 0 }}<i>/</i>{{ system.tasks || 0 }}</strong><small>当前有效业务数据</small></article>
        <article :class="{ warning: system.failedEmails }"><span>邮件队列</span><strong>{{ system.pendingEmails || 0 }}<i>/</i>{{ system.failedEmails || 0 }}</strong><small>待发送 / 失败</small></article>
        <article :class="{ warning: system.loginFailures24h }"><span>登录失败</span><strong>{{ system.loginFailures24h || 0 }}</strong><small>最近 24 小时</small></article>
        <article><span>数据库版本</span><strong>V{{ system.migrationVersion || '—' }}</strong><small>Flyway 已应用版本</small></article>
      </section>

      <section class="audit-panel">
        <div class="panel-title"><span>最近重要操作</span><small>可追溯的管理与交付事件</small></div>
        <div class="audit-list"><article v-for="item in audits" :key="item.id"><span>{{ item.category }}</span><div><strong>{{ item.summary }}</strong><small>{{ item.actorName || '系统' }} · {{ formatDateTime(item.createdAt) }}</small></div></article><p v-if="!audits.length">暂无审计事件。</p></div>
      </section>

      <section class="audit-panel login-panel">
        <div class="panel-title"><span>最近登录事件</span><small>成功与失败均留痕，便于定位账号安全问题</small></div>
        <div class="login-list">
          <article v-for="item in logins" :key="item.id">
            <el-tag :type="item.success ? 'success' : 'danger'" effect="plain" size="small">{{ item.success ? '成功' : '失败' }}</el-tag>
            <div><strong>@{{ item.username }}</strong><small>{{ item.reason || '身份验证通过' }} · {{ formatDateTime(item.createdAt) }}</small></div>
          </article>
          <p v-if="!logins.length">暂无登录事件。</p>
        </div>
      </section>

      <section class="user-panel" v-loading="loading">
        <div class="panel-title"><span>全部用户</span><small>{{ users.length }} 个账号</small></div>
        <el-table :data="users" class="user-table desktop-table" empty-text="暂无用户数据">
          <el-table-column label="用户" min-width="190">
            <template #default="{ row }"><div class="user-name"><span class="avatar">{{ (row.nickname || row.username)?.slice(0, 1).toUpperCase() }}</span><div><strong>{{ row.nickname || row.username }}</strong><small>@{{ row.username }}</small></div></div></template>
          </el-table-column>
          <el-table-column label="专业身份" min-width="125"><template #default="{ row }"><span class="muted">{{ identityLabel(row.identity) }}</span></template></el-table-column>
          <el-table-column label="系统权限" width="145"><template #default="{ row }"><el-select :model-value="row.systemRole" size="small" :disabled="pendingId === row.id" @change="value => saveRole(row, value)"><el-option label="普通用户" value="USER" /><el-option label="系统管理员" value="ADMIN" /></el-select></template></el-table-column>
          <el-table-column label="账号状态" width="132"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ row.status === 'ACTIVE' ? '正常' : '已停用' }}</el-tag></template></el-table-column>
          <el-table-column label="注册时间" width="124"><template #default="{ row }"><span class="muted">{{ formatDate(row.createdAt) }}</span></template></el-table-column>
          <el-table-column label="操作" width="210" fixed="right"><template #default="{ row }"><el-button text type="primary" :disabled="pendingId === row.id" @click="openPasswordDialog(row)">重置密码</el-button><el-button text :type="row.status === 'ACTIVE' ? 'danger' : 'success'" :disabled="pendingId === row.id" @click="toggleStatus(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button></template></el-table-column>
        </el-table>
        <div class="mobile-records">
          <article v-for="user in users" :key="user.id" class="record-card">
            <div class="user-name"><span class="avatar">{{ (user.nickname || user.username)?.slice(0, 1).toUpperCase() }}</span><div><strong>{{ user.nickname || user.username }}</strong><small>@{{ user.username }}</small></div></div>
            <dl><div><dt>专业身份</dt><dd>{{ identityLabel(user.identity) }}</dd></div><div><dt>账号状态</dt><dd><el-tag :type="user.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ user.status === 'ACTIVE' ? '正常' : '已停用' }}</el-tag></dd></div><div><dt>注册时间</dt><dd>{{ formatDate(user.createdAt) }}</dd></div></dl>
            <el-select :model-value="user.systemRole" size="small" :disabled="pendingId === user.id" aria-label="系统权限" @change="value => saveRole(user, value)"><el-option label="普通用户" value="USER" /><el-option label="系统管理员" value="ADMIN" /></el-select>
            <div class="record-actions"><el-button text type="primary" :disabled="pendingId === user.id" @click="openPasswordDialog(user)">重置密码</el-button><el-button text :type="user.status === 'ACTIVE' ? 'danger' : 'success'" :disabled="pendingId === user.id" @click="toggleStatus(user)">{{ user.status === 'ACTIVE' ? '停用' : '启用' }}</el-button></div>
          </article>
        </div>
      </section>
    </main>

    <el-dialog v-model="passwordDialogVisible" width="390px" title="重置用户密码" :close-on-click-modal="false">
      <p class="dialog-hint">为 <strong>{{ passwordTarget?.nickname || passwordTarget?.username }}</strong> 设置新密码。</p>
      <el-input v-model="newPassword" type="password" show-password placeholder="至少 3 位" @keyup.enter="savePassword" />
      <template #footer><el-button @click="passwordDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingPassword" @click="savePassword">保存新密码</el-button></template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAdminUsers, resetAdminUserPassword, updateAdminUserRole, updateAdminUserStatus } from '@/api/admin'
import { getSystemOverview, listAuditEvents, listLoginEvents } from '@/api/management'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'

const users = ref([])
const loading = ref(false)
const pendingId = ref(null)
const passwordDialogVisible = ref(false)
const passwordTarget = ref(null)
const newPassword = ref('')
const savingPassword = ref(false)
const system = ref({})
const audits = ref([])
const logins = ref([])

const identities = { PROJECT_MANAGER: '项目经理', PRODUCT_MANAGER: '产品经理', FRONTEND_DEV: '前端工程师', BACKEND_DEV: '后端工程师', QA_TESTER: '测试工程师', UI_DESIGNER: 'UI 设计师' }
const identityLabel = value => identities[value] || '未设置'
const formatDate = value => value ? String(value).slice(0, 10) : '未记录'
const formatDateTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12:false }) : '未记录'

async function fetchUsers() {
  loading.value = true
  try { const res = await listAdminUsers(); users.value = res.data?.data || [] } finally { loading.value = false }
}

async function fetchOperations() {
  const [systemRes,auditRes,loginRes] = await Promise.all([getSystemOverview(), listAuditEvents({ page:1,size:12 }), listLoginEvents({ page:1,size:10 })])
  system.value=systemRes.data.data||{}; audits.value=auditRes.data.data||[]; logins.value=loginRes.data.data||[]
}

async function saveRole(row, systemRole) {
  pendingId.value = row.id
  try { await updateAdminUserRole(row.id, systemRole); ElMessage.success('系统权限已更新'); await fetchUsers() } finally { pendingId.value = null }
}

async function toggleStatus(row) {
  const status = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = status === 'ACTIVE' ? '启用' : '停用'
  try { await ElMessageBox.confirm(`确定${action}账号「${row.username}」吗？`, `${action}账号`, { type: 'warning' }) } catch { return }
  pendingId.value = row.id
  try { await updateAdminUserStatus(row.id, status); ElMessage.success(`账号已${action}`); await fetchUsers() } finally { pendingId.value = null }
}

function openPasswordDialog(row) { passwordTarget.value = row; newPassword.value = ''; passwordDialogVisible.value = true }
async function savePassword() {
  if (newPassword.value.length < 3) { ElMessage.warning('新密码至少需要 3 位'); return }
  savingPassword.value = true
  try { await resetAdminUserPassword(passwordTarget.value.id, newPassword.value); ElMessage.success('密码已重置'); passwordDialogVisible.value = false } finally { savingPassword.value = false }
}

onMounted(() => Promise.all([fetchUsers(), fetchOperations()]))
</script>

<style scoped>
.content { max-width: 1240px; margin: 0 auto; padding: 34px 36px 52px; }
.admin-notice { display: flex; align-items: baseline; gap: 14px; padding: 14px 16px; margin: 24px 0 18px; border: 1px solid var(--border); border-left: 3px solid var(--brand); border-radius: 10px; background: var(--brand-light); }
.admin-notice span { color: var(--brand-deep); font-size: 13px; font-weight: 700; white-space: nowrap; }.admin-notice p { margin: 0; color: var(--text-secondary); font-size: 13px; }
.ops-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:9px;margin-bottom:16px}.ops-grid article{display:grid;gap:5px;padding:15px;border:1px solid var(--border-light);border-radius:10px;background:var(--surface)}.ops-grid span,.ops-grid small{color:var(--text-tertiary);font-size:10px}.ops-grid strong{font-size:22px}.ops-grid strong i{margin:0 5px;color:var(--border-strong);font-style:normal}.ops-grid article.warning{border-color:#e7c0c0;background:var(--danger-bg)}.ops-grid article.warning strong{color:var(--danger)}
.audit-panel{margin-bottom:16px;border:1px solid var(--border);border-radius:12px;background:var(--surface);overflow:hidden}.audit-list{display:grid;grid-template-columns:1fr 1fr;gap:0 22px;padding:8px 20px 16px}.audit-list article{display:grid;grid-template-columns:85px 1fr;gap:10px;padding:10px 0;border-bottom:1px solid var(--border-light)}.audit-list>article>span{color:var(--brand-deep);font:650 9px var(--font-mono)}.audit-list strong,.audit-list small{display:block}.audit-list strong{font-size:11px}.audit-list small{margin-top:3px;color:var(--text-tertiary);font-size:9px}.audit-list p{color:var(--text-tertiary);font-size:12px}
.login-list{display:grid;grid-template-columns:1fr 1fr;gap:0 22px;padding:8px 20px 16px}.login-list article{display:grid;grid-template-columns:54px 1fr;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid var(--border-light)}.login-list strong,.login-list small{display:block}.login-list strong{font-size:11px}.login-list small{margin-top:3px;color:var(--text-tertiary);font-size:9px}.login-list p{color:var(--text-tertiary);font-size:12px}
.user-panel { overflow: hidden; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); box-shadow: var(--shadow-xs); }.panel-title { display: flex; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid var(--border-light); color: var(--text-primary); font-weight: 700; }.panel-title small { color: var(--text-tertiary); font-weight: 400; }
.user-name { display: flex; align-items: center; gap: 10px; }.avatar { display: grid; place-items: center; flex: 0 0 auto; width: 32px; height: 32px; border-radius: 50%; background: var(--brand-light); color: var(--brand-deep); font-size: 12px; font-weight: 700; }.user-name strong,.user-name small { display: block; }.user-name small,.muted { color: var(--text-tertiary); font-size: 12px; }.dialog-hint { color: var(--text-secondary); margin: 0 0 14px; }
.mobile-records { display: none; }
@media (max-width: 1023px) { .ops-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 767px) { .content { padding: 22px 16px 36px; }.ops-grid { grid-template-columns: 1fr; }.audit-list,.login-list{grid-template-columns:1fr}.admin-notice { align-items: flex-start; flex-direction: column; gap: 5px; }.desktop-table { display: none; }.mobile-records { display: grid; gap: 12px; padding: 14px; }.record-card { display: grid; gap: 14px; padding: 16px; border: 1px solid var(--border-light); border-radius: 10px; background: var(--surface); }.record-card dl { display: grid; gap: 8px; margin: 0; }.record-card dl div { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.record-card dt { color: var(--text-tertiary); font-size: 12px; }.record-card dd { margin: 0; color: var(--text-secondary); font-size: 13px; }.record-actions { display: flex; justify-content: flex-end; border-top: 1px solid var(--border-light); padding-top: 8px; } }
</style>
