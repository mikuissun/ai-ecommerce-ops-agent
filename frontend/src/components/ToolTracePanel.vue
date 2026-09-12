<script setup lang="ts">
import type { ToolCall } from '../api'
defineProps<{ calls: ToolCall[]; busy: boolean; hasResponse?: boolean }>()
const safeArgs = (args: Record<string, unknown>) => Object.fromEntries(Object.entries(args || {}).filter(([key]) => ['sku','newPrice','days','keyword','marketplace','status','startDate','endDate'].includes(key)))
const toolLabels: Record<string, string> = { get_product: '查询商品详情', search_products: '搜索商品', get_inventory: '查询商品库存', list_low_stock_products: '查询低库存商品', query_orders: '查询订单', get_sales_summary: '查询销售汇总', update_product_price: '申请修改商品价格' }
const argumentLabels: Record<string, string> = { sku: '商品编码', newPrice: '调整后价格', days: '统计天数', keyword: '关键词', marketplace: '销售平台', status: '状态', startDate: '开始日期', endDate: '结束日期' }
function pretty(value: unknown) { return typeof value === 'object' ? JSON.stringify(value) : String(value) }
</script>
<template>
  <aside class="trace-panel" :class="{ 'trace-idle': !calls.length && !busy }">
    <div class="trace-title"><span><i class="pulse"></i> 智能体执行过程</span><span v-if="busy" class="trace-live">处理中</span></div>
    <div v-if="busy" class="trace-thinking"><span class="typing"><i></i><i></i><i></i></span> 正在分析你的问题</div>
    <div v-if="!calls.length && !busy" class="trace-empty"><span>◎</span><p>{{ hasResponse ? '本次回答无需调用工具。' : '发送消息后，这里会展示智能体的工具调用过程。' }}</p></div>
    <div v-for="(call, index) in calls" :key="`${call.iteration}-${index}`" class="trace-item">
      <div class="trace-line"><span class="trace-node" :class="{ failed: !call.success }"></span><span class="trace-iteration">第 {{ call.iteration }} 轮</span><span class="trace-status" :class="{ failed: !call.success }">{{ !call.success ? '执行失败' : call.toolName === 'update_product_price' ? '待人工确认' : '执行成功' }}</span></div>
      <strong>{{ call.toolName }}</strong><p class="tool-description">{{ toolLabels[call.toolName] || '业务数据工具' }}</p>
      <div v-if="Object.keys(safeArgs(call.arguments)).length" class="trace-args"><span v-for="(value, key) in safeArgs(call.arguments)" :key="key">{{ argumentLabels[key] || key }} <b>{{ pretty(value) }}</b></span></div>
    </div>
    <div v-if="calls.length && !busy" class="trace-final"><span>✓</span> 已生成最终运营回答</div>
  </aside>
</template>
