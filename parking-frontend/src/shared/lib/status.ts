import type { ParkingEntryType, ParkingSpotStatus, ParkingVisitStatus, ReservationStatus } from '../api/types'

export const parkingStatusLabels: Record<ParkingSpotStatus, string> = {
  FREE: 'Свободно',
  HELD: 'Временно удерживается',
  RESERVED: 'Забронировано',
  OCCUPIED: 'Занято',
  UNAVAILABLE: 'Недоступно',
}

export const reservationStatusLabels: Record<ReservationStatus, string> = {
  HELD: 'Ожидает подтверждения',
  HOLD_EXPIRED: 'Время выбора истекло',
  HOLD_RELEASED: 'Выбор отменён',
  ACTIVE: 'Подтверждено',
  CHECKED_IN: 'Автомобиль на парковке',
  COMPLETED: 'Завершено',
  CANCELLED: 'Отменено',
  EXPIRED: 'Истекло',
}

export const visitStatusLabels: Record<ParkingVisitStatus, string> = {
  ACTIVE: 'Сейчас на парковке',
  COMPLETED: 'Завершено',
}

export const entryTypeLabels: Record<ParkingEntryType, string> = {
  WALK_IN: 'Без бронирования',
  RESERVATION: 'По бронированию',
}
