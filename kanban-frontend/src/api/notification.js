import request from '@/utils/request'

export const listNotifications = unreadOnly => request.get('/notifications', { params: { unreadOnly } })
export const getUnreadNotificationCount = () => request.get('/notifications/unread-count')
export const markNotificationRead = id => request.put(`/notifications/${id}/read`)
export const markAllNotificationsRead = () => request.put('/notifications/read-all')
