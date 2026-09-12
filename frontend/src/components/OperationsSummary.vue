<script setup lang="ts">
import type { DashboardSummary } from '../api'
defineProps<{ summary: DashboardSummary | null; loading: boolean }>()
function money(value?: number) { return value == null ? '—' : Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 }) }
</script>
<template>
  <section class="summary-strip">
    <div class="summary-heading"><span>运营概览</span><small>多币种汇总，未折算汇率</small></div>
    <div v-if="loading" class="summary-loading">正在加载运营数据……</div>
    <template v-else-if="summary">
      <div class="metric"><span class="metric-icon purple">◒</span><span><small>商品总数</small><strong>{{ summary.productCount }}</strong></span></div>
      <div class="metric"><span class="metric-icon amber">⌁</span><span><small>低库存</small><strong>{{ summary.lowStockCount }}</strong></span></div>
      <div class="metric"><span class="metric-icon blue">◷</span><span><small>近 7 日订单</small><strong>{{ summary.recent7DaysOrderCount }}</strong></span></div>
      <div class="metric" title="多币种汇总，未折算汇率；可通过销售工具查询具体币种。"><span class="metric-icon green">Σ</span><span><small>近 7 日销售额</small><strong>{{ money(summary.recent7DaysRevenue) }}</strong></span></div>
    </template>
    <div v-else class="summary-loading">运营数据暂时不可用</div>
  </section>
</template>
