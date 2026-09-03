import { getAccessToken } from './http'

export interface AssistantEvent {
  event: string
  data: unknown
}

function decodeData(raw: string): unknown {
  const value = raw.trim()
  if (!value) return ''
  try { return JSON.parse(value) } catch { return value }
}

export function parseSseFrames(buffer: string): { events: AssistantEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const frames = normalized.split('\n\n')
  const rest = frames.pop() ?? ''
  const events = frames.flatMap((frame) => {
    if (!frame.trim()) return []
    let event = 'message'
    const data: string[] = []
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
      else if (!line.startsWith(':')) data.push(line)
    }
    return [{ event, data: decodeData(data.join('\n')) }]
  })
  return { events, rest }
}

export async function streamAssistant(
  memoryId: number,
  message: string,
  callbacks: { onEvent: (event: AssistantEvent) => void; onDone?: () => void },
  signal?: AbortSignal,
) {
  const token = getAccessToken()
  if (!token) throw new Error('登录状态已失效，请重新登录')
  const response = await fetch('/api/assistant/assistant/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ memoryId, message }),
    signal,
  })
  if (!response.ok) throw new Error(response.status === 401 ? '登录状态已失效' : `Agent 服务请求失败（${response.status}）`)
  if (!response.body) throw new Error('浏览器不支持读取流式响应')
  const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const parsed = parseSseFrames(buffer); buffer = parsed.rest
    parsed.events.forEach(callbacks.onEvent)
    if (done) break
  }
  if (buffer.trim()) callbacks.onEvent({ event: 'message', data: decodeData(buffer.replace(/^data:\s*/, '')) })
  callbacks.onDone?.()
}

export async function clearAssistantMemory(memoryId: number) {
  const token = getAccessToken()
  const response = await fetch(`/api/assistant/assistant/memory/${memoryId}`, { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } })
  if (!response.ok) throw new Error('清理会话失败')
}
