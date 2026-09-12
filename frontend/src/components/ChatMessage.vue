<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
const props = defineProps<{ role: 'USER' | 'ASSISTANT'; content: string; time?: string }>()
const rendered = computed(() => DOMPurify.sanitize(marked.parse(props.content, { async: false, breaks: true }), {
  ALLOWED_TAGS: ['p','br','strong','em','ul','ol','li','h1','h2','h3','h4','blockquote','pre','code','table','thead','tbody','tr','th','td','hr'],
  ALLOWED_ATTR: []
}))
</script>
<template>
  <article class="message-row" :class="role.toLowerCase()">
    <div v-if="role === 'ASSISTANT'" class="avatar ai-avatar">✦</div>
    <div class="message-body"><div class="message-meta">{{ role === 'ASSISTANT' ? 'AI 运营助手' : '你' }} <span>{{ time }}</span></div><div v-if="role === 'ASSISTANT'" class="message-bubble markdown" v-html="rendered"></div><div v-else class="message-bubble user-text">{{ content }}</div></div>
    <div v-if="role === 'USER'" class="avatar user-avatar">你</div>
  </article>
</template>
