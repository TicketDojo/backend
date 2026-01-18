package com.ticket.dojo.backdeepfamily.domain.ticketing.repository;

import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllBySequenceNumAndReservationStateOrderByUpdatedAtAsc(long sequenceNum, Reservation.ReservationState reservationState);

    // 비관적 락을 사용한 예약 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "3000")})
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdWithPessimisticLock(@Param("id") Long id);
}
