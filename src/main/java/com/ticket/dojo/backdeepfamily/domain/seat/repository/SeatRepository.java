package com.ticket.dojo.backdeepfamily.domain.seat.repository;

import com.ticket.dojo.backdeepfamily.domain.seat.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
}
