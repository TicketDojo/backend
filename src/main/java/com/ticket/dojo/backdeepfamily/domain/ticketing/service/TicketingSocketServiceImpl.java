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

@Service
@RequiredArgsConstructor
public class TicketingSocketServiceImpl implements TicketingSocketService {
        private final ReservationRepository reservationRepository;
        private final SeatRepository seatRepository;
        private final ReservationSeatRepository reservationSeatRepository;
        private static final int HOLD_SECONDS = 20;

        @Override
        @Transactional
        public long holdSeat(Long seatId, Long reservationId) {
                Reservation reservation = reservationRepository.findById(reservationId)
                                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

                Seat seat = seatRepository.findByIdWithPessimisticLock(seatId)
                                .orElseThrow(() -> new SeatNotFoundException(seatId));

                if (reservationSeatRepository.existsBySeat(seat)) {
                        throw new SeatAlreadyHeldException(seatId);
                }

                ReservationSeat reservationSeat = ReservationSeat.createReservationSeat(seat, reservation);

                reservationSeatRepository.save(reservationSeat);

            return reservation.getSequenceNum();
        }

        @Override
        @Transactional
        public long releaseSeat(Long reservationId, Long seatId) {
                Reservation reservation = reservationRepository.findById(reservationId)
                                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

                Seat seat = seatRepository.findById(seatId)
                                .orElseThrow(() -> new SeatNotFoundException(seatId));

                reservationSeatRepository.findAllByReservation(reservation)
                                .stream()
                                .filter(rs -> rs.getSeat().equals(seat))
                                .findFirst()
                                .ifPresent(reservationSeatRepository::delete);

            return reservation.getSequenceNum();
        }
}
