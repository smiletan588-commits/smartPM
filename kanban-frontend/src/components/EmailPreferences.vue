<template>
  <div class="email-preferences">
    <button class="settings-trigger" title="邮件通知设置" aria-label="打开邮件通知设置" @click="open">
      <el-icon><Message /></el-icon>
    </button>
    <el-drawer v-model="visible" title="邮件通知" size="min(440px, 94vw)" append-to-body>
      <div class="settings-body" v-loading="loading">
        <section>
          <small>接收地址</small>
          <h3>工作邮箱</h3>
          <el-input v-model="form.email" type="email" placeholder="name@company.com" />
          <p v-if="preference.emailVerifiedAt" class="verified">已验证 · {{ formatTime(preference.emailVerifiedAt) }}</p>
          <p v-else class="muted">保存邮箱后需要验证，未验证前只发送站内通知。</p>
          <div class="button-row">
            <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
            <el-button v-if="form.email && !preference.emailVerifiedAt" :loading="verifying" @click="requestVerification">发送验证邮件</el-button>
            <el-button v-if="preference.emailVerifiedAt" :loading="testing" @click="testEmail">发送测试邮件</el-button>
          </div>
        </section>
        <section>
          <div class="switch-row"><div><strong>启用邮件通知</strong><span>总开关，默认关闭</span></div><el-switch v-model="form.emailEnabled" /></div>
          <div class="switch-row"><div><strong>任务指派</strong><span>被指派新任务时</span></div><el-switch v-model="form.assignmentEnabled" /></div>
          <div class="switch-row"><div><strong>评论提及</strong><span>评论中有人提到你时</span></div><el-switch v-model="form.mentionEnabled" /></div>
          <div class="switch-row"><div><strong>期限提醒</strong><span>即将到期、逾期或阻塞时</span></div><el-switch v-model="form.deadlineEnabled" /></div>
          <div class="switch-row"><div><strong>风险提醒</strong><span>高风险或风险分派时</span></div><el-switch v-model="form.riskEnabled" /></div>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { Message } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getNotificationPreferences, requestEmailVerification, saveNotificationPreferences, sendTestEmail } from '@/api/email'

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const verifying = ref(false)
const testing = ref(false)
const preference = ref({})
const form = reactive({ email: '', emailEnabled: false, assignmentEnabled: true, mentionEnabled: true, deadlineEnabled: true, riskEnabled: true })

function apply(value = {}) {
  preference.value = value
  Object.assign(form, {
    email: value.email || '', emailEnabled: Boolean(value.emailEnabled), assignmentEnabled: value.assignmentEnabled !== false,
    mentionEnabled: value.mentionEnabled !== false, deadlineEnabled: value.deadlineEnabled !== false, riskEnabled: value.riskEnabled !== false
  })
}
async function open() {
  visible.value = true
  loading.value = true
  try { apply((await getNotificationPreferences()).data?.data) }
  finally { loading.value = false }
}
async function save() {
  saving.value = true
  try { apply((await saveNotificationPreferences({ ...form, email: form.email.trim() || null })).data?.data); ElMessage.success('邮件设置已保存') }
  finally { saving.value = false }
}
async function requestVerification() {
  if (form.email.trim() !== (preference.value.email || '')) await save()
  verifying.value = true
  try { await requestEmailVerification(); ElMessage.success('验证邮件已进入发送队列，请在 24 小时内完成验证') }
  finally { verifying.value = false }
}
async function testEmail() {
  testing.value = true
  try { await sendTestEmail(); ElMessage.success('测试邮件已进入发送队列') }
  finally { testing.value = false }
}
function formatTime(value) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
</script>

<style scoped>
.settings-trigger { display:grid; place-items:center; width:34px; height:34px; padding:0; border:0; border-radius:9px; color:var(--text-secondary); background:transparent; cursor:pointer; }
.settings-trigger:hover { color:var(--brand-deep); background:var(--brand-light); }
.settings-body { display:grid; gap:18px; }
.settings-body section { padding:18px; border:1px solid var(--border-light); border-radius:var(--radius); background:var(--surface); }
.settings-body small { color:var(--brand); font-size:10px; font-weight:750; letter-spacing:.08em; }
.settings-body h3 { margin:5px 0 14px; font-size:17px; }
.muted,.verified { margin:9px 0 0; font-size:11px; line-height:1.6; }
.muted { color:var(--text-tertiary); }.verified { color:var(--success); }
.button-row { display:flex; flex-wrap:wrap; gap:8px; margin-top:14px; }
.switch-row { display:flex; align-items:center; justify-content:space-between; gap:18px; min-height:58px; border-bottom:1px solid var(--border-light); }
.switch-row:last-child { border-bottom:0; }
.switch-row div { display:grid; gap:3px; }.switch-row strong { font-size:13px; }.switch-row span { color:var(--text-tertiary); font-size:11px; }
</style>
