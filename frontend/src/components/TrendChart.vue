<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Array, default: () => [] } })
const el = ref(null)
let chart = null

function render() {
  if (!chart) chart = echarts.init(el.value)
  const dates = props.data.map(d => d.date)
  const gmv = props.data.map(d => d.gmv)
  const orders = props.data.map(d => d.orders)
  chart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(8,20,50,0.9)', borderColor: 'rgba(0,212,255,0.3)', textStyle: { color: '#e8f1ff', fontSize: 12 } },
    legend: { data: ['GMV(¥)', '订单量'], textStyle: { color: '#9db3d8', fontSize: 11 }, top: 0, right: 10 },
    grid: { left: 54, right: 50, top: 30, bottom: 26 },
    xAxis: {
      type: 'category', data: dates, boundaryGap: true,
      axisLine: { lineStyle: { color: 'rgba(0,212,255,0.25)' } },
      axisLabel: { color: '#9db3d8', fontSize: 10, interval: 4 },
    },
    yAxis: [
      {
        type: 'value', name: 'GMV',
        nameTextStyle: { color: '#9db3d8', fontSize: 10 },
        axisLabel: { color: '#9db3d8', fontSize: 10, formatter: v => v >= 10000 ? (v / 10000) + '万' : v },
        splitLine: { lineStyle: { color: 'rgba(0,212,255,0.10)' } },
      },
      {
        type: 'value', name: '订单',
        nameTextStyle: { color: '#9db3d8', fontSize: 10 },
        axisLabel: { color: '#9db3d8', fontSize: 10 },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: 'GMV(¥)', type: 'bar', data: gmv, barMaxWidth: 14,
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#00d4ff' }, { offset: 1, color: 'rgba(46,108,255,0.25)' },
          ]),
        },
      },
      {
        name: '订单量', type: 'line', yAxisIndex: 1, data: orders, smooth: true,
        symbol: 'circle', symbolSize: 5,
        lineStyle: { color: '#ffc53d', width: 2 },
        itemStyle: { color: '#ffc53d' },
        areaStyle: { color: 'rgba(255,197,61,0.08)' },
      },
    ],
  })
}

onMounted(render)
watch(() => props.data, render, { deep: true })
onUnmounted(() => chart && chart.dispose())
</script>

<template><div ref="el" style="width: 100%; height: 100%"></div></template>
