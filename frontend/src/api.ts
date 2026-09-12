import axios from 'axios'
const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE ?? '' })

export interface ApiEnvelope<T> { success: boolean; data: T; message: string }
export interface AuthResponse { accessToken: string; email: string; name: string; userId: number }
export interface DashboardSummary {
  productCount: number; lowStockCount: number; orderCount: number
  recent7DaysOrderCount: number; recent7DaysRevenue: number
  refundCount: number; completedOrderCount: number
}
export interface Conversation { id: number; title: string; createdAt: string; updatedAt: string }
export interface ConversationMessage { id: number; role: 'USER' | 'ASSISTANT'; content: string; createdAt: string }
export interface ToolCall {
  iteration: number; toolName: string; arguments: Record<string, unknown>; success: boolean
}
export interface AgentResponse {
  conversationId: number; answer: string; toolCalls: ToolCall[]
  pendingActionId?: number; requiresApproval?: boolean
}
export interface ApprovalResponse {
  pendingActionId: number; status: string
  result?: { sku: string; beforePrice: number; afterPrice: number }
  errorMessage?: string
}
export interface PendingAction { pendingActionId: number; toolName: string; arguments: Record<string, unknown>; status: string }
export async function pendingActions(conversationId: number) {
  const data: PendingAction[] = []
  for (let offset = 0; ; offset += 100) {
    const page = await request<PendingAction[]>(`/api/pending-actions?conversationId=${conversationId}&limit=100&offset=${offset}`)
    data.push(...page)
    if (page.length < 100) return data.reverse()
  }
}

function token() { return localStorage.getItem('commerce_token') }

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  try {
    const auth = path === '/api/auth/login' ? null : token()
    return (await http.request<T>({ url: path, method: options.method || 'GET',
      data: options.body, headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: `Bearer ${auth}` } : {}) }
    })).data
  } catch (e) {
    if (axios.isAxiosError(e)) {
      if (e.response?.status === 401 && path !== '/api/auth/login') {
        logout(); window.dispatchEvent(new Event('commerce:logout'))
      }
      throw new Error(e.response?.data?.message || '网络连接失败，请检查服务后重试。')
    }
    throw e
  }
}

export async function login(email: string, password: string) {
  const result = await request<ApiEnvelope<AuthResponse>>('/api/auth/login', {
    method: 'POST', body: JSON.stringify({ email, password })
  })
  localStorage.setItem('commerce_token', result.data.accessToken)
  localStorage.setItem('commerce_user', JSON.stringify(result.data))
  return result.data
}
export function logout() {
  localStorage.removeItem('commerce_token'); localStorage.removeItem('commerce_user')
}
export function savedUser(): AuthResponse | null {
  try { return token() ? JSON.parse(localStorage.getItem('commerce_user') || 'null') : null } catch { return null }
}
export const dashboard = () => request<ApiEnvelope<DashboardSummary>>('/api/dashboard/summary')
export const conversations = (limit = 50) => request<ApiEnvelope<Conversation[]>>(`/api/conversations?limit=${limit}&offset=0`)
export const deleteConversation = (id: number) => request<ApiEnvelope<null>>(`/api/conversations/${id}`, { method: 'DELETE' })
export async function messages(id: number) {
  const data: ConversationMessage[] = []
  for (let offset = 0; ; offset += 100) {
    const page = await request<ApiEnvelope<ConversationMessage[]>>(`/api/conversations/${id}/messages?limit=100&offset=${offset}`)
    data.push(...page.data)
    if (page.data.length < 100) return { ...page, data }
  }
}
export const chat = (message: string, conversationId?: number) => request<AgentResponse>('/api/agent/chat', {
  method: 'POST', body: JSON.stringify({ message, ...(conversationId ? { conversationId } : {}) })
})
export const approve = (id: number) => request<ApprovalResponse>(`/api/pending-actions/${id}/approve`, { method: 'POST', body: '{}' })
export const reject = (id: number) => request<ApprovalResponse>(`/api/pending-actions/${id}/reject`, { method: 'POST', body: '{}' })
