import request from '@/utils/request'

export const getProjectSchedule = (projectId, baselineId) =>
  request.get(`/project/${projectId}/schedule`, { params: baselineId ? { baselineId } : {} })

export const simulateProjectSchedule = (projectId, data) =>
  request.post(`/project/${projectId}/schedule/simulate`, data)

export const listScheduleBaselines = projectId =>
  request.get(`/project/${projectId}/schedule/baselines`)

export const getScheduleBaseline = (projectId, baselineId) =>
  request.get(`/project/${projectId}/schedule/baselines/${baselineId}`)

export const createScheduleBaseline = (projectId, data) =>
  request.post(`/project/${projectId}/schedule/baselines`, data)

export const deleteScheduleBaseline = (projectId, baselineId) =>
  request.delete(`/project/${projectId}/schedule/baselines/${baselineId}`)
