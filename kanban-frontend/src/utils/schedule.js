const DAY_MS = 86400000

export function parseScheduleDate(value) {
  return value ? new Date(`${value}T00:00:00`) : null
}

export function scheduleDayDifference(from, to) {
  const start = typeof from === 'string' ? parseScheduleDate(from) : from
  const end = typeof to === 'string' ? parseScheduleDate(to) : to
  if (!start || !end) return 0
  return Math.round((end.getTime() - start.getTime()) / DAY_MS)
}

export function scheduleBarStyle(startDate, finishDate, timelineStart, timelineDays) {
  if (!startDate || !finishDate || !timelineStart || !timelineDays) return { left: '0%', width: '0%' }
  const offset = Math.max(0, scheduleDayDifference(timelineStart, startDate))
  const duration = Math.max(1, scheduleDayDifference(startDate, finishDate) + 1)
  return {
    left: `${Math.min(100, offset / timelineDays * 100)}%`,
    width: `${Math.max(0.8, Math.min(100, duration / timelineDays * 100))}%`
  }
}

export function varianceLabel(days) {
  if (!days) return '与基线一致'
  return days > 0 ? `较基线晚 ${days} 天` : `较基线早 ${Math.abs(days)} 天`
}
