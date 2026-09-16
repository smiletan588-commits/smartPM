<template>
  <div class="login-page">
    <section class="login-layout">
      <div class="brand-side">
        <BrandMark />
        <div class="brand-copy">
          <p class="brand-kicker"><span aria-hidden="true"></span>清晰计划 · 稳定交付</p>
          <h1>
            <span>把项目做清楚，</span>
            <span>让协作<span class="brand-accent">更从容。</span></span>
          </h1>
          <p class="brand-description">从任务分工到关键路径，让团队在同一节奏里推进每一次交付。</p>
        </div>
      </div>

      <div class="form-side">
        <div class="form-wrapper" aria-live="polite">
          <h2>{{ activeTab === 'login' ? '欢迎回来' : '创建账户' }}</h2>
          <p class="subtitle">
            {{ activeTab === 'login' ? '登录以继续你的工作' : '注册后即可创建和管理项目' }}
          </p>

          <el-tabs v-model="activeTab" class="auth-tabs">
            <el-tab-pane label="登录" name="login" />
            <el-tab-pane label="注册" name="register" />
          </el-tabs>

          <el-alert v-if="authError" class="auth-error" :title="authError" type="error" :closable="false" show-icon role="alert" aria-live="assertive" />

          <template v-if="activeTab === 'login'">
            <div class="input-group" :class="{ invalid: loginErrors.username }">
              <label>用户名</label>
              <el-input ref="loginUsernameInput" v-model="loginForm.username" placeholder="输入用户名" size="large"
                :disabled="loading" @input="clearLoginError('username')" />
              <span v-if="loginErrors.username" class="field-error" role="alert">{{ loginErrors.username }}</span>
            </div>
            <div class="input-group" :class="{ invalid: loginErrors.password }">
              <label>密码</label>
              <el-input ref="loginPasswordInput" v-model="loginForm.password" type="password" show-password
                placeholder="输入密码" size="large" :disabled="loading" @input="clearLoginError('password')"
                @keydown="detectCapsLock" @keyup="detectCapsLock" @keyup.enter="handleLogin" />
              <span v-if="loginErrors.password" class="field-error" role="alert">{{ loginErrors.password }}</span>
              <span v-else-if="capsLockOn" class="field-hint">大写锁定已开启</span>
            </div>
            <el-button type="primary" size="large" :loading="loading" class="submit-btn"
              @click="handleLogin">
              登录
            </el-button>
          </template>

          <template v-else>
            <div class="input-group" :class="{ invalid: registerErrors.username }">
              <label>用户名</label>
              <el-input ref="registerUsernameInput" v-model="registerForm.username" placeholder="输入用户名" size="large"
                :disabled="loading" @input="clearRegisterError('username')" />
              <span v-if="registerErrors.username" class="field-error" role="alert">{{ registerErrors.username }}</span>
            </div>
            <div class="input-group">
              <label>昵称</label>
              <el-input v-model="registerForm.nickname" placeholder="给自己起个名字" size="large" :disabled="loading" />
            </div>
            <div class="input-group" :class="{ invalid: registerErrors.password }">
              <label>密码</label>
              <el-input ref="registerPasswordInput" v-model="registerForm.password" type="password" show-password
                placeholder="至少 8 位" size="large" :disabled="loading" @input="clearRegisterError('password')"
                @keydown="detectCapsLock" @keyup="detectCapsLock" @keyup.enter="handleRegister" />
              <span v-if="registerErrors.password" class="field-error" role="alert">{{ registerErrors.password }}</span>
              <span v-else-if="capsLockOn" class="field-hint">大写锁定已开启</span>
            </div>
            <el-button type="primary" size="large" :loading="loading" class="submit-btn"
              @click="handleRegister">
              注册
            </el-button>
          </template>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { nextTick, ref, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const activeTab = ref('login')
const loading = ref(false)
const authError = ref('')
const capsLockOn = ref(false)
const loginUsernameInput = ref(null)
const loginPasswordInput = ref(null)
const registerUsernameInput = ref(null)
const registerPasswordInput = ref(null)
const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '' })
const loginErrors = reactive({ username: '', password: '' })
const registerErrors = reactive({ username: '', password: '' })

function detectCapsLock(event) { capsLockOn.value = Boolean(event.getModifierState?.('CapsLock')) }
function clearLoginError(field) { loginErrors[field] = ''; authError.value = '' }
function clearRegisterError(field) { registerErrors[field] = ''; authError.value = '' }
function safeRedirect() {
  const target = String(route.query.redirect || '')
  return target.startsWith('/') && !target.startsWith('//') && target !== '/login' ? target : '/dashboard'
}

async function focusFirstError(errors, usernameInput, passwordInput) {
  await nextTick()
  if (errors.username) usernameInput.value?.focus()
  else if (errors.password) passwordInput.value?.focus()
}

