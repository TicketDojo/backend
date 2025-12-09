package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.global.exception.socket.ReservationNotFoundException;
import com.ticket.dojo.backdeepfamily.global.exception.socket.SeatAlreadyHeldException;
import com.ticket.dojo.backdeepfamily.global.exception.socket.SeatNotFoundException;
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
                                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

                Seat seat = seatRepository.findById(seatId)
                                .orElseThrow(() -> new SeatNotFoundException(seatId));

                if (reservationSeatRepository.existsBySeatAndSequenceNum(seat, sequenceNum)) {
                        throw new SeatAlreadyHeldException(seatId, sequenceNum);
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
                                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

                Seat seat = seatRepository.findById(seatId)
                                .orElseThrow(() -> new SeatNotFoundException(seatId));

                reservationSeatRepository.findAllByReservation(reservation)
                                .stream()
                                .filter(rs -> rs.getSeat().equals(seat)
                                                && rs.getSequenceNum().equals(sequenceNum))
                                .findFirst()
                                .ifPresent(reservationSeatRepository::delete);
        }
}
