<script setup>
import { computed } from 'vue'
import { fmtMoney, fmtNum } from '../api.js'

const props = defineProps({ overview: { type: Object, default: null } })

const cards = computed(() => {
  const ov = props.overview
  if (!ov) return []
  const t = ov.today || {}
  const cmp = ov.compare || {}
  const mk = (key) => {
    const v = cmp[key]
    if (v == null) return { dir: 'flat', text: '--' }
    if (v > 0) return { dir: 'up', text: `+${v}%` }
    if (v < 0) return { dir: 'down', text: `${v}%` }
    return { dir: 'flat', text: '0.0%' }
  }
  return [
    { label: '今日 GMV (¥)', value: fmtMoney(t.gmv), delta: mk('gmvDelta'), color: '#00d4ff' },
    { label: '今日订单量', value: fmtNum(t.orders), delta: mk('ordersDelta'), color: '#7c6cff' },
    { label: '客单价 (¥)', value: (Number(t.aov) || 0).toFixed(1), delta: mk('aovDelta'), color: '#ffc53d' },
    { label: '支付转化率', value: `${Number(t.conversion) || 0}%`, delta: null, color: '#00e676' },
    { label: '退款率', value: `${Number(t.refundRate) || 0}%`, delta: null, color: '#ff4d4f' },
  ]
})
</script>

<template>
  <div class="kpi-row">
    <div v-for="c in cards" :key="c.label" class="kpi-card">
      <div class="kpi-label">{{ c.label }}</div>
      <div class="kpi-value" :style="{ color: c.color }">{{ c.value }}</div>
      <div v-if="c.delta" class="kpi-delta" :class="c.delta.dir">
        <span class="arrow">{{ c.delta.dir === 'up' ? '▲' : c.delta.dir === 'down' ? '▼' : '■' }}</span>
        <span>较昨日 {{ c.delta.text }}</span>
      </div>
      <div v-else class="kpi-delta flat"><span class="vs">实时口径 · 已支付/总订单</span></div>
    </div>
  </div>
</template>
