package com.parking.backend.common.web;

import com.parking.backend.common.exception.DuplicateVehiclePlateException;
import com.parking.backend.common.exception.EmailAlreadyExistsException;
import com.parking.backend.common.exception.InvalidCredentialsException;
import com.parking.backend.common.exception.InvalidParkingIntervalException;
import com.parking.backend.common.exception.InvalidPasswordException;
import com.parking.backend.common.exception.InvalidPlateNumberException;
import com.parking.backend.common.exception.InvalidReservationRequestException;
import com.parking.backend.common.exception.NoActiveVisitException;
import com.parking.backend.common.exception.NoAvailableParkingSpotException;
import com.parking.backend.common.exception.ParkingSpotNotFoundException;
import com.parking.backend.common.exception.ParkingVisitNotCompletedException;
import com.parking.backend.common.exception.ParkingVisitNotFoundException;
import com.parking.backend.common.exception.ReservationConflictException;
import com.parking.backend.common.exception.ReservationEntryConflictException;
import com.parking.backend.common.exception.ReservationNotFoundException;
import com.parking.backend.common.exception.UserNotFoundException;
import com.parking.backend.common.exception.VehicleNotFoundException;
import com.parking.backend.common.exception.VehicleAlreadyParkedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(this::validationMessage)
				.orElse("Request validation failed");
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleUnreadableRequest() {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is invalid");
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException exception) {
		return error(
				HttpStatus.BAD_REQUEST,
				"INVALID_REQUEST",
				"Required query parameter is missing: " + exception.getParameterName()
		);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidRequestParameter(MethodArgumentTypeMismatchException exception) {
		return error(
				HttpStatus.BAD_REQUEST,
				"INVALID_REQUEST",
				"Query parameter has an invalid value: " + exception.getName()
		);
	}

	@ExceptionHandler(InvalidParkingIntervalException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidParkingInterval(InvalidParkingIntervalException exception) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_PARKING_INTERVAL", exception.getMessage());
	}

	@ExceptionHandler(InvalidReservationRequestException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidReservationRequest(InvalidReservationRequestException exception) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION_REQUEST", exception.getMessage());
	}

	@ExceptionHandler(ReservationConflictException.class)
	ResponseEntity<ApiErrorResponse> handleReservationConflict(ReservationConflictException exception) {
		return error(HttpStatus.CONFLICT, "RESERVATION_CONFLICT", exception.getMessage());
	}

	@ExceptionHandler(VehicleAlreadyParkedException.class)
	ResponseEntity<ApiErrorResponse> handleVehicleAlreadyParked(VehicleAlreadyParkedException exception) {
		return error(HttpStatus.CONFLICT, "VEHICLE_ALREADY_PARKED", exception.getMessage());
	}

	@ExceptionHandler(NoAvailableParkingSpotException.class)
	ResponseEntity<ApiErrorResponse> handleNoAvailableParkingSpot(NoAvailableParkingSpotException exception) {
		return error(HttpStatus.CONFLICT, "NO_AVAILABLE_PARKING_SPOT", exception.getMessage());
	}

	@ExceptionHandler(ReservationEntryConflictException.class)
	ResponseEntity<ApiErrorResponse> handleReservationEntryConflict(ReservationEntryConflictException exception) {
		return error(HttpStatus.CONFLICT, "RESERVATION_ENTRY_CONFLICT", exception.getMessage());
	}

	@ExceptionHandler(NoActiveVisitException.class)
	ResponseEntity<ApiErrorResponse> handleNoActiveVisit(NoActiveVisitException exception) {
		return error(HttpStatus.CONFLICT, "NO_ACTIVE_VISIT", exception.getMessage());
	}

	@ExceptionHandler(ParkingVisitNotCompletedException.class)
	ResponseEntity<ApiErrorResponse> handleParkingVisitNotCompleted(ParkingVisitNotCompletedException exception) {
		return error(HttpStatus.CONFLICT, "PARKING_VISIT_NOT_COMPLETED", exception.getMessage());
	}

	@ExceptionHandler(InvalidPlateNumberException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidPlate(InvalidPlateNumberException exception) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_PLATE_NUMBER", exception.getMessage());
	}

	@ExceptionHandler(InvalidPasswordException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidPassword(InvalidPasswordException exception) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", exception.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
		return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage());
	}

	@ExceptionHandler(EmailAlreadyExistsException.class)
	ResponseEntity<ApiErrorResponse> handleDuplicateEmail() {
		return error(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "A user with this email already exists");
	}

	@ExceptionHandler(DuplicateVehiclePlateException.class)
	ResponseEntity<ApiErrorResponse> handleDuplicateVehicle() {
		return error(HttpStatus.CONFLICT, "VEHICLE_ALREADY_EXISTS", "Vehicle with this plate already exists");
	}

	@ExceptionHandler(UserNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleUserNotFound() {
		return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found");
	}

	@ExceptionHandler(VehicleNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleVehicleNotFound() {
		return error(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", "Vehicle was not found");
	}

	@ExceptionHandler(ParkingSpotNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleParkingSpotNotFound() {
		return error(HttpStatus.NOT_FOUND, "PARKING_SPOT_NOT_FOUND", "Parking spot was not found");
	}

	@ExceptionHandler(ParkingVisitNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleParkingVisitNotFound() {
		return error(HttpStatus.NOT_FOUND, "PARKING_VISIT_NOT_FOUND", "Parking visit was not found");
	}

	@ExceptionHandler(ReservationNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleReservationNotFound() {
		return error(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation was not found");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiErrorResponse> handleAccessDenied() {
		return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
		LOGGER.error("Unexpected request processing failure", exception);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
	}

	private String validationMessage(FieldError fieldError) {
		String defaultMessage = fieldError.getDefaultMessage();
		if (defaultMessage == null) {
			return "Request validation failed";
		}
		return fieldError.getField() + ": " + defaultMessage;
	}

	private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message));
	}
}
