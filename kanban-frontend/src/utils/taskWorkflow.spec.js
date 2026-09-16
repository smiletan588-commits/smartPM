import { describe, expect, it } from 'vitest'
import { canMoveTask, nextTaskStatus, transitionHint } from './taskWorkflow'

describe('task workflow', () => {
  it('allows reordering and adjacent forward transitions only', () => {
    expect(canMoveTask('TODO', 'TODO')).toBe(true)
    expect(canMoveTask('TODO', 'IN_PROGRESS')).toBe(true)
    expect(canMoveTask('IN_PROGRESS', 'DONE')).toBe(true)
    expect(canMoveTask('TODO', 'DONE')).toBe(false)
    expect(canMoveTask('IN_PROGRESS', 'TODO')).toBe(false)
    expect(canMoveTask('DONE', 'IN_PROGRESS')).toBe(false)
  })

  it('exposes the next step and actionable rejection copy', () => {
    expect(nextTaskStatus('TODO')).toBe('IN_PROGRESS')
    expect(nextTaskStatus('IN_PROGRESS')).toBe('DONE')
    expect(nextTaskStatus('DONE')).toBeNull()
    expect(transitionHint('TODO', 'DONE')).toContain('不能越级')
    expect(transitionHint('DONE', 'IN_PROGRESS')).toContain('测试工程师')
  })
})
