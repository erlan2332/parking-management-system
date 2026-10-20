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

	@EntityGraph(attributePaths = "spot")
	List<ReservationEntity> findAllByVehicle_IdAndStatusOrderByStartAtAscIdAsc(
			Long vehicleId,
			ReservationStatus status
	);

	@Query("""
			SELECT new com.parking.backend.reservation.ReservationEntryTarget(
			    reservation.id,
			    reservation.spot.id,
			    reservation.startAt,
			    reservation.endAt
			)
			FROM ReservationEntity reservation
			WHERE reservation.vehicle.id = :vehicleId
			  AND reservation.status = :activeStatus
			ORDER BY reservation.startAt ASC, reservation.id ASC
			""")
	List<ReservationEntryTarget> findActiveEntryTargetsByVehicleId(
			@Param("vehicleId") Long vehicleId,
			@Param("activeStatus") ReservationStatus activeStatus
	);

	@Query("""
			SELECT CASE WHEN COUNT(reservation) > 0 THEN true ELSE false END
			FROM ReservationEntity reservation
			WHERE reservation.vehicle.id = :vehicleId
			  AND reservation.status = :expiredStatus
			  AND reservation.startAt <= :now
			  AND reservation.endAt > :now
			""")
	boolean existsCurrentNoShowForVehicle(
			@Param("vehicleId") Long vehicleId,
			@Param("expiredStatus") ReservationStatus expiredStatus,
			@Param("now") Instant now
	);

	@Query("""
			SELECT CASE WHEN COUNT(reservation) > 0 THEN true ELSE false END
			FROM ReservationEntity reservation
			WHERE reservation.vehicle.id = :vehicleId
			  AND (
			      reservation.status IN :persistentStatuses
			      OR (
			          reservation.status = :activeStatus
			          AND reservation.endAt > :now
			          AND reservation.startAt > :activeStartCutoff
			      )
			      OR (reservation.status = :heldStatus AND reservation.holdExpiresAt > :now)
			  )
			""")
	boolean existsBlockingObligationForVehicle(
			@Param("vehicleId") Long vehicleId,
			@Param("now") Instant now,
			@Param("activeStartCutoff") Instant activeStartCutoff,
			@Param("heldStatus") ReservationStatus heldStatus,
			@Param("activeStatus") ReservationStatus activeStatus,
			@Param("persistentStatuses") Collection<ReservationStatus> persistentStatuses
	);

	@Query("""
			SELECT CASE WHEN COUNT(reservation) > 0 THEN true ELSE false END
			FROM ReservationEntity reservation
			WHERE reservation.spot.id = :spotId
			  AND (
			      reservation.status IN :persistentStatuses
			      OR (
			          reservation.status = :activeStatus
			          AND reservation.endAt > :now
			          AND reservation.startAt > :activeStartCutoff
			      )
			      OR (reservation.status = :heldStatus AND reservation.holdExpiresAt > :now)
			  )
			""")
	boolean existsBlockingObligationForSpot(
			@Param("spotId") Long spotId,
			@Param("now") Instant now,
			@Param("activeStartCutoff") Instant activeStartCutoff,
			@Param("heldStatus") ReservationStatus heldStatus,
			@Param("activeStatus") ReservationStatus activeStatus,
			@Param("persistentStatuses") Collection<ReservationStatus> persistentStatuses
	);

	@Query("""
			SELECT reservation
			FROM ReservationEntity reservation
			WHERE (reservation.spot.id = :spotId OR reservation.vehicle.id = :vehicleId)
			  AND reservation.startAt < :endAt
			  AND reservation.endAt > :startAt
			  AND (
			      reservation.status IN :persistentStatuses
			      OR (
			          reservation.status = :activeStatus
			          AND reservation.endAt > :now
			          AND reservation.startAt > :activeStartCutoff
			      )
			      OR (reservation.status = :heldStatus AND reservation.holdExpiresAt > :now)
			  )
			""")
	List<ReservationEntity> findBlockingConflicts(
			@Param("spotId") Long spotId,
			@Param("vehicleId") Long vehicleId,
			@Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt,
			@Param("now") Instant now,
			@Param("activeStartCutoff") Instant activeStartCutoff,
			@Param("heldStatus") ReservationStatus heldStatus,
			@Param("activeStatus") ReservationStatus activeStatus,
			@Param("persistentStatuses") Collection<ReservationStatus> persistentStatuses
	);

	@Query("""
			SELECT reservation
			FROM ReservationEntity reservation
			WHERE reservation.startAt < :endAt
			  AND reservation.endAt > :startAt
			  AND (
			      reservation.status IN :persistentStatuses
			      OR (
			          reservation.status = :activeStatus
			          AND reservation.endAt > :now
			          AND reservation.startAt > :activeStartCutoff
			      )
			      OR (reservation.status = :heldStatus AND reservation.holdExpiresAt > :now)
			  )
			""")
	@EntityGraph(attributePaths = "spot")
	List<ReservationEntity> findBlockingForInterval(
			@Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt,
			@Param("now") Instant now,
			@Param("activeStartCutoff") Instant activeStartCutoff,
			@Param("heldStatus") ReservationStatus heldStatus,
			@Param("activeStatus") ReservationStatus activeStatus,
			@Param("persistentStatuses") Collection<ReservationStatus> persistentStatuses
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE reservations reservation
			SET status = 'EXPIRED'
			WHERE status = 'ACTIVE'
			  AND (end_at <= :now OR start_at <= :activeStartCutoff)
			  AND (spot_id = :spotId OR vehicle_id = :vehicleId)
			  AND NOT EXISTS (
			      SELECT 1
			      FROM parking_visits visit
			      WHERE visit.reservation_id = reservation.id
			  )
			""", nativeQuery = true)
	int expireConflictingNoShows(
			@Param("spotId") Long spotId,
			@Param("vehicleId") Long vehicleId,
			@Param("now") Instant now,
			@Param("activeStartCutoff") Instant activeStartCutoff
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			WITH due AS (
			    SELECT id
			    FROM reservations
			    WHERE status = 'ACTIVE'
			      AND (end_at <= :now OR start_at <= :activeStartCutoff)
			      AND NOT EXISTS (
			          SELECT 1
			          FROM parking_visits visit
			          WHERE visit.reservation_id = reservations.id
			      )
			    ORDER BY id
			    LIMIT :batchSize
			    FOR UPDATE SKIP LOCKED
			)
			UPDATE reservations reservation
			SET status = 'EXPIRED'
			FROM due
			WHERE reservation.id = due.id
			  AND reservation.status = 'ACTIVE'
			""", nativeQuery = true)
	int expireDueNoShows(
			@Param("now") Instant now,
			@Param("activeStartCutoff") Instant activeStartCutoff,
			@Param("batchSize") int batchSize
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE reservations reservation
			SET status = 'EXPIRED'
			WHERE user_id = :userId
			  AND status = 'ACTIVE'
			  AND (end_at <= :now OR start_at <= :activeStartCutoff)
			  AND NOT EXISTS (
			      SELECT 1
			      FROM parking_visits visit
			      WHERE visit.reservation_id = reservation.id
			  )
			""", nativeQuery = true)
	int expireNoShowsForUser(
			@Param("userId") Long userId,
			@Param("now") Instant now,
			@Param("activeStartCutoff") Instant activeStartCutoff
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
