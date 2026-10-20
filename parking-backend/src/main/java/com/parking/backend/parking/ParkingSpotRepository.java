package com.parking.backend.parking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpotEntity, Long> {

	@Query(value = """
			SELECT id, code, active
			FROM parking_spots
			ORDER BY regexp_replace(code, '[0-9]+$', ''),
			         CAST(substring(code FROM '[0-9]+$') AS INTEGER),
			         code
			""", nativeQuery = true)
	List<ParkingSpotEntity> findAllInNaturalCodeOrder();
}
