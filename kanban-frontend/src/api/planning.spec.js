import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: request }))

import { applyPlanningDraft, generatePlanningDraft, getPlanningDraft, listPlanningDrafts, regeneratePlanningItem, savePlanningDraft } from './planning'

describe('AI 规划草稿接口', () => {
  beforeEach(() => vi.clearAllMocks())

  it('将澄清信息作为 POST 正文发送，不立即调用任务创建接口', () => {
    const input = { mode: 'DECOMPOSE', parentTaskId: 12, goal: '完成报名', deliverable: '可用表单', scope: '报名流程' }
    generatePlanningDraft(20, input)
    expect(request.post).toHaveBeenCalledWith('/project/20/planning/drafts', input, { timeout: 120000 })
  })

  it('读取、保存、单项重生成和应用均携带草稿标识及版本', () => {
    listPlanningDrafts(20)
    getPlanningDraft(20, 7)
    savePlanningDraft(20, 7, 3, { tasks: [] })
    regeneratePlanningItem(20, 7, 1)
    applyPlanningDraft(20, 7, 4)
    expect(request.get).toHaveBeenCalledWith('/project/20/planning/drafts')
    expect(request.get).toHaveBeenCalledWith('/project/20/planning/drafts/7')
    expect(request.put).toHaveBeenCalledWith('/project/20/planning/drafts/7', { tasks: [] }, { params: { version: 3 } })
    expect(request.post).toHaveBeenCalledWith('/project/20/planning/drafts/7/regenerate-item', null, { params: { index: 1 }, timeout: 120000 })
    expect(request.post).toHaveBeenCalledWith('/project/20/planning/drafts/7/apply', null, { params: { version: 4 } })
  })
})
