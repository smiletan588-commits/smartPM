import request from '@/utils/request'

export const listProjectTemplates = () => request.get('/project/templates')
export const saveProjectTemplate = (projectId, data) => request.post(`/project/${projectId}/templates`, data)
export const createProjectFromTemplate = data => request.post('/project/create-from-template', data)
export const deleteProjectTemplate = id => request.delete(`/project/templates/${id}`)
