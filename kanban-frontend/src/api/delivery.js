import request from '@/utils/request'

export const getTaskAcceptance = taskId => request.get(`/task/${taskId}/acceptance`)
export const updateTaskAcceptance = (taskId, data) => request.put(`/task/${taskId}/acceptance`, data)
export const addAcceptanceChecklist = (taskId, content, orderIndex) => request.post(`/task/${taskId}/acceptance/checklist`, { content, orderIndex })
export const toggleAcceptanceChecklist = (taskId, itemId, checked) => request.put(`/task/${taskId}/acceptance/checklist/${itemId}`, null, { params: { checked } })
export const deleteAcceptanceChecklist = (taskId, itemId) => request.delete(`/task/${taskId}/acceptance/checklist/${itemId}`)
export const listTimeEntries = taskId => request.get(`/task/${taskId}/time-entries`)
export const addTimeEntry = (taskId, data) => request.post(`/task/${taskId}/time-entries`, data)
export const getProjectCapacity = projectId => request.get(`/project/${projectId}/capacity`)
export const updateProjectCapacity = (projectId, data) => request.put(`/project/${projectId}/capacity`, data)
export const addCapacityException = (projectId, data) => request.post(`/project/${projectId}/capacity/exceptions`, data)
export const deleteCapacityException = (projectId, id) => request.delete(`/project/${projectId}/capacity/exceptions/${id}`)
