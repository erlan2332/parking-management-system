package com.parking.backend.parking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpotEntity, Long> {

	@Query(value = """
			SELECT id, code, active
			FROM parking_spots
			ORDER BY regexp_replace(code, '[0-9]+$', ''),
			         CAST(substring(code FROM '[0-9]+$') AS INTEGER),
			         code
			""", nativeQuery = true)
	List<ParkingSpotEntity> findAllInNaturalCodeOrder();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT spot FROM ParkingSpotEntity spot WHERE spot.id = :id")
	Optional<ParkingSpotEntity> findByIdForUpdate(@Param("id") Long id);
}
