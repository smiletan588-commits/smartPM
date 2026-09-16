<template>
  <div class="notification-center">
    <el-badge :value="unreadCount" :hidden="!unreadCount" :max="99">
      <button class="notification-trigger" aria-label="打开通知中心" @click="open">
        <el-icon><Bell /></el-icon>
      </button>
    </el-badge>

    <el-drawer v-model="visible" title="通知中心" size="min(420px, 92vw)" append-to-body>
      <template #header>
        <div class="drawer-heading"><div><small>协作动态</small><h2>通知中心</h2></div><el-button v-if="unreadCount" text @click="readAll">全部已读</el-button></div>
      </template>
      <div class="notification-list" v-loading="loading">
        <button v-for="item in notifications" :key="item.id" class="notification-item" :class="{ unread: !item.isRead }" @click="visit(item)">
          <span class="notification-mark"><el-icon><component :is="iconFor(item.type)" /></el-icon></span>
          <span class="notification-copy"><strong>{{ item.title }}</strong><span>{{ item.content }}</span><time>{{ formatTime(item.createdAt) }}</time></span>
          <i v-if="!item.isRead" aria-label="未读" />
        </button>
        <div v-if="!loading && !notifications.length" class="notification-empty">
          <el-icon><Check /></el-icon><strong>没有待处理通知</strong><span>新的任务指派、评论提醒和风险变化会出现在这里。</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, BellFilled, Calendar, ChatDotRound, Check, Link } from '@element-plus/icons-vue'
import { getUnreadNotificationCount, listNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/notification'

const router = useRouter()
const visible = ref(false)
const loading = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
let timer

async function refreshCount() {
  try { unreadCount.value = (await getUnreadNotificationCount()).data.data?.count || 0 } catch { /* 全局错误处理已提示 */ }
}
async function open() { visible.value = true; await load() }
async function load() {
  loading.value = true
  try { notifications.value = (await listNotifications()).data.data || []; await refreshCount() }
  finally { loading.value = false }
}
async function readAll() { await markAllNotificationsRead(); notifications.value.forEach(item => { item.isRead = true }); unreadCount.value = 0 }
async function visit(item) {
  if (!item.isRead) { await markNotificationRead(item.id); item.isRead = true; unreadCount.value = Math.max(0, unreadCount.value - 1) }
  visible.value = false
  if (item.projectId) router.push({ path: `/project/${item.projectId}`, query: item.taskId ? { task: item.taskId, tab: item.type === 'COMMENT_MENTIONED' ? 'comments' : undefined } : {} })
}
function iconFor(type) {
  if (type === 'COMMENT_MENTIONED') return ChatDotRound
  if (type === 'DUE_SOON' || type === 'TASK_OVERDUE') return Calendar
  if (type === 'TASK_BLOCKED') return Link
  return BellFilled
}
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '' }
function handleExternalRefresh() { refreshCount(); if (visible.value) load() }
onMounted(() => { refreshCount(); timer = window.setInterval(refreshCount, 60000); window.addEventListener('smartpm:notifications', handleExternalRefresh) })
onUnmounted(() => { window.clearInterval(timer); window.removeEventListener('smartpm:notifications', handleExternalRefresh) })
</script>

<style scoped>
.notification-trigger { display:grid; place-items:center; width:34px; height:34px; padding:0; border:0; border-radius:9px; color:var(--text-secondary); background:transparent; cursor:pointer; }
.notification-trigger:hover { color:var(--brand-deep); background:var(--brand-light); }
.drawer-heading { display:flex; align-items:center; justify-content:space-between; width:100%; padding-right:12px; }.drawer-heading small { color:var(--brand); font-size:11px; font-weight:700; letter-spacing:.08em; }.drawer-heading h2 { margin:3px 0 0; color:var(--text-primary); font-size:20px; }
.notification-list { min-height:240px; }.notification-item { position:relative; display:grid; grid-template-columns:38px 1fr 8px; gap:12px; width:100%; padding:15px 4px; border:0; border-bottom:1px solid var(--border-light); color:inherit; background:transparent; text-align:left; cursor:pointer; }.notification-item:hover { background:var(--bg-hover); }.notification-item.unread { background:linear-gradient(90deg,var(--brand-light),transparent 72%); }.notification-mark { display:grid; place-items:center; width:36px; height:36px; border-radius:10px; color:var(--brand-deep); background:var(--brand-light); }.notification-copy { display:grid; gap:4px; min-width:0; }.notification-copy strong { color:var(--text-primary); font-size:13px; }.notification-copy span { overflow:hidden; color:var(--text-secondary); font-size:12px; text-overflow:ellipsis; white-space:nowrap; }.notification-copy time { color:var(--text-tertiary); font-size:10px; }.notification-item>i { align-self:center; width:6px; height:6px; border-radius:50%; background:var(--brand); }.notification-empty { display:grid; justify-items:center; gap:8px; padding:64px 24px; color:var(--text-tertiary); text-align:center; }.notification-empty .el-icon { font-size:28px; color:var(--success); }.notification-empty strong { color:var(--text-primary); font-size:14px; }.notification-empty span { max-width:250px; font-size:12px; line-height:1.7; }
</style>
