import request from '@/utils/request'

export const listRecycleBin = config => config ? request.get('/recycle-bin', config) : request.get('/recycle-bin')
export const restoreRecycleItem = (type, id) => request.put(`/recycle-bin/${type}/${id}/restore`)
export const permanentlyDeleteRecycleItem = (type, id) => request.delete(`/recycle-bin/${type}/${id}/permanent`)
