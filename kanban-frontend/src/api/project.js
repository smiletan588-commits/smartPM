import request from '@/utils/request'

export function createProject(name, description) {
  return request.post('/project/create', null, { params: { name, description } })
}

export function listProjects(config) {
  return config ? request.get('/project/list', config) : request.get('/project/list')
}

export function updateProject(id, name, description) {
  return request.put('/project/update', null, { params: { id, name, description } })
}

export function deleteProject(id) {
  return request.delete(`/project/${id}`)
}

export function listProjectMembers(projectId) {
  return request.get(`/project/${projectId}/members`)
}

export function inviteProjectMember(projectId, username, identity, permission) {
  return request.post(`/project/${projectId}/members/invite`, null, { params: { username, identity, permission } })
}

export function updateProjectMember(projectId, userId, identity, permission) {
  return request.put(`/project/${projectId}/members/${userId}`, null, { params: { identity, permission } })
}

export function removeProjectMember(projectId, userId) {
  return request.delete(`/project/${projectId}/members/${userId}`)
}

export function transferProjectOwner(projectId, userId) {
  return request.post(`/project/${projectId}/transfer-owner`, null, { params: { userId } })
}

export function getProjectInviteCode(projectId) {
  return request.get(`/project/${projectId}/invite-code`)
}

export function joinProjectByInviteCode(inviteCode) {
  return request.post('/project/join', null, { params: { inviteCode } })
}

export function updateMyProjectIdentity(projectId, identity) {
  return request.put(`/project/${projectId}/my-identity`, null, { params: { identity } })
}

export function listMilestones(projectId) {
  return request.get(`/project/${projectId}/milestones`)
}

export function createMilestone(projectId, data) {
  return request.post(`/project/${projectId}/milestones`, data)
}

export function updateMilestone(projectId, data) {
  return request.put(`/project/${projectId}/milestones`, data)
}

export function deleteMilestone(projectId, milestoneId) {
  return request.delete(`/project/${projectId}/milestones/${milestoneId}`)
}

export function generateAiProjectPlan(projectId) {
  return request.post(`/project/${projectId}/ai-plan`, null, { timeout: 120000 })
}

export function applyAiProjectPlan(projectId, plan, operationId) {
  return request.post(`/project/${projectId}/ai-plan/apply`, plan, { params: { operationId } })
}
