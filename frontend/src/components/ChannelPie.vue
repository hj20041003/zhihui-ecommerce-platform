<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { fmtMoney } from '../api.js'

const props = defineProps({ data: { type: Array, default: () => [] } })
const el = ref(null)
let chart = null

const PALETTE = ['#00d4ff', '#2e6cff', '#7c6cff', '#ffc53d', '#00e676', '#ff8a3d', '#ff4d4f']

function render() {
  if (!chart) chart = echarts.init(el.value)
  chart.setOption({
    color: PALETTE,
    tooltip: {
      trigger: 'item', backgroundColor: 'rgba(8,20,50,0.9)', borderColor: 'rgba(0,212,255,0.3)',
      textStyle: { color: '#e8f1ff', fontSize: 12 },
      formatter: p => `${p.name}<br/>GMV ¥${fmtMoney(p.value)}<br/>占比 ${p.percent}%`,
    },
    legend: {
      orient: 'vertical', right: 4, top: 'middle', icon: 'circle',
      textStyle: { color: '#9db3d8', fontSize: 11 }, itemWidth: 8, itemHeight: 8, itemGap: 10,
    },
    series: [
      {
        type: 'pie', radius: ['32%', '62%'], center: ['38%', '52%'], roseType: 'area',
        itemStyle: { borderRadius: 5, borderColor: 'rgba(5,11,34,0.9)', borderWidth: 2 },
        label: { show: false },
        data: props.data.map(d => ({ name: d.channel, value: Number(d.gmv) })),
      },
      {
        type: 'pie', radius: ['0%', '24%'], center: ['38%', '52%'], silent: true,
        label: { show: false }, emphasis: { scale: false },
        itemStyle: { color: 'rgba(0,212,255,0.06)' },
        data: [{ value: 1, name: '' }],
      },
    ],
  })
}

onMounted(render)
watch(() => props.data, render, { deep: true })
onUnmounted(() => chart && chart.dispose())
</script>

<template><div ref="el" style="width: 100%; height: 100%"></div></template>
