import { describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: {} }))

import { consumeSseBuffer } from './productLab'

describe('product lab stream parser', () => {
  it('keeps a fragmented event and emits it after the next chunk', () => {
    const events = []
    let buffer = consumeSseBuffer('event:delta\ndata:第一', (...args) => events.push(args))
    expect(events).toEqual([])
    buffer = consumeSseBuffer(buffer + '段\n\nevent:done\ndata:complete\n\n', (...args) => events.push(args))
    expect(buffer).toBe('')
    expect(events).toEqual([['delta', '第一段'], ['done', 'complete']])
  })

  it('supports multiline data and flushes the final event', () => {
    const events = []
    const buffer = consumeSseBuffer('event:artifact\ndata:第一行\ndata:第二行', (...args) => events.push(args), true)
    expect(buffer).toBe('')
    expect(events).toEqual([['artifact', '第一行\n第二行']])
  })
})
