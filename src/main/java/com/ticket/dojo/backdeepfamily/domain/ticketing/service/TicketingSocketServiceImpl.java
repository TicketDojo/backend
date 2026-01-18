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
import com.ticket.dojo.backdeepfamily.global.lock.config.LockStrategy;
import com.ticket.dojo.backdeepfamily.global.lock.config.LockStrategyConfig;
import com.ticket.dojo.backdeepfamily.global.lock.service.NamedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketingSocketServiceImpl implements TicketingSocketService {
        private final ReservationRepository reservationRepository;
        private final SeatRepository seatRepository;
        private final ReservationSeatRepository reservationSeatRepository;
        private final NamedLockService namedLockService;
        private final LockStrategyConfig lockStrategyConfig;
        private static final int HOLD_SECONDS = 20;

        @Override
        @Transactional
        public long holdSeat(Long seatId, Long reservationId) {
                log.info("좌석 점유 요청 - seatId: {}, reservationId: {}, LockStrategy: {}",
                        seatId, reservationId, lockStrategyConfig.getSeatLockStrategy());

                Reservation reservation = reservationRepository.findById(reservationId)
                                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

                // 락 전략에 따라 좌석 조회
                Seat seat = findSeatWithStrategy(seatId);

                if (reservationSeatRepository.existsBySeat(seat)) {
                        throw new SeatAlreadyHeldException(seatId);
                }

                ReservationSeat reservationSeat = ReservationSeat.createReservationSeat(seat, reservation);

                reservationSeatRepository.save(reservationSeat);

            return reservation.getSequenceNum();
        }

        /**
         * 락 전략에 따라 좌석 조회
         */
        private Seat findSeatWithStrategy(Long seatId) {
                LockStrategy strategy = lockStrategyConfig.getSeatLockStrategy();

                return switch (strategy) {
                        case PESSIMISTIC -> seatRepository.findByIdWithPessimisticLock(seatId)
                                        .orElseThrow(() -> new SeatNotFoundException(seatId));
                        case NAMED -> findSeatWithNamedLock(seatId);
                        case OPTIMISTIC, NONE -> seatRepository.findById(seatId)
                                        .orElseThrow(() -> new SeatNotFoundException(seatId));
                };
        }

        /**
         * Named Lock으로 좌석 조회
         */
        private Seat findSeatWithNamedLock(Long seatId) {
                return namedLockService.executeWithLock("seat:hold:" + seatId, () ->
                        seatRepository.findById(seatId)
                                .orElseThrow(() -> new SeatNotFoundException(seatId))
                );
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
