<script setup lang="ts">
const emit = defineEmits<{ choose: [prompt: string] }>()
const prompts = [
  { title: '分析当前低库存商品', detail: '及时发现缺货风险', message: '有哪些低库存商品？' },
  { title: '总结近 7 日销售情况', detail: '了解近期经营表现', message: '最近 7 天销售情况怎么样？' },
  { title: '给出补货建议', detail: '结合库存与销量综合分析', message: '找出低库存商品，并结合最近 7 天销量给出补货建议。' },
  { title: '分析 SKU-A001', detail: '查看商品库存与近期销量', message: 'SKU-A001 当前库存怎么样？最近卖得好吗？' }
]
</script>
<template><div class="prompt-grid"><button v-for="prompt in prompts" :key="prompt.title" @click="emit('choose', prompt.message)"><span class="prompt-icon">↗</span><span class="prompt-copy"><strong>{{ prompt.title }}</strong><small>{{ prompt.detail }}</small></span></button></div></template>
