package com.ticket.dojo.backdeepfamily.domain.ticketing.repository;

import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
