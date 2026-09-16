import request from '@/utils/request'

export const exportTasks = projectId => request.get(`/project/${projectId}/tasks/export.csv`, { responseType: 'blob' })
export const previewTaskCsv = (projectId, file) => {
  const data = new FormData(); data.append('file', file)
  return request.post(`/project/${projectId}/tasks/import/preview`, data, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export const importTaskCsv = (projectId, rows) => request.post(`/project/${projectId}/tasks/import`, { rows })
