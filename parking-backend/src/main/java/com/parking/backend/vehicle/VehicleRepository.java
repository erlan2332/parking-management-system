package com.parking.backend.vehicle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<VehicleEntity, Long> {

	List<VehicleEntity> findAllByUser_IdOrderByIdAsc(Long userId);

	Optional<VehicleEntity> findByNormalizedPlate(String normalizedPlate);

	Optional<VehicleEntity> findByIdAndUser_Id(Long vehicleId, Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT vehicle FROM VehicleEntity vehicle " +
			"WHERE vehicle.id = :vehicleId AND vehicle.user.id = :userId")
	Optional<VehicleEntity> findOwnedByIdForUpdate(
			@Param("vehicleId") Long vehicleId,
			@Param("userId") Long userId
	);

	boolean existsByNormalizedPlate(String normalizedPlate);

	boolean existsByIdAndUser_Id(Long vehicleId, Long userId);
}
