<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteConversation } from './api'
import { pendingActions, type PendingAction } from './api'
const actions = ref<PendingAction[]>([])
async function refreshActions(id: number) { actions.value = await pendingActions(id) }
import { chat, conversations, dashboard, logout, messages, savedUser, type AgentResponse, type AuthResponse, type Conversation, type ConversationMessage, type DashboardSummary } from './api'
import LoginView from './components/LoginView.vue'
import ConversationSidebar from './components/ConversationSidebar.vue'
import OperationsSummary from './components/OperationsSummary.vue'
import ChatMessage from './components/ChatMessage.vue'
import ToolTracePanel from './components/ToolTracePanel.vue'
import ApprovalCard from './components/ApprovalCard.vue'
import PromptSuggestions from './components/PromptSuggestions.vue'

const user = ref<AuthResponse | null>(savedUser())
const router = useRouter()
let generation = 0
const summaryLoading = ref(false)
const approvalBusy = ref(false)
const deleting = ref(false)
const busy = computed(() => sending.value || pageLoading.value || approvalBusy.value || deleting.value)
const responseCache = new Map<number, AgentResponse>()
const hasResponse = ref(false)
const items = ref<Conversation[]>([])
const activeId = ref<number>()
const history = ref<ConversationMessage[]>([])
const summary = ref<DashboardSummary | null>(null)
const listLoading = ref(false)
const pageLoading = ref(false)
const sending = ref(false)
const draft = ref('')
const error = ref('')
const trace = ref<AgentResponse['toolCalls']>([])
const activeResponse = ref<AgentResponse>()
const scrollArea = ref<HTMLElement>()
const greeting = computed(() => {
  const hour = new Date().getHours()
  return hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
})

function time(value?: string) { return value ? new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '' }
async function refreshConversations() {
  listLoading.value = true
  try { items.value = (await conversations()).data } catch (e) { error.value = messageOf(e) } finally { listLoading.value = false }
}
async function refreshSummary() { summaryLoading.value = true; try { summary.value = (await dashboard()).data } catch { summary.value = null } finally { summaryLoading.value = false } }
async function loadConversation(id: number) {
  if (busy.value) return
  const current = ++generation
  pageLoading.value = true; error.value = ''; activeId.value = id; trace.value = []; activeResponse.value = undefined
  history.value = []; actions.value = []; hasResponse.value = false
  try { const [result, savedActions] = await Promise.all([messages(id), pendingActions(id)]); if (current !== generation) return; history.value = result.data; actions.value = savedActions; activeResponse.value = responseCache.get(id); trace.value = activeResponse.value?.toolCalls || []; hasResponse.value = !!activeResponse.value; await scrollBottom() } catch (e) { if (current === generation) error.value = messageOf(e) } finally { if (current === generation) pageLoading.value = false }
}
function newConversation() { if (busy.value) return; ++generation; activeId.value = undefined; history.value = []; actions.value = []; trace.value = []; activeResponse.value = undefined; error.value = ''; draft.value = ''; hasResponse.value = false }
async function send(text = draft.value) {
  const content = text.trim()
  if (!content || busy.value || content.length > 4000) return
  const current = generation
  draft.value = ''; error.value = ''; sending.value = true; trace.value = []; activeResponse.value = undefined
  history.value.push({ id: Date.now(), role: 'USER', content, createdAt: new Date().toISOString() }); await scrollBottom()
  try {
    const result = await chat(content, activeId.value)
    if (current !== generation) return
    activeId.value = result.conversationId
    history.value.push({ id: Date.now() + 1, role: 'ASSISTANT', content: result.answer, createdAt: new Date().toISOString() })
    trace.value = result.toolCalls || []; activeResponse.value = result; hasResponse.value = true; responseCache.set(result.conversationId, result)
    try { await refreshActions(result.conversationId) } catch (e) { error.value = '回复已保存，但操作记录加载失败。请重新打开会话重试。' }
    await refreshConversations(); await scrollBottom()
  } catch (e) { if (current === generation) { error.value = messageOf(e); history.value.pop(); draft.value = content } } finally { if (current === generation) sending.value = false }
}
async function removeConversation(id: number) {
  if (busy.value) return
  const current = generation
  deleting.value = true
  try {
    try {
      await ElMessageBox.confirm('删除后无法恢复会话及消息，待确认操作将自动拒绝。已完成操作和审计记录会保留。', '删除会话', {
        confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning'
      })
    } catch { return }
    if (current !== generation) return
    await deleteConversation(id)
    if (current !== generation) return
    responseCache.delete(id)
    items.value = items.value.filter(item => item.id !== id)
    if (activeId.value === id) {
      deleting.value = false
      newConversation()
      deleting.value = true
    }
    ElMessage.success('会话已删除')
    await refreshConversations()
  } catch (e) { if (current === generation) error.value = messageOf(e) }
  finally { deleting.value = false }
}
async function scrollBottom() { await nextTick(); if (scrollArea.value) scrollArea.value.scrollTop = scrollArea.value.scrollHeight }
function messageOf(e: unknown) { return e instanceof Error ? e.message : '暂时无法连接运营服务，请稍后重试。' }
function onApproval(status: string, message: string) { error.value = status === 'ERROR' ? message : ''; if (status === 'EXECUTED') refreshSummary() }
function signOut() { ++generation; logout(); user.value = null; responseCache.clear(); router.replace('/login') }
function composerKey(event: KeyboardEvent) { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); send() } }
function onLogin() { user.value = savedUser(); refreshConversations(); refreshSummary() }
onMounted(() => { window.addEventListener('commerce:logout', signOut); if (user.value) { refreshConversations(); refreshSummary() } })
onUnmounted(() => { ++generation; window.removeEventListener('commerce:logout', signOut) })
</script>

