<script setup>
import { computed } from 'vue'
import { fmtMoney } from '../api.js'

const props = defineProps({ orders: { type: Array, default: () => [] } })
const rows = computed(() => props.orders.slice(0, 30))
</script>

<template>
  <div class="live-wrap" style="display: flex; flex-direction: column; height: 100%;">
    <div class="live-head">
      <span>订单号</span><span>渠道</span><span>商品</span><span style="text-align: right;">金额</span><span style="text-align: center;">状态</span>
    </div>
    <div style="flex: 1; overflow: hidden;">
      <div v-if="!rows.length" style="padding: 18px; text-align: center; color: #9db3d8; font-size: 12px;">
        等待实时订单推送…
      </div>
      <div v-for="o in rows" :key="o.orderNo" class="live-item">
        <span class="no">{{ o.orderNo }}</span>
        <span class="ch">{{ o.channel }}</span>
        <span class="prod" :title="o.productSummary">{{ o.productSummary }}</span>
        <span class="amt">¥{{ Number(o.amount).toLocaleString('zh-CN') }}</span>
        <span class="st">
          <span class="tag" :class="o.status === 'PAID' ? 'paid' : o.status === 'PENDING' ? 'pending' : 'refunded'">
            {{ o.statusLabel }}
          </span>
        </span>
      </div>
    </div>
  </div>
</template>
