import request from '@/utils/request'

export const getNotificationPreferences = () => request.get('/user/notification-preferences')
export const saveNotificationPreferences = data => request.put('/user/notification-preferences', data)
export const requestEmailVerification = () => request.post('/user/notification-preferences/verification')
export const verifyEmail = token => request.post('/user/notification-preferences/verify', { token })
export const sendTestEmail = () => request.post('/user/notification-preferences/test')