<template>
  <LoginView v-if="!user" @success="onLogin" />
  <main v-else class="app-shell">
    <header class="topbar">
      <div class="brand"><span class="brand-symbol">✦</span><span><strong>跨境电商智能运营</strong><small>AI 运营工作台</small></span></div>
      <div class="topbar-right"><span class="ready"><i></i> 智能体在线</span><span class="user-name">{{ user.email === 'demo@example.com' ? '演示账号' : (user.name || user.email) }}</span><button class="logout" @click="signOut">退出登录</button></div>
    </header>
    <div class="workspace">
      <ConversationSidebar :items="items" :active-id="activeId" :loading="listLoading" :disabled="busy" @select="loadConversation" @new-chat="newConversation" @delete="removeConversation" />
      <section class="center-panel">
        <div class="workspace-head"><div><p class="kicker">AI 运营助手</p><h1>今天想分析什么运营问题？</h1><p class="workspace-sub">通过 AI 分析商品、库存、订单与销售数据</p></div><div class="head-status"><span class="ready"><i></i> 数据已连接</span></div></div>
        <OperationsSummary :summary="summary" :loading="summaryLoading" />
        <div ref="scrollArea" class="chat-scroll">
          <div v-if="pageLoading" class="page-loading">正在加载会话……</div>
          <template v-else-if="!history.length">
            <div class="welcome"><div class="welcome-orb">✦</div><h2>{{ greeting }}<span>，从一个运营问题开始</span></h2><p>从真实业务数据出发，让分析有依据，让操作可确认。</p><PromptSuggestions @choose="send" /></div>
          </template>
          <template v-else>
            <ChatMessage v-for="item in history" :key="item.id" :role="item.role" :content="item.content" :time="time(item.createdAt)" />
            <ApprovalCard v-for="action in actions" :key="action.pendingActionId" :action="action" :disabled="sending || deleting" @changed="onApproval" @busy="approvalBusy = $event" />
            <div v-if="sending" class="thinking-row"><div class="avatar ai-avatar">✦</div><div class="thinking-bubble"><i></i><i></i><i></i> 正在查询业务数据并整理分析结果</div></div>
          </template>
        </div>
        <div v-if="error" class="error-banner">! {{ error }}</div>
        <form class="composer" @submit.prevent="send()"><textarea v-model="draft" rows="2" maxlength="4000" aria-label="运营问题" :disabled="busy" placeholder="输入你想分析的运营问题……" @keydown="composerKey" /><button class="send-button" aria-label="发送消息" :disabled="busy || !draft.trim()">{{ sending ? '…' : '↑' }}</button><small>Enter 发送 · Shift + Enter 换行 · {{ draft.length }}/4000</small></form>
      </section>
      <aside class="right-column"><ToolTracePanel :calls="trace" :busy="sending" :has-response="hasResponse" /><div class="right-note"><span>◎</span><div><strong>关键操作，由你确认</strong><p>修改业务数据前必须经你确认。历史会话仅保留消息，不保存工具执行过程。</p></div></div></aside>
    </div>
  </main>
</template>
