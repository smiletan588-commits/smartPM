import request from '@/utils/request'

export const getProjectRisks = (projectId, baselineId) =>
  request.get(`/project/${projectId}/risks`, { params: baselineId ? { baselineId } : {} })
export const generateAiRiskAnalysis = projectId => request.post(`/project/${projectId}/risks/ai-analysis`, null, { timeout: 120000 })
export const updateRiskAction = (projectId, taskId, data) => request.put(`/project/${projectId}/risks/${taskId}/action`, data)
export const getRiskEvents = (projectId, taskId) => request.get(`/project/${projectId}/risks/${taskId}/events`)
