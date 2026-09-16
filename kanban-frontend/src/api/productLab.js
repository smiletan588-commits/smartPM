import request from '@/utils/request'

export const listProductConversations = projectId => request.get(`/project/${projectId}/product-lab/conversations`)
export const createProductConversation = (projectId, data) => request.post(`/project/${projectId}/product-lab/conversations`, data)
export const getProductConversation = (projectId, id) => request.get(`/project/${projectId}/product-lab/conversations/${id}`)
export const listProductArtifacts = (projectId, conversationId) => request.get(`/project/${projectId}/product-lab/artifacts`, { params: { conversationId } })
export const getProductArtifact = (projectId, id) => request.get(`/project/${projectId}/product-lab/artifacts/${id}`)
export const createProductArtifact = (projectId, data) => request.post(`/project/${projectId}/product-lab/artifacts`, data)
export const updateProductArtifact = (projectId, id, data) => request.put(`/project/${projectId}/product-lab/artifacts/${id}`, data)
export const publishProductArtifact = (projectId, id) => request.post(`/project/${projectId}/product-lab/artifacts/${id}/publish`)
export const previewProductArtifact = (projectId, id, data) => request.post(`/project/${projectId}/product-lab/artifacts/${id}/apply/preview`, data)
export const applyProductArtifact = (projectId, id, data) => request.post(`/project/${projectId}/product-lab/artifacts/${id}/apply`, data)

export function consumeSseBuffer(buffer, onEvent, flush = false) {
  const blocks = buffer.split(/\r?\n\r?\n/)
  let rest = blocks.pop() || ''
  if (flush && rest.trim()) { blocks.push(rest); rest = '' }
  for (const block of blocks) {
    const event = block.match(/^event:(.+)$/m)?.[1]?.trim() || 'delta'
    const payload = block.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
    onEvent(event, payload)
  }
  return rest
}

export async function streamProductMessage(projectId, conversationId, data, onEvent, signal) {
  const response = await fetch(`/api/project/${projectId}/product-lab/conversations/${conversationId}/messages/stream`, {
    method: 'POST', signal,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
    body: JSON.stringify(data)
  })
  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || !response.body || !contentType.includes('text/event-stream')) {
    const body = await response.text()
    let message = body
    try { message = JSON.parse(body)?.msg || body } catch { /* 保留原始响应 */ }
    throw new Error(message || '共创服务暂时不可用')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
      buffer = consumeSseBuffer(buffer, onEvent, done)
      if (done) break
    }
  } finally {
    try { await reader.cancel() } catch { /* 连接可能已经由服务端关闭 */ }
  }
}
