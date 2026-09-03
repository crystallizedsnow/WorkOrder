import { describe, expect, it } from 'vitest'
import { parseSseFrames } from './assistant-stream'

describe('parseSseFrames', () => {
  it('parses named json events and keeps incomplete data', () => {
    const result = parseSseFrames('event: message.delta\ndata: {"content":"你"}\n\ndata: 后')
    expect(result.events).toEqual([{ event: 'message.delta', data: { content: '你' } }])
    expect(result.rest).toBe('data: 后')
  })
  it('parses default text messages', () => {
    expect(parseSseFrames('data: 完成\n\n').events[0]).toEqual({ event: 'message', data: '完成' })
  })
})
