package com.parking.backend.parking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

	@Query(value = """
			SELECT spot.id
			FROM parking_spots spot
			WHERE spot.active = TRUE
			  AND NOT EXISTS (
			      SELECT 1
			      FROM parking_visits visit
			      WHERE visit.spot_id = spot.id AND visit.exited_at IS NULL
			  )
			  AND NOT EXISTS (
			      SELECT 1
			      FROM reservations reservation
			      WHERE reservation.spot_id = spot.id
			        AND (
			            reservation.status IN ('ACTIVE', 'CHECKED_IN')
			            OR (
			                reservation.status = 'HELD'
			                AND reservation.hold_expires_at > :now
			            )
			        )
			  )
			ORDER BY regexp_replace(spot.code, '[0-9]+$', ''),
			         CAST(substring(spot.code FROM '[0-9]+$') AS INTEGER),
			         spot.code
			""", nativeQuery = true)
	List<Long> findWalkInCandidateIds(@Param("now") Instant now);
}
