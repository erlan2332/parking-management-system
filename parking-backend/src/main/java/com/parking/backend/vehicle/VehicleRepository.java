package com.parking.backend.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<VehicleEntity, Long> {

	List<VehicleEntity> findAllByUser_IdOrderByIdAsc(Long userId);

	Optional<VehicleEntity> findByNormalizedPlate(String normalizedPlate);

	Optional<VehicleEntity> findByIdAndUser_Id(Long vehicleId, Long userId);

	boolean existsByNormalizedPlate(String normalizedPlate);

	boolean existsByIdAndUser_Id(Long vehicleId, Long userId);
}
