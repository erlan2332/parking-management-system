package com.parking.backend.visit.repository;

import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.model.ParkingVisitLockTarget;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParkingVisitRepository extends JpaRepository<ParkingVisitEntity, Long> {

	@Query("""
			SELECT new com.parking.backend.visit.model.ParkingVisitLockTarget(visit.id, visit.spot.id)
			FROM ParkingVisitEntity visit
			WHERE visit.vehicle.id = :vehicleId
			  AND visit.exitedAt IS NULL
			""")
	Optional<ParkingVisitLockTarget> findActiveLockTargetByVehicleId(@Param("vehicleId") Long vehicleId);

	boolean existsByVehicle_IdAndExitedAtIsNull(Long vehicleId);

	boolean existsBySpot_IdAndExitedAtIsNull(Long spotId);

	@Query("SELECT visit.spot.id FROM ParkingVisitEntity visit WHERE visit.exitedAt IS NULL")
	List<Long> findActiveSpotIds();

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
