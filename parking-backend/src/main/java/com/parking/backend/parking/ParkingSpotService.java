package com.parking.backend.parking;

import com.parking.backend.common.exception.InvalidParkingIntervalException;
import com.parking.backend.common.exception.ParkingSpotNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ParkingSpotService {

	private final ParkingSpotRepository parkingSpotRepository;

	public ParkingSpotService(ParkingSpotRepository parkingSpotRepository) {
		this.parkingSpotRepository = parkingSpotRepository;
	}

	@Transactional(readOnly = true)
	public List<ParkingSpotEntity> getAllSpots() {
		return List.copyOf(parkingSpotRepository.findAllInNaturalCodeOrder());
	}

	@Transactional(readOnly = true)
	public ParkingSpotEntity getSpotById(Long id) {
		return parkingSpotRepository.findById(id)
				.orElseThrow(() -> new ParkingSpotNotFoundException(id));
	}

	public ParkingSpotStatus determineStatus(ParkingSpotEntity parkingSpot) {
		return parkingSpot.isActive() ? ParkingSpotStatus.FREE : ParkingSpotStatus.UNAVAILABLE;
	}

	@Transactional(readOnly = true)
	public List<ParkingSpotAvailability> getAvailability(OffsetDateTime startAt, OffsetDateTime endAt) {
		validateInterval(startAt, endAt);
		return parkingSpotRepository.findAllInNaturalCodeOrder().stream()
				.map(spot -> new ParkingSpotAvailability(spot.getId(), spot.getCode(), spot.isActive()))
				.toList();
	}

	private static void validateInterval(OffsetDateTime startAt, OffsetDateTime endAt) {
		if (startAt == null || endAt == null || !startAt.toInstant().isBefore(endAt.toInstant())) {
			throw new InvalidParkingIntervalException();
		}
	}
}
