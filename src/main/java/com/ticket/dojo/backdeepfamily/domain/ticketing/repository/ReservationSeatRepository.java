package com.ticket.dojo.backdeepfamily.domain.ticketing.repository;

import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {
    boolean existsBySeat(Seat seat);

    List<ReservationSeat> findAllByReservation(Reservation reservation);

    List<ReservationSeat> findAllByExpiredAtBefore(LocalDateTime now);
}
