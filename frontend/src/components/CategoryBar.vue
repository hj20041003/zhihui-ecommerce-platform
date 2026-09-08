<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { fmtMoney } from '../api.js'

const props = defineProps({ data: { type: Array, default: () => [] } })
const el = ref(null)
let chart = null

function render() {
  if (!chart) chart = echarts.init(el.value)
  const rows = [...props.data].reverse() // 倒序让 TOP1 在最上
  chart.setOption({
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(8,20,50,0.9)', borderColor: 'rgba(0,212,255,0.3)',
      textStyle: { color: '#e8f1ff', fontSize: 12 },
      formatter: ps => {
        const p = ps[0]
        const row = props.data[props.data.length - 1 - p.dataIndex] || {}
        return `${p.name}<br/>GMV ¥${fmtMoney(p.value)}<br/>件数 ${row.qty ?? '-'}`
      },
    },
    grid: { left: 76, right: 40, top: 8, bottom: 4, containLabel: false },
    xAxis: { type: 'value', axisLabel: { show: false }, splitLine: { show: false } },
    yAxis: {
      type: 'category',
      data: rows.map(d => d.category),
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#c6d8f5', fontSize: 11 },
    },
    series: [{
      type: 'bar', data: rows.map(d => Number(d.gmv)), barMaxWidth: 12,
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: 'rgba(124,108,255,0.35)' }, { offset: 1, color: '#7c6cff' },
        ]),
      },
      label: { show: true, position: 'right', color: '#9db3d8', fontSize: 10, formatter: p => fmtMoney(p.value) },
    }],
  })
}

onMounted(render)
watch(() => props.data, render, { deep: true })
onUnmounted(() => chart && chart.dispose())
</script>

<template><div ref="el" style="width: 100%; height: 100%"></div></template>
