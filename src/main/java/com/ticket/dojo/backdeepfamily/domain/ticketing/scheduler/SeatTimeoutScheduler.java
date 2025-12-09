package com.ticket.dojo.backdeepfamily.domain.ticketing.scheduler;

import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.SeatStatusEventResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.SeatTimeoutNotification;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
/// todo: sendToUser JWT 도입 후 테스트
public class SeatTimeoutScheduler {
    private final ReservationSeatRepository reservationSeatRepository;
    private final ReservationRepository reservationRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @Scheduled(fixedRate = 2000) // 2초
    @Transactional
    public void releaseExpiredSeats() {
        LocalDateTime now = LocalDateTime.now();

        List<ReservationSeat> expiredSeats = reservationSeatRepository.findAllExpiredAtBefore(now);

        if (expiredSeats.isEmpty()) {
            return;
        }

        // Reservation별로 그룹화
        Map<Reservation, List<ReservationSeat>> seatsByReservation = expiredSeats.stream()
                .collect(Collectors.groupingBy(ReservationSeat::getReservation));

        // 각 Reservation 처리
        seatsByReservation.forEach((reservation, seats) -> {
            Long sequenceNum = seats.get(0).getSequenceNum();
            List<Long> seatIds = seats.stream()
                    .map(rs -> rs.getSeat().getId())
                    .collect(Collectors.toList());

            // 모든 사용자에게 좌석 해제 이벤트 브로드캐스트
            seats.forEach(seat -> {
                simpMessagingTemplate.convertAndSend(
                        "/sub/round/" + sequenceNum + "/seats",
                        SeatStatusEventResponse.builder()
                                .type("RELEASE")
                                .seatId(seat.getSeat().getId())
                                .reservationId(reservation.getId())
                                .build());
            });

            // 해당 사용자에게 개인 타임아웃 알림 전송
            String userEmail = reservation.getUser().getEmail();
            String reservationStatus = reservation.getReservationState().name();

            // PAYING 상태인 경우 TIMEOUT으로 변경 -> 결제 중단해야됨
            if (reservation.getReservationState() == Reservation.ReservationState.PAYING) {
                reservation.changeState(Reservation.ReservationState.TIMEOUT);
                reservationRepository.save(reservation);
                reservationStatus = "TIMEOUT";
                log.info("결제 중 타임아웃 발생 - Reservation ID: {},", reservation.getId());
            }

            /**
             * STOMP의 convertAndSendToUser()는
             * WebSocket 세션에 바인딩된 Principal.getName()을 기준으로 동작
             */
            simpMessagingTemplate.convertAndSendToUser(
                    userEmail,
                    "/queue/timeout", //user/queue/timeout
                    SeatTimeoutNotification.builder()
                            .type("TIMEOUT")
                            .reservationId(reservation.getId())
                            .seatIds(seatIds)
                            .reservationStatus(reservationStatus)
                            .message("좌석 점유 시간이 만료되었습니다.")
                            .build());

            log.info("좌석 타임아웃 처리 완료 - Reservation ID: {}, 좌석 수: {}, 사용자: {}",
                    reservation.getId(), seatIds.size(), userEmail);
        });

        expiredSeats.forEach(reservationSeatRepository::delete);
    }
}
