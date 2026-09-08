<script setup>
import { onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'
import { api, fmtMoney, fmtNum, getUser, tryRefresh, onAuthChange, logout } from './api.js'
import { sseConnect } from './sse.js'
import KpiCards from './components/KpiCards.vue'
import TrendChart from './components/TrendChart.vue'
import ChannelPie from './components/ChannelPie.vue'
import CategoryBar from './components/CategoryBar.vue'
import ProductBar from './components/ProductBar.vue'
import RegionBar from './components/RegionBar.vue'
import LiveOrders from './components/LiveOrders.vue'
import Login from './components/Login.vue'
import UserAdmin from './components/UserAdmin.vue'

const user = ref(null)
const authReady = ref(false)
const showUserAdmin = ref(false)

const now = ref(new Date())
const totalGmv = ref(null)
const trendData = ref([])
const channelData = ref([])
const categoryData = ref([])
const productData = ref([])
const regionData = ref([])
const overview = ref(null)
const liveOrders = ref([])
const sseState = ref('connecting') // connecting | on | off
const errorMsg = ref('')

let clockTimer = null
let pollTimer = null
let stopSse = null

// 累计 GMV 数字滚动
const gmvDisplay = shallowRef(0)
let gmvAnim = null
function animateGmv(target) {
  if (target == null) return
  const from = gmvDisplay.value
  const start = performance.now()
  const dur = 900
  cancelAnimationFrame(gmvAnim)
  const step = (t) => {
    const p = Math.min((t - start) / dur, 1)
    gmvDisplay.value = from + (target - from) * (1 - Math.pow(1 - p, 3))
    if (p < 1) gmvAnim = requestAnimationFrame(step)
  }
  gmvAnim = requestAnimationFrame(step)
}

async function loadAll() {
  try {
    const tasks = [
      api.overview(), api.trend(30), api.channel(),
      api.category(), api.product(), api.region(),
    ]
    const [ov, trend, channel, category, product, region] = await Promise.all(tasks)
    overview.value = ov
    totalGmv.value = ov.totalGmv
    animateGmv(Number(ov.totalGmv) || 0)
    trendData.value = trend
    channelData.value = channel
    categoryData.value = category
    productData.value = product
    regionData.value = region
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e.message || '数据加载失败'
  }
}

function startSession() {
  if (pollTimer) clearInterval(pollTimer)
  loadAll()
  pollTimer = setInterval(loadAll, 30000)
  sseState.value = 'connecting'
  stopSse = sseConnect('/api/analytics/stream', {
    onOrders(list) {
      if (Array.isArray(list) && list.length) {
        liveOrders.value = [...list.reverse(), ...liveOrders.value].slice(0, 30)
        sseState.value = 'on'
      }
    },
    onAuthFailed() {
      user.value = null
    },
  })
  // SSE 是否成功建立 3 秒后未确认则标记
  setTimeout(() => { if (sseState.value === 'connecting' && !liveOrders.value.length) sseState.value = 'off' }, 4000)
}

function stopSession() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (stopSse) { stopSse(); stopSse = null }
  sseState.value = 'connecting'
}

async function onLoginSuccess(u) {
  user.value = u
  liveOrders.value = []
  startSession()
}

async function onLogout() {
  await logout() // api.js 内部会触发 onAuthChange -> user 置空
}

// 登录状态变化（含 401 被动登出）
const stopAuthWatch = onAuthChange((u) => {
  if (!u && user.value) {
    user.value = null
    stopSession()
    errorMsg.value = ''
  }
})

function pad(n) { return String(n).padStart(2, '0') }

onMounted(async () => {
  clockTimer = setInterval(() => { now.value = new Date() }, 1000)
  // 恢复会话：有 HttpOnly 刷新令牌则免登录
  const restored = await tryRefresh()
  authReady.value = true
  if (restored) {
    user.value = getUser()
    startSession()
  }
})

