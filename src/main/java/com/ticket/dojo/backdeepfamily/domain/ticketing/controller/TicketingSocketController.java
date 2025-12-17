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

    /**
     * 좌석 점유
     * /pub/seat/hold
     */
    @MessageMapping("/seat/hold")
    public void holdSeat(SeatHoldRequest request) {
        long sequenceNum = ticketingSocketService.holdSeat(request.getSeatId(), request.getReservationId());

        simpMessagingTemplate.convertAndSend("/sub/round/" + sequenceNum + "/seats",
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
        long sequenceNum = ticketingSocketService.releaseSeat(request.getReservationId(), request.getSeatId());

        simpMessagingTemplate.convertAndSend(
                "/sub/round/" + sequenceNum + "/seats",
                SeatStatusEventResponse.builder()
                        .type("RELEASE")
                        .seatId(request.getSeatId())
                        .reservationId(request.getReservationId())
                        .build());
    }

    /**
     * 웹소켓 예외 처리
     *
     * @SendToUser: 해당 사용자에게만 에러 메시지 전송 (/user/queue/errors)
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public SocketError handleException(Exception ex) {
        log.error("WebSocket 예외 발생: {}", ex.getMessage());

        String errorCode = getErrorCode(ex);

        return SocketError.builder()
                .errorCode(errorCode)
                .message(ex.getMessage())
                .build();
    }

    /**
     * 예외 타입에 따른 에러 코드 반환
     */
    private String getErrorCode(Exception ex) {
        if (ex instanceof SeatAlreadyHeldException) {
            return "SEAT_ALREADY_HELD";
        }
        if (ex instanceof SeatNotFoundException) {
            return "SEAT_NOT_FOUND";
        }
        if (ex instanceof ReservationNotFoundException) {
            return "RESERVATION_NOT_FOUND";
        }
        return "INTERNAL_ERROR";
    }
}
