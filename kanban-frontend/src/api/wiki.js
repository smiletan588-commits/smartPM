import request from '@/utils/request'

export function listWiki(projectId, params = {}, config = {}) {
  return request.get(`/wiki/list/${projectId}`, { ...config, params })
}

export function getWiki(id) {
  return request.get(`/wiki/${id}`)
}

export function createWiki(projectId, title, content) {
  return request.post('/wiki/create', null, { params: { projectId, title, content } })
}

export function updateWiki(id, title, content, config = {}) {
  return request.put('/wiki/update', null, { ...config, params: { id, title, content } })
}

export function deleteWiki(id) {
  return request.delete(`/wiki/${id}`)
}

export const listWikiVersions = id => request.get(`/wiki/${id}/versions`)
export const restoreWikiVersion = (id, versionId) => request.post(`/wiki/${id}/versions/${versionId}/restore`)
export const updateWikiTaskLinks = (id, taskIds) => request.put(`/wiki/${id}/task-links`, { taskIds })

import { streamSSE } from '@/utils/sse'

export function streamAiCopilot(prompt, text, callbacks) {
  const url = `/api/wiki/ai-copilot?prompt=${encodeURIComponent(prompt)}&text=${encodeURIComponent(text)}`
  streamSSE(url, callbacks)
}
