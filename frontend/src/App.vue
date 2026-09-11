<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const stage = ref('Stage 1 · 基础业务系统')
const notify = () => ElMessage.info('完整运营页面将在后续阶段接入业务 API。')
</script>

<template>
  <main class="shell">
    <section class="hero">
      <p class="eyebrow">AI CROSS-BORDER ECOMMERCE OPERATIONS</p>
      <h1>AI 跨境电商运营 Agent</h1>
      <p class="subtitle">{{ stage }}</p>
      <el-button type="primary" @click="notify">查看 Stage 1 边界</el-button>
    </section>
    <section class="status-grid">
      <el-card shadow="hover"><template #header>后端</template>Spring Boot 3 · JWT · Flyway</el-card>
      <el-card shadow="hover"><template #header>数据</template>商品 · 库存 · 订单 · Demo 数据</el-card>
      <el-card shadow="hover"><template #header>前端</template>Vue 3 · TypeScript · Element Plus</el-card>
    </section>
  </main>
</template>
