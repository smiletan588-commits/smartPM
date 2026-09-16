import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))

import request from '@/utils/request'
import { addTaskComment, deleteTaskComment } from './collaboration'
import { markAllNotificationsRead, markNotificationRead } from './notification'
import { getProjectRisks } from './risk'
import { updateRiskAction, getRiskEvents } from './risk'
import { getRoleView, getWorkspaceOverview, getWorkspaceTasks, globalSearch } from './workspace'
import { batchUpdateTasks } from './task'
import { listWikiVersions, restoreWikiVersion, updateWikiTaskLinks } from './wiki'
import { listProjectTemplates, createProjectFromTemplate } from './template'
import { getTaskRecurrence, saveTaskRecurrence } from './recurrence'
import { getNotificationPreferences, saveNotificationPreferences } from './email'
import {
  createScheduleBaseline,
  deleteScheduleBaseline,
  getProjectSchedule,
  listScheduleBaselines,
  simulateProjectSchedule
} from './schedule'
import { addAcceptanceChecklist, addCapacityException, addTimeEntry, deleteCapacityException, getProjectCapacity, getTaskAcceptance, updateTaskAcceptance } from './delivery'
import { downloadExecutiveSummaryPdf, getExecutiveSummary, getSystemOverview, listAuditEvents, listLoginEvents } from './management'
import { applyProductArtifact, createProductConversation, getProductArtifact, listProductArtifacts, previewProductArtifact, updateProductArtifact } from './productLab'

