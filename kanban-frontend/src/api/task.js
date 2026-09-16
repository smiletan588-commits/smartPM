import request from '@/utils/request'

export function createTask(projectId, title, description, assigneeId, dueDate, startDate, priority, tags, dependencyIds, estimatedHours, actualHours, acceptanceCriteria) {
  return request.post('/task/create', null, {
    params: { projectId, title, description, assigneeId, dueDate, startDate, priority, tags, dependencyIds, estimatedHours, actualHours, acceptanceCriteria }
  })
}

export function listTasks(projectId, config) {
  return config ? request.get(`/task/list/${projectId}`, config) : request.get(`/task/list/${projectId}`)
}

export function updateTask(dto) {
  return request.put('/task/update', dto)
}

export function batchUpdateTasks(data) {
  return request.put('/task/batch', data)
}

export function deleteTask(id) {
  return request.delete(`/task/${id}`)
}

export function decomposeTask(taskId) {
  // AI 拆解通常需要数十秒，不能沿用普通接口的 15 秒超时。
  return request.post(`/task/${taskId}/ai-decompose`, null, { timeout: 120000 })
}

export function optimizeTaskWithAi(taskId) {
  return request.post(`/task/${taskId}/ai-optimize`, null, { timeout: 120000 })
}

export function listSubtasks(taskId) {
  return request.get(`/task/${taskId}/subtasks`)
}

export function toggleSubtask(taskId) {
  return request.put(`/task/${taskId}/toggle-subtask`)
}

export function initProjectTasks(projectId) {
  return request.post(`/task/${projectId}/ai-init-tasks`, null, { timeout: 120000 })
}

export function listTaskAttachments(taskId) {
  return request.get(`/task/${taskId}/attachments`)
}

export function uploadTaskAttachment(taskId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/task/${taskId}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000
  })
}

export function deleteTaskAttachment(attachmentId) {
  return request.delete(`/task/attachments/${attachmentId}`)
}

export function getAttachmentDownloadUrl(attachmentId) {
  return `${request.defaults.baseURL}/task/attachments/${attachmentId}/download`
}

export function listAttachmentDownloadLogs(attachmentId) {
  return request.get(`/task/attachments/${attachmentId}/download-logs`)
}
