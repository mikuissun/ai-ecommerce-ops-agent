<script setup lang="ts">
import { ref } from 'vue'
import { login } from '../api'
import { useRouter } from 'vue-router'
const router = useRouter()

const emit = defineEmits<{ success: [] }>()
const email = ref('demo@example.com')
const password = ref('password')
const loading = ref(false)
const error = ref('')

async function submit() {
  if (loading.value) return
  loading.value = true; error.value = ''
  try { await login(email.value, password.value); emit('success'); await router.replace('/') }
  catch (e) { error.value = e instanceof Error ? e.message : '登录失败，请检查服务是否启动' }
  finally { loading.value = false }
}
</script>
<template>
  <main class="login-shell">
    <section class="login-art">
      <div class="brand-mark">✦</div>
      <p class="eyebrow">跨境电商智能运营</p>
      <h1>让运营决策<br><em>有据可依</em></h1>
      <p>连接商品、库存、订单与销售数据，让 AI 辅助分析，让每一次关键操作都由你掌控。</p>
      <div class="login-orbit"><span>库存洞察</span><span>销售分析</span><span>人工确认</span></div>
    </section>
    <section class="login-card">
      <div class="mini-brand"><span class="brand-mark">✦</span><span>跨境电商智能运营</span></div>
      <p class="kicker">AI 运营工作台</p>
      <h2>欢迎回来</h2>
      <p class="muted">登录后，开始你的智能运营分析。</p>
      <form @submit.prevent="submit">
        <label>邮箱<input v-model="email" type="email" autocomplete="username" required /></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" required /></label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="primary-button" :disabled="loading">{{ loading ? '正在登录……' : '进入工作台' }} <span>↗</span></button>
      </form>
      <p class="login-foot">演示账号：demo@example.com · 密码：password</p>
    </section>
  </main>
</template>
