import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ApprovalCard from './ApprovalCard.vue'
import ToolTracePanel from './ToolTracePanel.vue'
import OperationsSummary from './OperationsSummary.vue'
import { approve, reject } from '../api'
vi.mock('../api', () => ({ approve: vi.fn(), reject: vi.fn() }))
const action = () => ({ pendingActionId: 7, toolName: 'update_product_price', arguments: { sku: 'SKU-A001', newPrice: 25.99 }, status: 'PENDING' })
beforeEach(() => vi.resetAllMocks())

describe('approval state', () => {
  it('keeps the executed card and shows real before/after values', async () => {
    vi.mocked(approve).mockResolvedValue({ pendingActionId: 7, status: 'EXECUTED', result: { sku: 'SKU-A001', beforePrice: 29.99, afterPrice: 25.99 } })
    const wrapper = mount(ApprovalCard, { props: { action: action() } })
    await wrapper.get('.approve-button').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('操作已执行')
    expect(wrapper.text()).toContain('29.99 → 25.99')
    expect(wrapper.find('.approve-button').exists()).toBe(false)
    expect(approve).toHaveBeenCalledTimes(1)
  })
  it('keeps rejected state without calling approve', async () => {
    vi.mocked(reject).mockResolvedValue({ pendingActionId: 7, status: 'REJECTED' })
    const wrapper = mount(ApprovalCard, { props: { action: action() } })
    await wrapper.get('.reject-button').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('已拒绝此次操作')
    expect(approve).not.toHaveBeenCalled()
  })
  it('retains approval controls on a network error', async () => {
    vi.mocked(approve).mockRejectedValue(new Error('Network unavailable'))
    const wrapper = mount(ApprovalCard, { props: { action: action() } })
    await wrapper.get('.approve-button').trigger('click'); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Network unavailable')
    expect(wrapper.get('.approve-button').attributes('disabled')).toBeUndefined()
  })
  it('disables both decisions during execution', async () => {
    let finish!: (value: any) => void
    vi.mocked(approve).mockReturnValue(new Promise(resolve => { finish = resolve }))
    const wrapper = mount(ApprovalCard, { props: { action: action() } })
    await wrapper.get('.approve-button').trigger('click')
    await wrapper.get('.approve-button').trigger('click')
    expect(wrapper.get('.reject-button').attributes('disabled')).toBeDefined()
    expect(approve).toHaveBeenCalledTimes(1)
    finish({ status: 'EXECUTED', pendingActionId: 7 }); await flushPromises()
  })
  it('restores terminal actions without actionable buttons', () => {
    const wrapper = mount(ApprovalCard, { props: { action: { ...action(), status: 'EXECUTED' } } })
    expect(wrapper.find('.approve-button').exists()).toBe(false)
    expect(wrapper.text()).toContain('操作已执行')
  })
})
it('shows write calls as proposals and excludes sensitive arguments', () => {
  const wrapper = mount(ToolTracePanel, { props: { busy: false, calls: [{ iteration: 1, toolName: 'update_product_price', success: true, arguments: { sku: 'SKU-A001', userId: 99, JWT: 'secret' } }] } })
  expect(wrapper.text()).toContain('待人工确认')
  expect(wrapper.text()).not.toContain('执行成功')
  expect(wrapper.text()).not.toContain('secret')
  expect(wrapper.text()).not.toContain('userId')
})
it('does not present mixed-currency dashboard totals as dollars', () => {
  const wrapper = mount(OperationsSummary, { props: { loading: false, summary: { productCount: 12, lowStockCount: 5, orderCount: 30, recent7DaysOrderCount: 7, recent7DaysRevenue: 239.5, refundCount: 2, completedOrderCount: 10 } } })
  expect(wrapper.text()).toContain('多币种汇总，未折算汇率')
  expect(wrapper.text()).not.toContain('$')
})
