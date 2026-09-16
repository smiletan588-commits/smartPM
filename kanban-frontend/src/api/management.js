import request from '@/utils/request'

export const getExecutiveSummary = projectId => request.get(`/project/${projectId}/executive-summary`)
export const downloadExecutiveSummaryPdf = projectId => request.get(`/project/${projectId}/executive-summary/pdf`, { responseType: 'blob', timeout: 60000 })
export const emailExecutiveSummary = (projectId, recipientUserIds) => request.post(`/project/${projectId}/executive-summary/email`, { recipientUserIds })
export const getSystemOverview = () => request.get('/admin/system-overview')
export const listAuditEvents = params => request.get('/admin/audit-events', { params })
export const listLoginEvents = params => request.get('/admin/login-events', { params })
