import request from '@/utils/request'

export const getWorkspaceOverview = config => config ? request.get('/workspace/overview', config) : request.get('/workspace/overview')
export const getRoleView = () => request.get('/workspace/role-view')
export const getWorkspaceTasks = params => request.get('/workspace/tasks', { params })
export const globalSearch = (q, limit = 8) => request.get('/search', { params: { q, limit } })
