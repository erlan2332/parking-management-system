package com.parking.backend.common.web;

import com.parking.backend.common.exception.DuplicateVehiclePlateException;
import com.parking.backend.common.exception.EmailAlreadyExistsException;
import com.parking.backend.common.exception.InvalidCredentialsException;
import com.parking.backend.common.exception.InvalidPasswordException;
import com.parking.backend.common.exception.InvalidPlateNumberException;
import com.parking.backend.common.exception.UserNotFoundException;
import com.parking.backend.common.exception.VehicleNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
