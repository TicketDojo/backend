package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TicketingSocketServiceImpl implements TicketingSocketService {
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private static final int HOLD_SECONDS = 20;

    @Override
    @Transactional
    public void holdSeat(Long seatId, Long reservationId, Long sequenceNum) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow();

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow();

        if (reservationSeatRepository.existsBySeatAndSequenceNum(seat, sequenceNum)) {
            throw new IllegalStateException("이미 점유된 좌석입니다.");
        }

        ReservationSeat reservationSeat = ReservationSeat.builder()
                .seat(seat)
                .reservation(reservation)
                .sequenceNum(sequenceNum)
                .expiredAt(LocalDateTime.now().plusSeconds(HOLD_SECONDS))
                .build();

        reservationSeatRepository.save(reservationSeat);
    }

    @Override
    @Transactional
    public void releaseSeat(Long reservationId, Long sequenceNum, Long seatId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow();

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow();

        reservationSeatRepository.findAllByReservation(reservation)
                .stream()
                .filter(rs -> rs.getSeat().equals(seat)
                                && rs.getSequenceNum().equals(sequenceNum)
                )
                .findFirst()
                .ifPresent(reservationSeatRepository::delete);
    }
}