async function handleLogin() {
  loginErrors.username = loginForm.username.trim() ? '' : '请输入用户名'
  loginErrors.password = loginForm.password ? '' : '请输入密码'
  if (loginErrors.username || loginErrors.password) return focusFirstError(loginErrors, loginUsernameInput, loginPasswordInput)
  loading.value = true
  authError.value = ''
  let focusPasswordAfterRequest = false
  try {
    await userStore.login(loginForm.username.trim(), loginForm.password)
    ElMessage.success('登录成功')
    router.replace(safeRedirect())
  } catch (error) {
    authError.value = error.kind === 'NETWORK'
      ? '暂时无法连接服务器，请检查网络或稍后重试'
      : (error.message === '用户名或密码错误' ? '用户名或密码错误，请重新输入' : error.message)
    loginForm.password = ''
    focusPasswordAfterRequest = true
  } finally {
    loading.value = false
  }
  if (focusPasswordAfterRequest) {
    await nextTick()
    loginPasswordInput.value?.focus()
  }
}

async function handleRegister() {
  registerErrors.username = registerForm.username.trim() ? '' : '请输入用户名'
  registerErrors.password = !registerForm.password ? '请输入密码' : (registerForm.password.length < 8 ? '密码至少需要 8 位' : '')
  if (registerErrors.username || registerErrors.password) return focusFirstError(registerErrors, registerUsernameInput, registerPasswordInput)
  loading.value = true
  authError.value = ''
  let focusUsernameAfterRequest = false
  try {
    await userStore.register(registerForm.username.trim(), registerForm.password, registerForm.nickname.trim())
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    registerForm.username = ''
    registerForm.password = ''
    registerForm.nickname = ''
  } catch (error) {
    authError.value = error.kind === 'NETWORK'
      ? '暂时无法连接服务器，请检查网络或稍后重试'
      : (error.message === '用户名已存在' ? '该用户名已被使用，请更换后重试' : error.message)
    if (error.message === '用户名已存在') {
      registerErrors.username = '该用户名已被使用'
      focusUsernameAfterRequest = true
    }
  } finally {
    loading.value = false
  }
  if (focusUsernameAfterRequest) {
    await nextTick()
    registerUsernameInput.value?.focus()
  }
}

watch(activeTab, () => {
  authError.value = ''
  capsLockOn.value = false
  Object.assign(loginErrors, { username: '', password: '' })
  Object.assign(registerErrors, { username: '', password: '' })
})
</script>

<style scoped>
.login-page {
  min-height: 100dvh;
  padding: clamp(18px, 4vw, 52px);
  background: var(--bg-base);
}
.login-layout {
  display: grid;
  grid-template-columns: minmax(320px, .85fr) minmax(460px, 1.15fr);
  width: min(1120px, 100%);
  min-height: calc(100dvh - clamp(36px, 8vw, 104px));
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}
.brand-side {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: clamp(28px, 5vw, 62px);
  border-right: 1px solid var(--border-light);
  background: var(--brand-soft);
}
.brand-copy {
  max-width: 390px;
  padding-bottom: clamp(52px, 9vh, 104px);
}
.brand-kicker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 22px;
  color: var(--brand-deep);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .12em;
}
.brand-kicker span {
  width: 28px;
  height: 2px;
  border-radius: 999px;
  background: var(--brand);
}
.brand-copy h1 {
  margin: 0;
  color: var(--text-primary);
  font-family: "HarmonyOS Sans SC", "MiSans", "PingFang SC", "Microsoft YaHei UI", var(--font-sans);
  font-size: clamp(36px, 3.55vw, 48px);
  font-weight: 650;
  line-height: 1.18;
  letter-spacing: -.035em;
}
.brand-copy h1 > span { display: block; white-space: nowrap; }
.brand-accent { color: var(--brand); }
.brand-description {
  max-width: 29em;
  margin: 24px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(36px, 8vw, 96px);
  background: var(--surface);
}
.form-wrapper { width: min(380px, 100%); }
.form-wrapper h2 { margin: 0; color: var(--text-primary); font-size: 28px; line-height: 1.2; letter-spacing: -.035em; }
.subtitle { margin: 9px 0 0; color: var(--text-tertiary); font-size: 13px; }
.auth-tabs { margin: 26px 0 12px; }
.auth-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
.auth-tabs :deep(.el-tabs__item) { height: 40px; padding: 0 22px 0 0; font-weight: 620; }
.auth-error { margin: 0 0 16px; }
.input-group { margin-bottom: 17px; }
.field-error,.field-hint { display:block; margin-top:6px; font-size:12px; line-height:1.4; }
.field-error { color:var(--danger); }
.field-hint { color:var(--warning); }
.input-group.invalid :deep(.el-input__wrapper) { box-shadow:0 0 0 1px var(--danger) inset; }
.submit-btn { width: 100%; height: 44px; margin-top: 5px; }

@media (max-width: 768px) {
  .login-page { padding: 0; }
  .login-layout { grid-template-columns: 1fr; min-height: 100dvh; border: 0; border-radius: 0; }
  .brand-side { min-height: 250px; padding: 26px 24px 30px; border-right: 0; border-bottom: 1px solid var(--border-light); }
  .brand-copy { max-width: none; padding: 40px 0 0; }
  .brand-kicker { margin-bottom: 13px; font-size: 9px; }
  .brand-kicker span { width: 20px; }
  .brand-copy h1 { font-size: clamp(27px, 8.2vw, 34px); letter-spacing: -.025em; }
  .brand-description { max-width: 26em; margin-top: 14px; font-size: 11px; line-height: 1.65; }
  .form-side { align-items: flex-start; padding: 38px 24px 48px; }
}
</style>
