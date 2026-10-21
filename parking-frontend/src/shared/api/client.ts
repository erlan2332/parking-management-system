import { getAccessToken } from '../lib/auth-storage'
import type { ApiErrorResponse } from './types'

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
export const API_BASE_URL = (configuredBaseUrl || 'http://localhost:8080').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.code === 'string' && typeof candidate.message === 'string'
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, signal?: AbortSignal): Promise<T> {
  const token = getAccessToken()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers, signal })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'NETWORK_ERROR', 'Не удалось подключиться к серверу')
  }

  const responseBody: unknown = response.headers.get('content-type')?.includes('application/json')
    ? await response.json()
    : null

  if (!response.ok) {
    const apiError = isApiErrorResponse(responseBody)
      ? new ApiError(response.status, responseBody.code, responseBody.message)
      : new ApiError(response.status, 'REQUEST_FAILED', 'Запрос не выполнен')
    if (response.status === 401 && token) window.dispatchEvent(new CustomEvent('parking:unauthorized'))
    throw apiError
  }

  return responseBody as T
}

const errorMessages: Record<string, string> = {
  INVALID_CREDENTIALS: 'Неверный email или пароль.',
  EMAIL_ALREADY_EXISTS: 'Пользователь с таким email уже зарегистрирован.',
  VEHICLE_ALREADY_EXISTS: 'Автомобиль с таким номером уже добавлен.',
  INVALID_PLATE_NUMBER: 'Проверьте формат государственного номера.',
  INVALID_PARKING_INTERVAL: 'Время окончания должно быть позже времени начала.',
  INVALID_RESERVATION_REQUEST: 'Проверьте автомобиль и выбранный интервал.',
  RESERVATION_CONFLICT: 'Это место уже выбрал другой водитель. Пожалуйста, выберите другое.',
  VEHICLE_ALREADY_PARKED: 'Этот автомобиль уже находится на парковке.',
  NO_AVAILABLE_PARKING_SPOT: 'Сейчас нет свободных мест для въезда без брони.',
  RESERVATION_ENTRY_CONFLICT: 'Въезд по бронированию сейчас недоступен.',
  NO_ACTIVE_VISIT: 'У автомобиля нет активной парковки.',
  VEHICLE_NOT_FOUND: 'Автомобиль не найден.',
  RESERVATION_NOT_FOUND: 'Бронирование не найдено.',
  INVOICE_NOT_FOUND: 'Счёт не найден.',
  NETWORK_ERROR: 'Не удалось подключиться к серверу.',
}

export function getErrorMessage(error: unknown, fallback = 'Что-то пошло не так. Повторите попытку.'): string {
  if (error instanceof ApiError) return errorMessages[error.code] ?? fallback
  return fallback
}
