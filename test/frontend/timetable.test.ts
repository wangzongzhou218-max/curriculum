import { describe, expect, it } from 'vitest'
import { layoutDay } from '../../code/frontend/app/utils/timetable'
import type { Course } from '../../code/frontend/app/types/domain'

const course = (id: string, startTime: string, endTime: string, weekday = 1) => ({ id, code: id, meetings: [{ weekday, startTime, endTime }] }) as Course
describe('完整周课表布局', () => {
  it('相邻课程不占用额外列，午后位置保留午休空间', () => {
    const blocks = layoutDay([course('1', '08:00', '10:00'), course('2', '10:00', '12:00'), course('3', '14:00', '15:00')], 1)
    expect(blocks.map(b => [b.top, b.height, b.column, b.columns])).toEqual([[0, 160, 0, 1], [160, 160, 0, 1], [480, 80, 0, 1]])
  })
  it('所有重叠课程均可见，连通重叠组共享列宽', () => {
    const blocks = layoutDay([course('1', '08:00', '10:00'), course('2', '09:00', '11:00'), course('3', '10:00', '12:00'), course('4', '08:00', '09:00', 2)], 1)
    expect(blocks).toHaveLength(3)
    expect(blocks.map(b => b.column)).toEqual([0, 1, 0])
    expect(blocks.every(b => b.columns === 2)).toBe(true)
  })
  it('超过一页的课程不被截断', () => {
    expect(layoutDay(Array.from({ length: 25 }, (_, i) => course(String(i), '08:00', '09:00')), 1)).toHaveLength(25)
  })
})
