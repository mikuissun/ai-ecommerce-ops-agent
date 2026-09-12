<script setup lang="ts">
import { computed, ref } from 'vue'
import { approve, reject, pendingActions, type PendingAction, type ApprovalResponse } from '../api'
const props = defineProps<{ action: PendingAction; disabled?: boolean }>()
const emit = defineEmits<{ changed: [status: string, message: string]; busy: [value: boolean] }>()
const statusLabels: Record<string, string> = { PENDING: '操作待确认', APPROVED: '已确认', REJECTED: '已拒绝', EXECUTED: '操作已执行', FAILED: '执行失败' }
const busy = ref(false)
const state = computed(() => props.action.status)
const error = ref('')
const resultValue = ref<ApprovalResponse['result']>()
const args = computed(() => props.action.arguments)
async function decide(kind: 'approve' | 'reject') {
  if (busy.value || props.disabled || state.value !== 'PENDING') return
  busy.value = true; error.value = ''; emit('busy', true)
  try {
    const result = kind === 'approve' ? await approve(props.action.pendingActionId) : await reject(props.action.pendingActionId)
    props.action.status = result.status; resultValue.value = result.result
    error.value = result.errorMessage || ''
    emit('changed', result.status, result.errorMessage || (result.status === 'EXECUTED' ? '价格修改成功。' : '已拒绝此次操作。'))
  } catch (e) { error.value = e instanceof Error ? e.message : '操作确认失败，请重新打开会话查看最新状态。'; emit('changed', 'ERROR', error.value) }
  finally { busy.value = false; emit('busy', false) }
}
</script>
<template>
  <section class="approval-card" :class="state.toLowerCase()">
    <div class="approval-kicker">⚠ {{ statusLabels[state] || '操作状态待确认' }}</div><h3>修改商品价格</h3>
    <p class="approval-copy">{{ state === 'PENDING' ? 'AI 已准备此操作。确认后才会真正修改业务数据。' : state === 'EXECUTED' ? '价格已修改，执行结果已记录到审计日志。' : state === 'REJECTED' ? '你已拒绝此次操作，业务数据未发生变化。' : '操作未完成，请查看错误信息后再发起请求。' }}</p>
    <div class="price-compare"><div><small>商品编码</small><strong>{{ args.sku || '—' }}</strong></div><div><small>调整后价格</small><strong>{{ args.newPrice ?? '—' }}</strong></div></div>
    <p v-if="resultValue" class="approval-result">{{ resultValue.beforePrice }} → {{ resultValue.afterPrice }}</p>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <div v-if="state === 'PENDING'" class="approval-actions"><button class="reject-button" :disabled="busy || disabled" @click="decide('reject')">拒绝</button><button class="approve-button" :disabled="busy || disabled" @click="decide('approve')">{{ busy ? '正在处理……' : '确认执行' }}</button></div>
    <div v-else class="approval-result">{{ state === 'EXECUTED' ? '✓ 操作已执行' : state === 'REJECTED' ? '已拒绝此次操作' : state === 'ERROR' ? '操作状态更新失败' : (statusLabels[state] || '操作状态待确认') }}</div>
  </section>
</template>
