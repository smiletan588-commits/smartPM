import request from '@/utils/request'

export const getTaskRecurrence = taskId => request.get(`/task/${taskId}/recurrence`)
export const saveTaskRecurrence = (taskId, data) => request.put(`/task/${taskId}/recurrence`, data)
export const deleteTaskRecurrence = taskId => request.delete(`/task/${taskId}/recurrence`)
