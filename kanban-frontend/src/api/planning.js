import request from '@/utils/request'

const base = projectId => `/project/${projectId}/planning/drafts`

export const listPlanningDrafts = projectId => request.get(base(projectId))
export const generatePlanningDraft = (projectId, input) => request.post(base(projectId), input, { timeout: 120000 })
export const getPlanningDraft = (projectId, draftId) => request.get(`${base(projectId)}/${draftId}`)
export const savePlanningDraft = (projectId, draftId, version, content) => request.put(`${base(projectId)}/${draftId}`, content, { params: { version } })
export const regeneratePlanningItem = (projectId, draftId, index) => request.post(`${base(projectId)}/${draftId}/regenerate-item`, null, { params: { index }, timeout: 120000 })
export const applyPlanningDraft = (projectId, draftId, version) => request.post(`${base(projectId)}/${draftId}/apply`, null, { params: { version } })
