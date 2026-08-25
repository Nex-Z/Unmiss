import { normalizeNotificationRow } from '../src/agent/analysis.repository'
import { isScheduleDue } from '../src/agent/notification-analysis.service'

describe('analysis notification row normalization', () => {
  const baseRow = {
    id: 'notification-id',
    userId: 'user-id',
    deviceId: 'device-id',
    notificationKey: 'notification-key',
    packageName: 'com.example.app',
    title: 'Title',
    body: 'Body',
    subText: null,
    timezone: 'Asia/Hong_Kong',
  }

  it('converts PostgreSQL timestamp strings to Date objects', () => {
    const row = normalizeNotificationRow({
      ...baseRow,
      postedAt: '2026-08-25T14:30:00.000Z',
      receivedAt: '2026-08-25T14:30:01.000Z',
    })

    expect(row.postedAt).toBeInstanceOf(Date)
    expect(row.receivedAt).toBeInstanceOf(Date)
    expect(row.postedAt.toISOString()).toBe('2026-08-25T14:30:00.000Z')
  })

  it('rejects invalid database timestamps', () => {
    expect(() =>
      normalizeNotificationRow({
        ...baseRow,
        postedAt: 'not-a-date',
        receivedAt: new Date(),
      }),
    ).toThrow('invalid postedAt from database')
  })
})

describe('analysis schedule due calculation', () => {
  const schedule = {
    times: ['07:00', '19:30', '22:00'],
    timezone: 'Asia/Hong_Kong',
  }

  it('runs at each configured local time only once', () => {
    const now = new Date('2026-08-25T11:31:00.000Z') // 19:31 Hong Kong
    expect(isScheduleDue({ ...schedule, lastRunAt: null }, now)).toBe(true)
    expect(
      isScheduleDue(
        { ...schedule, lastRunAt: new Date('2026-08-25T11:30:30.000Z') },
        now,
      ),
    ).toBe(false)
  })

  it('recognizes the next morning slot across midnight', () => {
    expect(
      isScheduleDue(
        {
          ...schedule,
          lastRunAt: new Date('2026-08-25T14:01:00.000Z'), // 22:01
        },
        new Date('2026-08-25T23:01:00.000Z'), // next day 07:01
      ),
    ).toBe(true)
  })
})
