package com.parking.backend.reservation.mapper;

import com.parking.backend.reservation.dto.HoldResponse;
import com.parking.backend.reservation.dto.ReservationResponse;
import com.parking.backend.reservation.entity.ReservationEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReservationMapper {

	public HoldResponse toHoldResponse(ReservationEntity hold) {
		return new HoldResponse(
				hold.getId(),
				hold.getSpot().getId(),
				hold.getSpot().getCode(),
				hold.getVehicle().getId(),
				hold.getStartAt(),
				hold.getEndAt(),
				hold.getStatus(),
				hold.getHoldExpiresAt()
		);
	}

	public List<HoldResponse> toHoldResponses(List<ReservationEntity> holds) {
		return holds.stream().map(this::toHoldResponse).toList();
	}

	public ReservationResponse toResponse(ReservationEntity reservation) {
		return new ReservationResponse(
				reservation.getId(),
				reservation.getVehicle().getId(),
				reservation.getSpot().getId(),
				reservation.getSpot().getCode(),
				reservation.getStartAt(),
				reservation.getEndAt(),
				reservation.getStatus(),
				reservation.getConfirmedAt(),
				reservation.getCancelledAt()
		);
	}

	public List<ReservationResponse> toResponses(List<ReservationEntity> reservations) {
		return reservations.stream().map(this::toResponse).toList();
	}
}
