<template>
  <div class="app-shell" :class="{ 'is-collapsed': collapsed }">
    <button v-if="mobileOpen" class="shell-backdrop" aria-label="关闭导航" @click="mobileOpen = false" />
    <aside class="shell-sidebar" :class="{ 'is-mobile-open': mobileOpen }">
      <div class="sidebar-brand">
        <BrandMark :compact="collapsed" />
        <button class="icon-button collapse-button" :aria-label="collapsed ? '展开导航' : '收起导航'" @click="toggleCollapsed">
          <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
        </button>
        <button class="icon-button mobile-close" aria-label="关闭导航" @click="mobileOpen = false"><el-icon><Close /></el-icon></button>
      </div>

      <nav class="sidebar-nav" aria-label="主导航">
        <RouterLink v-for="item in primaryNav" :key="item.to" :to="item.to" class="nav-item" :title="collapsed ? item.label : undefined">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
        </RouterLink>
        <template v-if="projectId">
          <div class="nav-separator"><span>当前项目</span></div>
          <RouterLink v-for="item in projectNav" :key="String(item.to)" :to="item.to" class="nav-item" :title="collapsed ? item.label : undefined">
            <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
          </RouterLink>
        </template>
      </nav>

      <div class="sidebar-user">
        <EmailPreferences />
        <NotificationCenter />
        <span class="user-avatar">{{ userInitial }}</span>
        <div class="user-copy"><strong>{{ userStore.userInfo?.username || '用户' }}</strong><span>{{ userStore.systemRole === 'ADMIN' ? '系统管理员' : '团队成员' }}</span></div>
        <button class="icon-button logout-button" title="退出登录" aria-label="退出登录" @click="handleLogout"><el-icon><SwitchButton /></el-icon></button>
      </div>
    </aside>

    <div class="shell-body">
      <header class="mobile-topbar">
        <button class="icon-button" aria-label="打开导航" @click="mobileOpen = true"><el-icon><Menu /></el-icon></button>
        <BrandMark />
        <div class="mobile-actions"><EmailPreferences /><NotificationCenter /></div>
      </header>
      <main class="shell-main"><slot /></main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Close, DataAnalysis, Delete, Document, Expand, Fold, Grid, Menu, Setting, SwitchButton, Tickets, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import BrandMark from './BrandMark.vue'
import EmailPreferences from './EmailPreferences.vue'
import NotificationCenter from './NotificationCenter.vue'

const props = defineProps({ projectId: { type: [String, Number], default: null }, projectName: { type: String, default: '' } })
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(localStorage.getItem('smartpm.sidebar.collapsed') === '1')
const mobileOpen = ref(false)
let notificationSocket
let notificationReconnectTimer
const userInitial = computed(() => userStore.userInfo?.username?.slice(0, 1).toUpperCase() || 'U')
const primaryNav = computed(() => {
  const items = [
    { to: '/dashboard', label: '我的工作台', icon: Grid },
    { to: '/analytics', label: '项目洞察', icon: DataAnalysis },
    { to: '/recycle-bin', label: '回收站', icon: Delete }
  ]
  if (userStore.systemRole === 'ADMIN') items.push({ to: '/admin/users', label: '系统管理', icon: UserFilled })
  return items
})
const projectNav = computed(() => [
  { to: `/project/${props.projectId}`, label: '任务看板', icon: Tickets },
  { to: `/project/${props.projectId}/product-lab`, label: '产品共创', icon: ChatDotRound },
  { to: { path: `/project/${props.projectId}/wiki`, query: props.projectName ? { projectName: props.projectName } : {} }, label: '文档中心', icon: Document },
  { to: `/project/${props.projectId}/manage`, label: '项目管理', icon: Setting }
])
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem('smartpm.sidebar.collapsed', collapsed.value ? '1' : '0')
}
function handleLogout() { userStore.logout(); router.push('/login') }
function connectNotificationSocket() {
  if (!userStore.token || notificationSocket?.readyState === WebSocket.OPEN || notificationSocket?.readyState === WebSocket.CONNECTING) return
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  notificationSocket = new WebSocket(`${protocol}://${location.host}/ws/notifications?token=${encodeURIComponent(userStore.token)}`)
  notificationSocket.onmessage = event => {
    try {
      if (JSON.parse(event.data).type === 'NOTIFICATION_UPDATED') window.dispatchEvent(new CustomEvent('smartpm:notifications'))
    } catch { /* ignore malformed server event */ }
  }
  notificationSocket.onclose = () => { notificationSocket = null; notificationReconnectTimer = window.setTimeout(connectNotificationSocket, 3000) }
}
function disconnectNotificationSocket() {
  window.clearTimeout(notificationReconnectTimer)
  if (notificationSocket) { notificationSocket.onclose = null; notificationSocket.close(); notificationSocket = null }
}
watch(() => route.fullPath, () => { mobileOpen.value = false })
onMounted(connectNotificationSocket)
onUnmounted(disconnectNotificationSocket)
</script>

