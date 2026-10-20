package com.parking.backend.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = {"vehicle", "spot"})
	@Query("SELECT reservation FROM ReservationEntity reservation " +
			"WHERE reservation.id = :id AND reservation.user.id = :userId")
	Optional<ReservationEntity> findOwnedByIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

	@EntityGraph(attributePaths = {"vehicle", "spot"})
	List<ReservationEntity> findAllByUser_IdAndStatusOrderByCreatedAtAscIdAsc(
			Long userId,
			ReservationStatus status
	);

	@EntityGraph(attributePaths = {"vehicle", "spot"})
	List<ReservationEntity> findAllByUser_IdAndStatusInOrderByStartAtAscIdAsc(
			Long userId,
			Collection<ReservationStatus> statuses
	);

	@Query("""
			SELECT reservation
			FROM ReservationEntity reservation
			WHERE (reservation.spot.id = :spotId OR reservation.vehicle.id = :vehicleId)
			  AND reservation.startAt < :endAt
			  AND reservation.endAt > :startAt
			  AND (
			      reservation.status IN :persistentStatuses
			      OR (reservation.status = :heldStatus AND reservation.holdExpiresAt > :now)
			  )
			""")
	List<ReservationEntity> findBlockingConflicts(
			@Param("spotId") Long spotId,
			@Param("vehicleId") Long vehicleId,
			@Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt,
			@Param("now") Instant now,
			@Param("heldStatus") ReservationStatus heldStatus,
			@Param("persistentStatuses") Collection<ReservationStatus> persistentStatuses
	);

	@Query("""
			SELECT reservation
			FROM ReservationEntity reservation
			WHERE reservation.startAt < :endAt
			  AND reservation.endAt > :startAt
			  AND (
			      reservation.status IN :persistentStatuses
			      OR (reservation.status = :heldStatus AND reservation.holdExpiresAt > :now)
			  )
			""")
	@EntityGraph(attributePaths = "spot")
	List<ReservationEntity> findBlockingForInterval(
			@Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt,
			@Param("now") Instant now,
			@Param("heldStatus") ReservationStatus heldStatus,
			@Param("persistentStatuses") Collection<ReservationStatus> persistentStatuses
	);

	@Modifying(flushAutomatically = true)
	@Query(value = """
			UPDATE reservations
			SET status = 'HOLD_EXPIRED'
			WHERE status = 'HELD'
			  AND hold_expires_at <= :now
			  AND start_at < :endAt
			  AND end_at > :startAt
			  AND (spot_id = :spotId OR vehicle_id = :vehicleId)
			""", nativeQuery = true)
	int expireConflictingHolds(
			@Param("spotId") Long spotId,
			@Param("vehicleId") Long vehicleId,
			@Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt,
			@Param("now") Instant now
	);

	@Modifying(flushAutomatically = true)
	@Query(value = """
			UPDATE reservations
			SET status = 'HOLD_EXPIRED'
			WHERE status = 'HELD' AND hold_expires_at <= :now
			""", nativeQuery = true)
	int expireAllHolds(@Param("now") Instant now);

	@Modifying(flushAutomatically = true)
	@Query(value = """
			UPDATE reservations
			SET status = 'HOLD_EXPIRED'
			WHERE user_id = :userId
			  AND status = 'HELD'
			  AND hold_expires_at <= :now
			""", nativeQuery = true)
	int expireHoldsForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
