import { normalizeNotificationRow } from '../src/agent/analysis.repository'

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
