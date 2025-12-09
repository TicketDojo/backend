package com.ticket.dojo.backdeepfamily.domain.ticketing.controller;

import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.request.SeatHoldRequest;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.request.SeatReleaseRequest;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.SeatStatusEventResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.service.TicketingSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
/// 클라이언트 -> 서버의 MessageMapping 설정 주소로 메세지 전송
public class TicketingSocketController {
    /// SimpMessagingTemplate: subscribe된 특정 목적지로 메시지를 브로드캐스팅하는 데 활용
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final TicketingSocketService ticketingSocketService;

    /**
     * 좌석 점유
     * /pub/seat/hold
     */
    @MessageMapping("/seat/hold")
    public void holdSeat(SeatHoldRequest request) {
        ticketingSocketService.holdSeat(request.getSeatId(), request.getReservationId(), request.getSequenceNum());

        simpMessagingTemplate.convertAndSend("/sub/round/" + request.getSequenceNum() + "/seats",
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

        ticketingSocketService.releaseSeat(
                request.getReservationId(),
                request.getSequenceNum(),
                request.getSeatId()
        );

        simpMessagingTemplate.convertAndSend(
                "/sub/round/" + request.getSequenceNum() + "/seats",
                SeatStatusEventResponse.builder()
                        .type("RELEASE")
                        .seatId(request.getSeatId())
                        .reservationId(request.getReservationId())
                        .build()
        );
    }
}
