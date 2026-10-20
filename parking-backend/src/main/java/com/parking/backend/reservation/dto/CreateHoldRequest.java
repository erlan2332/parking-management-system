package com.parking.backend.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record CreateHoldRequest(
		@NotNull @Positive Long vehicleId,
		@NotNull @Positive Long spotId,
		@NotNull OffsetDateTime startAt,
		@NotNull OffsetDateTime endAt
) {
}