describe('graduation feature API contracts', () => {
  beforeEach(() => vi.clearAllMocks())

  it('submits comment text with explicit mentions', () => {
    addTaskComment(7, '请确认接口', [2, 3])
    expect(request.post).toHaveBeenCalledWith('/task/7/comments', { content: '请确认接口', mentionedUserIds: [2, 3] })
    deleteTaskComment(7, 9)
    expect(request.delete).toHaveBeenCalledWith('/task/7/comments/9')
  })

  it('uses the notification read endpoints', () => {
    markNotificationRead(5)
    markAllNotificationsRead()
    expect(request.put).toHaveBeenCalledWith('/notifications/5/read')
    expect(request.put).toHaveBeenCalledWith('/notifications/read-all')
  })

  it('loads server-computed risks instead of calculating in the browser', () => {
    getProjectRisks(11)
    expect(request.get).toHaveBeenCalledWith('/project/11/risks', { params: {} })

    getProjectRisks(11, 8)
    expect(request.get).toHaveBeenCalledWith('/project/11/risks', { params: { baselineId: 8 } })
  })

  it('uses schedule, simulation and immutable baseline endpoints', () => {
    getProjectSchedule(11, 8)
    listScheduleBaselines(11)
    simulateProjectSchedule(11, { taskId: 7, durationDays: 9 })
    createScheduleBaseline(11, { name: '答辩前计划' })
    deleteScheduleBaseline(11, 8)

    expect(request.get).toHaveBeenCalledWith('/project/11/schedule', { params: { baselineId: 8 } })
    expect(request.get).toHaveBeenCalledWith('/project/11/schedule/baselines')
    expect(request.post).toHaveBeenCalledWith('/project/11/schedule/simulate', { taskId: 7, durationDays: 9 })
    expect(request.post).toHaveBeenCalledWith('/project/11/schedule/baselines', { name: '答辩前计划' })
    expect(request.delete).toHaveBeenCalledWith('/project/11/schedule/baselines/8')
  })

  it('uses permission-scoped workspace search and atomic batch updates', () => {
    getWorkspaceOverview()
    getRoleView()
    getWorkspaceTasks({ scope: 'OVERDUE', page: 1, size: 20 })
    globalSearch('接口', 6)
    batchUpdateTasks({ taskIds: [1, 2], priority: 'HIGH' })
    expect(request.get).toHaveBeenCalledWith('/workspace/overview')
    expect(request.get).toHaveBeenCalledWith('/workspace/role-view')
    expect(request.get).toHaveBeenCalledWith('/workspace/tasks', { params: { scope: 'OVERDUE', page: 1, size: 20 } })
    expect(request.get).toHaveBeenCalledWith('/search', { params: { q: '接口', limit: 6 } })
    expect(request.put).toHaveBeenCalledWith('/task/batch', { taskIds: [1, 2], priority: 'HIGH' })
  })

  it('uses product lab preview-before-apply contracts', () => {
    createProductConversation(4, { title: 'MVP 讨论' })
    listProductArtifacts(4, 9)
    getProductArtifact(4, 12)
    updateProductArtifact(4, 12, { title:'MVP v2' })
    previewProductArtifact(4, 12, { target: 'TASKS', tasks: [{ title: '实现 MVP' }] })
    applyProductArtifact(4, 12, { target: 'TASKS', confirmed: true, tasks: [{ title: '实现 MVP', durationDays: 15 }] })
    expect(request.post).toHaveBeenCalledWith('/project/4/product-lab/conversations', { title: 'MVP 讨论' })
    expect(request.get).toHaveBeenCalledWith('/project/4/product-lab/artifacts', { params: { conversationId: 9 } })
    expect(request.get).toHaveBeenCalledWith('/project/4/product-lab/artifacts/12')
    expect(request.put).toHaveBeenCalledWith('/project/4/product-lab/artifacts/12', { title:'MVP v2' })
    expect(request.post).toHaveBeenCalledWith('/project/4/product-lab/artifacts/12/apply/preview', { target: 'TASKS', tasks: [{ title: '实现 MVP' }] })
    expect(request.post).toHaveBeenCalledWith('/project/4/product-lab/artifacts/12/apply', { target: 'TASKS', confirmed: true, tasks: [{ title: '实现 MVP', durationDays: 15 }] })
  })

  it('uses acceptance, time, capacity, summary and operations contracts', () => {
    getTaskAcceptance(7)
    updateTaskAcceptance(7, { action: 'SUBMIT' })
    addAcceptanceChecklist(7, '移动端通过', 1)
    addTimeEntry(7, { workDate: '2026-09-15', hours: 2.5 })
    getProjectCapacity(4)
    addCapacityException(4, { userId:2, exceptionDate:'2026-09-15', availableHours:0 })
    deleteCapacityException(4, 18)
    getExecutiveSummary(4)
    downloadExecutiveSummaryPdf(4)
    getSystemOverview()
    listAuditEvents({ projectId: 4, page: 1 })
    listLoginEvents({ success:false, page:1 })
    expect(request.get).toHaveBeenCalledWith('/task/7/acceptance')
    expect(request.put).toHaveBeenCalledWith('/task/7/acceptance', { action: 'SUBMIT' })
    expect(request.post).toHaveBeenCalledWith('/task/7/acceptance/checklist', { content: '移动端通过', orderIndex: 1 })
    expect(request.post).toHaveBeenCalledWith('/task/7/time-entries', { workDate: '2026-09-15', hours: 2.5 })
    expect(request.get).toHaveBeenCalledWith('/project/4/capacity')
    expect(request.post).toHaveBeenCalledWith('/project/4/capacity/exceptions', { userId:2, exceptionDate:'2026-09-15', availableHours:0 })
    expect(request.delete).toHaveBeenCalledWith('/project/4/capacity/exceptions/18')
    expect(request.get).toHaveBeenCalledWith('/project/4/executive-summary')
    expect(request.get).toHaveBeenCalledWith('/project/4/executive-summary/pdf', { responseType: 'blob', timeout: 60000 })
    expect(request.get).toHaveBeenCalledWith('/admin/system-overview')
    expect(request.get).toHaveBeenCalledWith('/admin/audit-events', { params: { projectId: 4, page: 1 } })
    expect(request.get).toHaveBeenCalledWith('/admin/login-events', { params: { success:false, page:1 } })
  })

  it('uses risk action and immutable Wiki version endpoints', () => {
    updateRiskAction(3, 7, { status: 'IN_PROGRESS', ownerUserId: 2 })
    getRiskEvents(3, 7)
    listWikiVersions(9)
    restoreWikiVersion(9, 12)
    updateWikiTaskLinks(9, [7, 8])
    expect(request.put).toHaveBeenCalledWith('/project/3/risks/7/action', { status: 'IN_PROGRESS', ownerUserId: 2 })
    expect(request.get).toHaveBeenCalledWith('/project/3/risks/7/events')
    expect(request.get).toHaveBeenCalledWith('/wiki/9/versions')
    expect(request.post).toHaveBeenCalledWith('/wiki/9/versions/12/restore')
    expect(request.put).toHaveBeenCalledWith('/wiki/9/task-links', { taskIds: [7, 8] })
  })

  it('uses template, recurrence, and email preference endpoints', () => {
    listProjectTemplates()
    createProjectFromTemplate({ templateId: 4, name: '新项目', startDate: '2026-09-14' })
    getTaskRecurrence(7)
    saveTaskRecurrence(7, { frequency: 'WEEKLY', intervalValue: 1, startDate: '2026-09-14' })
    getNotificationPreferences()
    saveNotificationPreferences({ emailEnabled: true })
    expect(request.get).toHaveBeenCalledWith('/project/templates')
    expect(request.post).toHaveBeenCalledWith('/project/create-from-template', { templateId: 4, name: '新项目', startDate: '2026-09-14' })
    expect(request.get).toHaveBeenCalledWith('/task/7/recurrence')
    expect(request.put).toHaveBeenCalledWith('/task/7/recurrence', { frequency: 'WEEKLY', intervalValue: 1, startDate: '2026-09-14' })
    expect(request.get).toHaveBeenCalledWith('/user/notification-preferences')
    expect(request.put).toHaveBeenCalledWith('/user/notification-preferences', { emailEnabled: true })
  })
})
