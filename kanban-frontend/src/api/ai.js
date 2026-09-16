import request from '@/utils/request'

export const submitAiFeedback = (operationId, rating, feedback = '') =>
  request.post(`/ai/operations/${operationId}/feedback`, { rating, feedback })
export const markAiOperationApplied = (operationId, modifiedCount = 0) =>
  request.post(`/ai/operations/${operationId}/applied`, null, { params: { modifiedCount } })
export const getAiOverview = params => request.get('/analytics/ai-overview', { params })
