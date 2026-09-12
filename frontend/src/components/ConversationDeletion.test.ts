import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'
import AgentWorkspace from '../AgentWorkspace.vue'
import ConversationSidebar from './ConversationSidebar.vue'
import { conversations, deleteConversation, messages } from '../api'

vi.mock('vue-router', () => ({ useRouter: () => ({ replace: vi.fn() }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
vi.mock('../api', () => ({
  savedUser: () => ({ email: 'demo@example.com' }), logout: vi.fn(),
  conversations: vi.fn(), deleteConversation: vi.fn(), messages: vi.fn(),
  pendingActions: vi.fn().mockResolvedValue([]), dashboard: vi.fn().mockResolvedValue({ data: null }),
  chat: vi.fn(), approve: vi.fn(), reject: vi.fn()
}))
const items = [1, 2].map(id => ({ id, title: `测试会话${id}`, createdAt: '2026-09-12', updatedAt: '2026-09-12' }))
// Element Plus declares an intersection for prompt/confirm; confirm resolves to an action string.
const confirmed = 'confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>
let wrapper: ReturnType<typeof mount>
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(conversations).mockResolvedValue({ success: true, message: 'OK', data: [...items] })
  vi.mocked(deleteConversation).mockResolvedValue({ success: true, message: '会话已删除', data: null })
  vi.mocked(messages).mockResolvedValue({ success: true, message: 'OK', data: [{ id: 1, role: 'ASSISTANT', content: '保留的历史回答', createdAt: '2026-09-12' }] })
})
afterEach(() => wrapper?.unmount())
async function openWorkspace() {
  wrapper = mount(AgentWorkspace)
  await flushPromises()
  await wrapper.findAll('.conversation-item')[0]!.trigger('click')
  await flushPromises()
}
it('delete entry does not select the conversation', async () => {
  wrapper = mount(ConversationSidebar, { props: { items, loading: false } })
  await wrapper.findAll('.conversation-delete')[0]!.trigger('click')
  expect(wrapper.emitted('delete')).toEqual([[1]])
  expect(wrapper.emitted('select')).toBeUndefined()
})
it('cancel keeps the conversation and never sends DELETE', async () => {
  vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
  await openWorkspace()
  await wrapper.findAll('.conversation-delete')[0]!.trigger('click'); await flushPromises()
  expect(deleteConversation).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('保留的历史回答')
  expect(wrapper.findAll('.conversation-item')).toHaveLength(2)
})
it('confirmed deletion refreshes the list and clears the active workspace', async () => {
  vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce(confirmed)
  await openWorkspace()
  vi.mocked(conversations).mockResolvedValueOnce({ success: true, message: 'OK', data: [items[1]!] })
  await wrapper.findAll('.conversation-delete')[0]!.trigger('click'); await flushPromises()
  expect(deleteConversation).toHaveBeenCalledTimes(1)
  expect(deleteConversation).toHaveBeenCalledWith(1)
  expect(conversations).toHaveBeenCalledTimes(2)
  expect(wrapper.findAll('.conversation-item')).toHaveLength(1)
  expect(wrapper.text()).not.toContain('保留的历史回答')
  expect(wrapper.find('.welcome').exists()).toBe(true)
  expect(ElMessage.success).toHaveBeenCalledWith('会话已删除')
})
it('deleting another conversation preserves the active history', async () => {
  vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce(confirmed)
  await openWorkspace()
  vi.mocked(conversations).mockResolvedValueOnce({ success: true, message: 'OK', data: [items[0]!] })
  await wrapper.findAll('.conversation-delete')[1]!.trigger('click'); await flushPromises()
  expect(deleteConversation).toHaveBeenCalledWith(2)
  expect(wrapper.text()).toContain('保留的历史回答')
  expect(wrapper.get('.conversation-item.active').text()).toContain('测试会话1')
})
it('failure keeps history and shows a Chinese error', async () => {
  vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce(confirmed)
  vi.mocked(deleteConversation).mockRejectedValueOnce(new Error('删除失败，请稍后重试。'))
  await openWorkspace()
  await wrapper.findAll('.conversation-delete')[0]!.trigger('click'); await flushPromises()
  expect(wrapper.get('.error-banner').text()).toContain('删除失败，请稍后重试。')
  expect(wrapper.text()).toContain('保留的历史回答')
  expect(wrapper.findAll('.conversation-item')).toHaveLength(2)
})
