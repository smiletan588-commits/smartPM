export const TASK_STATUS_FLOW = ['TODO', 'IN_PROGRESS', 'DONE']

export function canMoveTask(sourceStatus, targetStatus) {
  if (sourceStatus === targetStatus) return true
  return sourceStatus === 'TODO' && targetStatus === 'IN_PROGRESS'
    || sourceStatus === 'IN_PROGRESS' && targetStatus === 'DONE'
}

export function nextTaskStatus(status) {
  if (status === 'TODO') return 'IN_PROGRESS'
  if (status === 'IN_PROGRESS') return 'DONE'
  return null
}

export function transitionHint(sourceStatus, targetStatus) {
  if (sourceStatus === 'TODO' && targetStatus === 'DONE') return '待办任务必须先领取并开始，不能越级完成'
  if (sourceStatus === 'DONE' && targetStatus !== 'DONE') return '已完成任务只能由测试工程师因 Bug 打回'
  return '任务只能按“待办 → 进行中 → 已完成”顺序流转'
}
