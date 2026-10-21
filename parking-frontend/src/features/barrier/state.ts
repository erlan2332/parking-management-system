import type { ParkingVisitResponse, VehicleResponse } from '../../shared/api/types'

export function findActiveVisit(
  visits: ParkingVisitResponse[] | undefined,
  vehicle: VehicleResponse | undefined,
): ParkingVisitResponse | undefined {
  if (!vehicle) return undefined
  return visits?.find(
    (visit) => visit.status === 'ACTIVE' && visit.plateNumber === vehicle.normalizedPlate,
  )
}
