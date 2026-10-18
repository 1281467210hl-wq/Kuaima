import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 后端时间字段可能带有微秒（例如 2026-09-24 11:18:03.979582）。
// Web 端统一展示到秒，避免各页面直接展示不同精度的原始值。
const normalizeDateTime = (value) => {
  if (typeof value !== 'string') return value
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})(?:\.\d+)?([Zz]|[+-]\d{2}:?\d{2})?$/)
  if (!match) return value
  return `${match[1]} ${match[2]}`
}

const normalizeDateTimes = (value) => {
  if (Array.isArray(value)) return value.map(normalizeDateTimes)
  if (value && typeof value === 'object') {
    Object.keys(value).forEach((key) => {
      value[key] = normalizeDateTimes(value[key])
    })
    return value
  }
  return normalizeDateTime(value)
}

const BASE_URL = '/api'

const request = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：注入 token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('admin_token')
    if (token && !config.url.includes('/admin/auth/login')) {
      config.headers.Authorization = 'Bearer ' + token
    }
    return config
  },
  (err) => Promise.reject(err)
)

// 响应拦截器：解析 Result 结构
request.interceptors.response.use(
  (resp) => {
    const body = resp.data
    normalizeDateTimes(body)
    // 后端统一 Result 格式：{ code, message, data, page, total }
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        return body // 原样返回，业务方自行解构 data/page/total
      }
      if (body.code === 401) {
        localStorage.removeItem('admin_token')
        if (router.currentRoute.value.path !== '/login') {
          ElMessage.error('登录已过期，请重新登录')
          router.push('/login')
        }
        return Promise.reject(Object.assign(new Error(body.message || '未授权'), { notified: true }))
      }
      ElMessage.error(body.message || '请求失败')
      // notified：拦截器已弹过提示，页面 catch 不必再弹一次（避免出现「请求失败 (400)」+ 英文 axios 文案两条）
      return Promise.reject(Object.assign(new Error(body.message || '请求失败'), { notified: true }))
    }
    // 非标准响应（例如代理层直接返回）
    return resp.data
  },
  (err) => {
    if (err.response) {
      const status = err.response.status
      if (status === 401) {
        localStorage.removeItem('admin_token')
        if (router.currentRoute.value.path !== '/login') {
          ElMessage.error('登录已过期，请重新登录')
          router.push('/login')
        }
      } else if (status === 403) {
        ElMessage.error(err.response.data?.message || '仅管理员可操作')
      } else if (status === 400) {
        ElMessage.error(err.response.data?.message || '请求参数错误')
      } else {
        ElMessage.error(`请求失败 (${status})`)
      }
      err.notified = true
    } else if (err.message?.includes('Network Error')) {
      // 后端未启动时静默一些，不频繁弹窗
      console.warn('[API] 后端服务未启动: ' + err.config?.url)
    } else {
      ElMessage.error(err.message || '网络异常')
      err.notified = true
    }
    return Promise.reject(err)
  }
)

export default request

export { BASE_URL }