<style scoped>
.app-shell { min-height: 100dvh; background: var(--bg-base); }
.shell-sidebar { position: fixed; inset: 0 auto 0 0; z-index: 30; display: flex; flex-direction: column; width: 224px; padding: 18px 14px; border-right: 1px solid var(--border-light); background: var(--surface); transition: transform 200ms ease, width 200ms ease; }
.sidebar-brand { display: flex; align-items: center; min-height: 42px; padding: 0 6px 16px; border-bottom: 1px solid var(--border-light); }
.icon-button { display: inline-grid; place-items: center; width: 34px; height: 34px; padding: 0; border: 0; border-radius: 8px; color: var(--text-tertiary); background: transparent; cursor: pointer; }
.icon-button:hover { color: var(--text-primary); background: var(--bg-hover); }
.collapse-button { margin-left: auto; }
.mobile-close { display: none; margin-left: auto; }
.sidebar-nav { display: grid; gap: 4px; padding: 18px 0; }
.nav-item { display: flex; align-items: center; gap: 12px; min-height: 42px; padding: 0 12px; border-radius: 9px; color: var(--text-secondary); text-decoration: none; font-weight: 590; white-space: nowrap; overflow: hidden; transition: color 160ms ease, background-color 160ms ease; }
.nav-item .el-icon { flex: 0 0 auto; font-size: 18px; }
.nav-item:hover { color: var(--text-primary); background: var(--surface-strong); }
.nav-item.router-link-exact-active { color: var(--brand-deep); background: var(--brand-light); }
.nav-separator { display: flex; align-items: center; min-height: 34px; margin-top: 12px; padding: 0 12px; color: var(--text-tertiary); font-size: 11px; font-weight: 650; }
.sidebar-user { display: flex; align-items: center; gap: 10px; margin-top: auto; padding: 14px 6px 2px; border-top: 1px solid var(--border-light); }
.user-avatar,.mobile-avatar { display: grid; place-items: center; flex: 0 0 auto; width: 34px; height: 34px; border-radius: 50%; color: #f8faff; background: var(--brand); font-size: 12px; font-weight: 700; }
.user-copy { display: grid; min-width: 0; flex: 1; }
.user-copy strong { overflow: hidden; color: var(--text-primary); font-size: 13px; text-overflow: ellipsis; }
.user-copy span { color: var(--text-tertiary); font-size: 11px; }
.logout-button { flex: 0 0 auto; }
.shell-body { min-width: 0; min-height: 100dvh; margin-left: 224px; transition: margin-left 200ms ease; }
.shell-main { min-width: 0; min-height: 100dvh; }
.mobile-topbar { display: none; }
.shell-backdrop { position: fixed; inset: 0; z-index: 35; border: 0; background: rgba(21,31,50,.38); }
.is-collapsed .shell-sidebar { width: 72px; }
.is-collapsed .shell-body { margin-left: 72px; }
.is-collapsed .sidebar-brand { justify-content: center; padding-inline: 0; }
.is-collapsed .collapse-button { position: absolute; left: 54px; top: 23px; width: 26px; height: 26px; border: 1px solid var(--border); background: var(--surface); box-shadow: var(--shadow-xs); }
.is-collapsed .nav-item { justify-content: center; padding-inline: 0; }
.is-collapsed .nav-item span,.is-collapsed .nav-separator span,.is-collapsed .user-copy,.is-collapsed .logout-button { display: none; }
.mobile-actions { display:none; }
.is-collapsed .sidebar-user { justify-content: center; padding-inline: 0; }
.is-collapsed .sidebar-user .user-avatar { display:none; }
@media (min-width: 768px) and (max-width: 1100px) {
  .shell-sidebar { width: 72px; }
  .shell-body { margin-left: 72px; }
  .sidebar-brand { justify-content: center; padding-inline: 0; }
  .sidebar-brand :deep(.brand-name) { display: none; }
  .collapse-button { display: none; }
  .nav-item { justify-content: center; padding-inline: 0; }
  .nav-item span,.nav-separator span,.user-copy,.logout-button { display: none; }
  .sidebar-user { justify-content: center; padding-inline: 0; }
  .sidebar-user .user-avatar { display:none; }
}
@media (max-width: 767px) {
  .shell-sidebar { z-index: 40; width: min(286px, 86vw); transform: translateX(-104%); box-shadow: var(--shadow-lg); }
  .shell-sidebar.is-mobile-open { transform: translateX(0); }
  .collapse-button { display: none; }
  .mobile-close { display: inline-grid; }
  .shell-body,.is-collapsed .shell-body { margin-left: 0; }
  .is-collapsed .shell-sidebar { width: min(286px, 86vw); }
  .is-collapsed .sidebar-brand { justify-content: flex-start; padding: 0 6px 16px; }
  .is-collapsed .nav-item { justify-content: flex-start; padding: 0 12px; }
  .is-collapsed .nav-item span,.is-collapsed .nav-separator span,.is-collapsed .user-copy,.is-collapsed .logout-button { display: initial; }
  .is-collapsed .sidebar-user { justify-content: flex-start; padding: 14px 6px 2px; }
  .is-collapsed .sidebar-user .user-avatar { display:grid; }
  .mobile-topbar { position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: 40px 1fr 40px; align-items: center; min-height: 60px; padding: 0 14px; border-bottom: 1px solid var(--border-light); background: rgba(251,252,254,.96); }
  .mobile-topbar :deep(.brand-lockup) { justify-self: center; }
  .mobile-actions { display:flex; justify-self:end; }
  .shell-main { min-height: calc(100dvh - 60px); }
}
</style>
