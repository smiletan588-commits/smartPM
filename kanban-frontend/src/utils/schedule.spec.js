import { describe, expect, it } from 'vitest'
import { scheduleBarStyle, scheduleDayDifference, varianceLabel } from './schedule'

describe('schedule helpers', () => {
  it('calculates calendar-day differences without timezone drift', () => {
    expect(scheduleDayDifference('2026-09-10', '2026-09-15')).toBe(5)
  })

  it('positions inclusive schedule bars on the shared timeline', () => {
    expect(scheduleBarStyle('2026-09-12', '2026-09-14', '2026-09-10', 10)).toEqual({
      left: '20%', width: '30%'
    })
  })

  it('formats positive, negative and zero baseline variance', () => {
    expect(varianceLabel(4)).toBe('较基线晚 4 天')
    expect(varianceLabel(-2)).toBe('较基线早 2 天')
    expect(varianceLabel(0)).toBe('与基线一致')
  })
})
