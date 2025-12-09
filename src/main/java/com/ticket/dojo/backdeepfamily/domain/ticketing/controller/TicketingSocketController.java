package com.ticket.dojo.backdeepfamily.domain.ticketing.controller;

import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.request.SeatHoldRequest;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.request.SeatReleaseRequest;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.SocketError;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.SeatStatusEventResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.global.exception.socket.ReservationNotFoundException;
import com.ticket.dojo.backdeepfamily.global.exception.socket.SeatAlreadyHeldException;
import com.ticket.dojo.backdeepfamily.global.exception.socket.SeatNotFoundException;
import com.ticket.dojo.backdeepfamily.domain.ticketing.service.TicketingSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TicketingSocketController {
        private final SimpMessagingTemplate simpMessagingTemplate;
        private final TicketingSocketService ticketingSocketService;
        private final ReservationRepository reservationRepository;

        /**
         * 좌석 점유
         * /pub/seat/hold
         */
        @MessageMapping("/seat/hold")
        public void holdSeat(SeatHoldRequest request) {
                // sequenceNum 파라미터 제거
                ticketingSocketService.holdSeat(request.getSeatId(), request.getReservationId());

                // Reservation에서 sequenceNum 조회
                Reservation reservation = reservationRepository.findById(request.getReservationId())
                                .orElseThrow(() -> new ReservationNotFoundException(request.getReservationId()));

                simpMessagingTemplate.convertAndSend("/sub/round/" + reservation.getSequenceNum() + "/seats",
                                SeatStatusEventResponse.builder()
                                                .type("HOLD")
                                                .seatId(request.getSeatId())
                                                .reservationId(request.getReservationId())
                                                .build());
        }

        /**
         * 좌석 해제
         * /pub/seat/release
         */
        @MessageMapping("/seat/release")
        public void releaseSeat(SeatReleaseRequest request) {
                // sequenceNum 파라미터 제거
                ticketingSocketService.releaseSeat(request.getReservationId(), request.getSeatId());

                // Reservation에서 sequenceNum 조회
                Reservation reservation = reservationRepository.findById(request.getReservationId())
                                .orElseThrow(() -> new ReservationNotFoundException(request.getReservationId()));

                simpMessagingTemplate.convertAndSend(
                                "/sub/round/" + reservation.getSequenceNum() + "/seats",
                                SeatStatusEventResponse.builder()
                                                .type("RELEASE")
                                                .seatId(request.getSeatId())
                                                .reservationId(request.getReservationId())
                                                .build());
        }

        /**
         * 웹소켓 예외 처리 - 모든 RuntimeException 통합 처리
         * 
         * @SendToUser: 해당 사용자에게만 에러 메시지 전송 (/user/queue/errors)
         */
        @MessageExceptionHandler(RuntimeException.class)
        @SendToUser("/queue/errors")
        public SocketError handleRuntimeException(RuntimeException ex) {
                log.error("웹소켓 처리 중 예외 발생: {}", ex.getMessage());

                SocketError.SocketErrorBuilder builder = SocketError.builder()
                                .type("ERROR")
                                .message(ex.getMessage());

                // 예외 타입별 errorCode 및 추가 정보 설정
                if (ex instanceof SeatAlreadyHeldException) {
                        SeatAlreadyHeldException seatEx = (SeatAlreadyHeldException) ex;
                        builder.errorCode("SEAT_ALREADY_HELD")
                                        .seatId(seatEx.getSeatId());
                } else if (ex instanceof SeatNotFoundException) {
                        SeatNotFoundException seatEx = (SeatNotFoundException) ex;
                        builder.errorCode("SEAT_NOT_FOUND")
                                        .seatId(seatEx.getSeatId());
                } else if (ex instanceof ReservationNotFoundException) {
                        ReservationNotFoundException reservationEx = (ReservationNotFoundException) ex;
                        builder.errorCode("RESERVATION_NOT_FOUND")
                                        .reservationId(reservationEx.getReservationId());
                } else {
                        // 기타 예외는 일반 메시지로 처리
                        builder.errorCode("INTERNAL_ERROR")
                                        .message("처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                }

                return builder.build();
        }
}
