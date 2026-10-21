import { DateTime } from 'luxon'

export const PARKING_ZONE = 'Asia/Bishkek'

export function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return DateTime.fromISO(value, { setZone: true }).setZone(PARKING_ZONE).setLocale('ru').toFormat('dd LLL yyyy, HH:mm')
}

export function formatMoney(value: number, currency = 'KGS'): string {
  const suffix = currency === 'KGS' ? 'сом' : currency
  return `${new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)} ${suffix}`
}

export function formatDuration(seconds: number | null): string {
  if (seconds === null) return 'Сейчас на парковке'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours === 0) return `${Math.max(minutes, 1)} мин`
  return `${hours} ч ${minutes} мин`
}

export function toParkingOffsetDateTime(date: string, time: string): string | null {
  const value = DateTime.fromISO(`${date}T${time}`, { zone: PARKING_ZONE })
  return value.isValid ? value.toISO({ suppressMilliseconds: true }) : null
}

export function initialParkingForm(): { date: string; startTime: string; endTime: string } {
  const start = DateTime.now().setZone(PARKING_ZONE).plus({ minutes: 30 }).startOf('minute')
  const end = start.plus({ hours: 1 })
  return {
    date: start.toISODate() ?? '',
    startTime: start.toFormat('HH:mm'),
    endTime: end.toFormat('HH:mm'),
  }
}
