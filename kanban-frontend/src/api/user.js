import request from '@/utils/request'

export function login(username, password) {
  return request.post('/user/login', { username, password }, { errorMode: 'silent' })
}

export function register(username, password, nickname) {
  return request.post('/user/register', { username, password, nickname }, { errorMode: 'silent' })
}

export function updateIdentity(identity) {
  return request.put('/user/identity', null, { params: { identity } })
}
