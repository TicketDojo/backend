package com.ticket.dojo.backdeepfamily.domain.ticketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 웹소켓 에러 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocketError {
    //ERROR
    private String type;
    private String message;
    private String errorCode;
    private Long seatId;
    private Long reservationId;
}
