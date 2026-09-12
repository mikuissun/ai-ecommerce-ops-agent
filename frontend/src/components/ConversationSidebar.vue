<script setup lang="ts">
import { computed } from 'vue'
import type { Conversation } from '../api'
const props = defineProps<{ items: Conversation[]; activeId?: number; loading: boolean; disabled?: boolean }>()
const groups = computed(() => [
  { title: '今天', items: props.items.filter(item => new Date(item.updatedAt).toDateString() === new Date().toDateString()) },
  { title: '之前', items: props.items.filter(item => new Date(item.updatedAt).toDateString() !== new Date().toDateString()) }
].filter(group => group.items.length))
const emit = defineEmits<{ select: [id: number]; newChat: []; delete: [id: number] }>()
function relative(date: string) {
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const day = new Date(date); day.setHours(0, 0, 0, 0)
  const days = Math.round((today.getTime() - day.getTime()) / 86400000)
  return days <= 0 ? '今天' : days === 1 ? '昨天' : `${days} 天前`
}
</script>
<template>
  <aside class="sidebar"><fieldset :disabled="disabled" class="sidebar-controls">
    <div class="sidebar-head"><div class="side-label">会话</div><button class="icon-button" @click="emit('newChat')" title="新建会话">＋</button></div>
    <button class="new-chat" @click="emit('newChat')"><span>＋</span> 新建会话</button>
    <div v-if="loading" class="side-empty">正在加载历史会话……</div>
    <div v-else-if="!items.length" class="side-empty"><div class="empty-dot">◌</div><p>还没有会话</p><small>新建会话，开始分析你的运营问题。</small></div>
    <div v-else class="conversation-list">
      <div v-for="group in groups" :key="group.title" class="conversation-group">
      <div class="date-label">{{ group.title }}</div>
      <div v-for="item in group.items" :key="item.id" class="conversation-row">
      <button class="conversation-item" :class="{ active: item.id === activeId }" @click="emit('select', item.id)">
        <span class="conversation-icon">◈</span><span class="conversation-copy"><strong>{{ item.title }}</strong><small>{{ relative(item.updatedAt) }}</small></span><span class="chevron">›</span>
      </button>
      <button class="conversation-delete" :aria-label="`删除会话：${item.title}`" title="删除会话" @click="emit('delete', item.id)">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 10v7M14 10v7" /></svg>
      </button>
      </div>
      </div>
    </div>
    </fieldset><div class="sidebar-bottom">会话独立保存，仅当前账号可见</div>
  </aside>
</template>
