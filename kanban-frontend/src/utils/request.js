import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/store/user'
import { AppError, createMessageDeduper, normalizeApiError } from '@/utils/appError'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

// 请求拦截器 — 自动附加 Token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

const shouldNotify = createMessageDeduper(1500)
let handlingUnauthorized = false

function notifyError(error) {
  if (error.config?.errorMode === 'silent' || error.originalError?.code === 'ERR_CANCELED') return
  const message = error.kind === 'NETWORK'
    ? '暂时无法连接服务器，请检查网络或稍后重试'
    : error.message
  if (shouldNotify(`${error.status || error.kind}:${message}`)) ElMessage.error(message)
}

async function handleUnauthorized(error) {
  const current = router.currentRoute.value
  const isAuthRequest = /\/user\/(login|register)$/.test(error.config?.url || '')
  if (isAuthRequest || current.path === '/login' || handlingUnauthorized) return
  handlingUnauthorized = true
  const userStore = useUserStore()
  userStore.logout()
  const redirect = current.fullPath && current.fullPath !== '/' ? current.fullPath : '/dashboard'
  if (shouldNotify('401:登录状态已失效，请重新登录')) ElMessage.error('登录状态已失效，请重新登录')
  try {
    await router.replace({ path: '/login', query: { redirect } })
  } finally {
    handlingUnauthorized = false
  }
}

// 第一层只负责解析后端统一响应，第二层集中展示错误。
request.interceptors.response.use(
  response => {
    if (response.data && response.data.code && response.data.code !== 200) {
      return Promise.reject(new AppError(response.data.msg || '请求处理失败', {
        code: response.data.code,
        status: response.data.code,
        kind: response.data.code === 403 ? 'PERMISSION' : 'BUSINESS',
        retryable: response.data.code >= 500,
        response,
        config: response.config
      }))
    }
    return response
  }, error => Promise.reject(error)
)

request.interceptors.response.use(response => response, async rawError => {
  const error = normalizeApiError(rawError)
  if (error.status === 401) await handleUnauthorized(error)
  else notifyError(error)
  return Promise.reject(error)
})

export default request
