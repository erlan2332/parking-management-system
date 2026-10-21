export type ParkingSpotStatus = 'FREE' | 'HELD' | 'RESERVED' | 'OCCUPIED' | 'UNAVAILABLE'

export type ReservationStatus =
  | 'HELD'
  | 'HOLD_EXPIRED'
  | 'HOLD_RELEASED'
  | 'ACTIVE'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'

export type ParkingEntryType = 'WALK_IN' | 'RESERVATION'
export type ParkingVisitStatus = 'ACTIVE' | 'COMPLETED'
export type InvoiceStatus = 'ISSUED'

export interface ApiErrorResponse {
  code: string
  message: string
  timestamp: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface RegisteredUserResponse {
  id: number
  email: string
}

export interface UserResponse {
  id: number
  email: string
  createdAt: string
}

export interface VehicleResponse {
  id: number
  plateNumber: string
  normalizedPlate: string
}

export interface SpotAvailabilityResponse {
  id: number
  code: string
  available: boolean
  status: ParkingSpotStatus
}

export interface ParkingAvailabilityResponse {
  startAt: string
  endAt: string
  spots: SpotAvailabilityResponse[]
}

export interface HoldResponse {
  id: number
  spotId: number
  spotCode: string
  vehicleId: number
  startAt: string
  endAt: string
  status: ReservationStatus
  holdExpiresAt: string
}

export interface ReservationResponse {
  id: number
  vehicleId: number
  spotId: number
  spotCode: string
  startAt: string
  endAt: string
  status: ReservationStatus
  confirmedAt: string | null
  cancelledAt: string | null
}

export interface ParkingVisitResponse {
  id: number
  plateNumber: string
  spotId: number
  spotCode: string
  entryType: ParkingEntryType
  enteredAt: string
  exitedAt: string | null
  durationSeconds: number | null
  status: ParkingVisitStatus
}

export interface BarrierEntryResponse {
  visitId: number
  plateNumber: string
  spotId: number
  spotCode: string
  entryType: ParkingEntryType
  enteredAt: string
  status: ParkingVisitStatus
}

export interface BarrierExitResponse {
  visitId: number
  plateNumber: string
  spotCode: string
  entryType: ParkingEntryType
  enteredAt: string
  exitedAt: string
  durationSeconds: number
  status: ParkingVisitStatus
  invoiceId: number
  invoiceNumber: string
  totalAmount: number
  currency: string
}

export interface InvoiceSummaryResponse {
  id: number
  invoiceNumber: string
  visitId: number
  vehiclePlate: string
  spotCode: string
  issuedAt: string
  status: InvoiceStatus
  totalAmount: number
  currency: string
}

export interface InvoiceResponse extends InvoiceSummaryResponse {
  enteredAt: string
  exitedAt: string
  pricingZoneId: string
  totalDurationSeconds: number
  dayDurationSeconds: number
  nightDurationSeconds: number
  dayRatePerMinute: number
  nightRatePerMinute: number
  dayCost: number
  nightCost: number
}

export interface ParkingAvailabilityChangedEvent {
  type: 'PARKING_AVAILABILITY_CHANGED'
  reason:
    | 'HOLD_CREATED'
    | 'HOLD_RELEASED'
    | 'HOLD_EXPIRED'
    | 'RESERVATION_CONFIRMED'
    | 'RESERVATION_CANCELLED'
    | 'RESERVATION_EXPIRED'
    | 'VEHICLE_ENTERED'
    | 'VEHICLE_EXITED'
  spotId: number
  spotCode: string
  occurredAt: string
}
