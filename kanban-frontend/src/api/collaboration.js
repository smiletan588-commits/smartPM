import request from '@/utils/request'

export const listTaskComments = taskId => request.get(`/task/${taskId}/comments`)
export const addTaskComment = (taskId, content, mentionedUserIds = []) =>
  request.post(`/task/${taskId}/comments`, { content, mentionedUserIds })
export const deleteTaskComment = (taskId, commentId) => request.delete(`/task/${taskId}/comments/${commentId}`)
export const listTaskActivities = taskId => request.get(`/task/${taskId}/activities`)
