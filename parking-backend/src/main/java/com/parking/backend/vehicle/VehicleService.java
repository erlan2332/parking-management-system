package com.parking.backend.vehicle;

import com.parking.backend.common.exception.DuplicateVehiclePlateException;
import com.parking.backend.common.exception.InvalidPlateNumberException;
import com.parking.backend.common.exception.UserNotFoundException;
import com.parking.backend.user.UserEntity;
import com.parking.backend.user.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class VehicleService {

	private static final int MAX_DISPLAY_PLATE_LENGTH = 32;
	private static final String UNIQUE_PLATE_CONSTRAINT = "uk_vehicles_normalized_plate";

	private final UserRepository userRepository;
	private final VehicleRepository vehicleRepository;
	private final PlateNormalizer plateNormalizer;

	public VehicleService(
			UserRepository userRepository,
			VehicleRepository vehicleRepository,
			PlateNormalizer plateNormalizer
	) {
		this.userRepository = userRepository;
		this.vehicleRepository = vehicleRepository;
		this.plateNormalizer = plateNormalizer;
	}

	@Transactional
	public VehicleEntity addVehicle(Long userId, String plateNumber) {
		UserEntity user = findUser(userId);
		String displayPlate = prepareDisplayPlate(plateNumber);
		String normalizedPlate = plateNormalizer.normalize(displayPlate);

		if (vehicleRepository.existsByNormalizedPlate(normalizedPlate)) {
			throw new DuplicateVehiclePlateException(normalizedPlate);
		}

		try {
			return vehicleRepository.saveAndFlush(new VehicleEntity(user, displayPlate, normalizedPlate));
		} catch (DataIntegrityViolationException exception) {
			if (isNormalizedPlateConflict(exception)) {
				throw new DuplicateVehiclePlateException(normalizedPlate, exception);
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public List<VehicleEntity> getVehiclesByUserId(Long userId) {
		findUser(userId);
		return vehicleRepository.findAllByUser_IdOrderByIdAsc(userId);
	}

	@Transactional(readOnly = true)
	public Optional<VehicleEntity> findByPlate(String plateNumber) {
		String normalizedPlate = plateNormalizer.normalize(plateNumber);
		return vehicleRepository.findByNormalizedPlate(normalizedPlate);
	}

	@Transactional(readOnly = true)
	public boolean plateExists(String plateNumber) {
		String normalizedPlate = plateNormalizer.normalize(plateNumber);
		return vehicleRepository.existsByNormalizedPlate(normalizedPlate);
	}

	@Transactional(readOnly = true)
	public boolean belongsToUser(Long vehicleId, Long userId) {
		return vehicleRepository.existsByIdAndUser_Id(vehicleId, userId);
	}

	private UserEntity findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException(userId));
	}

	private static String prepareDisplayPlate(String plateNumber) {
		if (plateNumber == null || plateNumber.isBlank()) {
			throw new InvalidPlateNumberException("Plate number must not be blank");
		}

		String displayPlate = plateNumber.strip();
		int displayLength = displayPlate.codePointCount(0, displayPlate.length());
		if (displayLength > MAX_DISPLAY_PLATE_LENGTH) {
			throw new InvalidPlateNumberException("Plate number must not exceed 32 characters");
		}
		return displayPlate;
	}

	private static boolean isNormalizedPlateConflict(Throwable exception) {
		Throwable currentCause = exception;
		while (currentCause != null) {
			if (currentCause instanceof ConstraintViolationException constraintViolation) {
				return UNIQUE_PLATE_CONSTRAINT.equals(constraintViolation.getConstraintName());
			}
			currentCause = currentCause.getCause();
		}
		return false;
	}
}