onUnmounted(() => {
  clearInterval(clockTimer)
  stopSession()
  cancelAnimationFrame(gmvAnim)
  stopAuthWatch()
  if (echarts) echarts.dispose
})

const weekCn = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const dateStr = () => `${now.value.getFullYear()}-${pad(now.value.getMonth() + 1)}-${pad(now.value.getDate())} ${weekCn[now.value.getDay()]}`
const timeStr = () => `${pad(now.value.getHours())}:${pad(now.value.getMinutes())}:${pad(now.value.getSeconds())}`
</script>

<template>
  <!-- 会话恢复中 -->
  <div v-if="!authReady" class="boot-screen">正在恢复登录会话…</div>

  <!-- 未登录：登录页 -->
  <Login v-else-if="!user" @success="onLoginSuccess" />

  <!-- 已登录：数据大屏 -->
  <div v-else class="dashboard">
    <header class="header">
      <div class="side">
        <div class="clock">{{ dateStr() }}<br />{{ timeStr() }}</div>
      </div>
      <h1 class="title">智辉电商全域运营数据分析平台</h1>
      <div class="side right">
        <div>全渠道累计 GMV</div>
        <div class="total-gmv">¥ {{ fmtMoney(gmvDisplay) }}</div>
      </div>
    </header>

    <div v-if="errorMsg" class="error-bar">⚠ {{ errorMsg }} —— 数据每 30 秒自动重试</div>

    <main class="main">
      <!-- 左列 -->
      <div class="col">
        <div class="panel" style="flex: 11;">
          <div class="panel-title">渠道销售占比<span class="panel-sub">GMV / 全渠道</span></div>
          <div class="chart"><ChannelPie :data="channelData" /></div>
        </div>
        <div class="panel" style="flex: 9;">
          <div class="panel-title">品类销售 TOP8<span class="panel-sub">近 30 天</span></div>
          <div class="chart"><CategoryBar :data="categoryData" /></div>
        </div>
      </div>

      <!-- 中列 -->
      <div class="col center">
        <KpiCards :overview="overview" />
        <div class="panel" style="flex: 1;">
          <div class="panel-title">近 30 天 GMV 与订单量趋势</div>
          <div class="chart"><TrendChart :data="trendData" /></div>
        </div>
        <div class="panel" style="flex: 0 0 30%;">
          <div class="panel-title">
            <span class="live-dot"></span>实时订单流
            <span class="panel-sub sse-state" :class="{ on: sseState === 'on' }">
              {{ sseState === 'on' ? '● 已连接' : sseState === 'connecting' ? '连接中…' : '● 已断开' }}
            </span>
          </div>
          <div class="live-list"><LiveOrders :orders="liveOrders" /></div>
        </div>
      </div>

      <!-- 右列 -->
      <div class="col">
        <div class="panel" style="flex: 11;">
          <div class="panel-title">热销商品 TOP10<span class="panel-sub">近 30 天</span></div>
          <div class="chart"><ProductBar :data="productData" /></div>
        </div>
        <div class="panel" style="flex: 9;">
          <div class="panel-title">地域销售分布<span class="panel-sub">GMV / 七大区</span></div>
          <div class="chart"><RegionBar :data="regionData" /></div>
        </div>
      </div>
    </main>

    <!-- 用户角标 -->
    <div class="user-bar">
      <span class="user-name">{{ user.username }}</span>
      <span class="role-badge" :class="user.role === 'ADMIN' ? 'admin' : 'viewer'">
        {{ user.role === 'ADMIN' ? '管理员' : '只读' }}
      </span>
      <button v-if="user.role === 'ADMIN'" class="bar-btn" @click="showUserAdmin = true">账号管理</button>
      <button class="bar-btn danger" @click="onLogout">退出</button>
    </div>

    <UserAdmin v-if="showUserAdmin" @close="showUserAdmin = false" />
  </div>
</template>
