package com.parking.backend.visit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParkingVisitRepository extends JpaRepository<ParkingVisitEntity, Long> {

	@EntityGraph(attributePaths = {"vehicle", "spot", "reservation"})
	Optional<ParkingVisitEntity> findByVehicle_IdAndExitedAtIsNull(Long vehicleId);

	boolean existsByVehicle_IdAndExitedAtIsNull(Long vehicleId);

	boolean existsBySpot_IdAndExitedAtIsNull(Long spotId);

	@EntityGraph(attributePaths = "spot")
	List<ParkingVisitEntity> findAllByExitedAtIsNull();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT visit
			FROM ParkingVisitEntity visit
			JOIN FETCH visit.vehicle
			JOIN FETCH visit.spot
			WHERE visit.id = :id
			""")
	Optional<ParkingVisitEntity> findByIdForUpdate(@Param("id") Long id);

	@EntityGraph(attributePaths = {"vehicle", "spot", "reservation"})
	List<ParkingVisitEntity> findAllByVehicle_User_IdOrderByEnteredAtDescIdDesc(Long userId);

	Optional<ParkingVisitEntity> findByIdAndVehicle_User_Id(Long id, Long userId);
}
