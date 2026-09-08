// 统一 API 客户端（带 JWT 认证）
// 安全策略：access token 只存内存；refresh token 由服务端 HttpOnly Cookie 持有；
// 4xx 不重试；401 自动刷新令牌后重试一次；5xx 最多自动重试 2 次

class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

let accessToken = null
let currentUser = null
const authListeners = new Set()

export function getUser() {
  return currentUser
}

export function authHeaders() {
  return accessToken ? { Authorization: 'Bearer ' + accessToken } : {}
}

export function onAuthChange(fn) {
  authListeners.add(fn)
  return () => authListeners.delete(fn)
}

function notify() {
  authListeners.forEach(fn => fn(currentUser))
}

export async function tryRefresh() {
  try {
    // 浏览器自动携带 HttpOnly Cookie（zh_refresh）
    const resp = await fetch('/api/auth/refresh', { method: 'POST' })
    if (!resp.ok) return false
    const data = await resp.json()
    accessToken = data.accessToken
    currentUser = { username: data.username, role: data.role }
    notify()
    return true
  } catch (e) {
    return false
  }
}

export async function login(username, password) {
  const resp = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const data = await resp.json().catch(() => ({}))
  if (!resp.ok) {
    throw new ApiError(data.error || `登录失败 (${resp.status})`, resp.status)
  }
  accessToken = data.accessToken
  currentUser = { username: data.username, role: data.role }
  notify()
  return currentUser
}

export async function logout() {
  try {
    await request('/api/auth/logout', { method: 'POST' })
  } catch (e) { /* 忽略登出接口异常 */ }
  accessToken = null
  currentUser = null
  notify()
}

async function rawRequest(path, options, retried) {
  let resp
  try {
    resp = await fetch(path, { ...options, headers: { ...authHeaders(), ...(options.headers || {}) } })
  } catch (e) {
    throw new ApiError('网络连接失败，请检查后端服务是否在线', 0)
  }
  // 401 且已登录过：刷新令牌后重试一次
  if (resp.status === 401 && !retried && accessToken) {
    if (await tryRefresh()) return rawRequest(path, options, true)
    accessToken = null
    currentUser = null
    notify()
    throw new ApiError('登录已过期，请重新登录', 401)
  }
  if (resp.ok) return resp.json()
  if (resp.status < 500) {
    const data = await resp.json().catch(() => ({}))
    throw new ApiError(data.error || `请求失败 (${resp.status})`, resp.status)
  }
  const tries = (typeof retried === 'number' ? retried : 0)
  if (tries < 2) {
    await new Promise(r => setTimeout(r, 800))
    return rawRequest(path, options, tries + 1)
  }
  throw new ApiError(`服务暂时不可用 (${resp.status})`, resp.status)
}

async function request(path, options = {}) {
  return rawRequest(path, options, false)
}

export const api = {
  overview: () => request('/api/analytics/overview'),
  trend: (days = 30) => request(`/api/analytics/trend?days=${days}`),
  channel: () => request('/api/analytics/channel'),
  category: () => request('/api/analytics/category?limit=8'),
  product: () => request('/api/analytics/product?limit=10'),
  region: () => request('/api/analytics/region'),
  me: () => request('/api/auth/me'),
  listUsers: () => request('/api/auth/users'),
  createUser: (payload) => request('/api/auth/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }),
}

export function fmtMoney(v) {
  const n = Number(v) || 0
  if (n >= 1e8) return (n / 1e8).toFixed(2) + ' 亿'
  if (n >= 1e4) return (n / 1e4).toFixed(1) + ' 万'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

export function fmtNum(v) {
  return (Number(v) || 0).toLocaleString('zh-CN')
}
