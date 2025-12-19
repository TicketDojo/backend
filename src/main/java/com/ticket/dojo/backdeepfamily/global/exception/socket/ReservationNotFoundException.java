package com.ticket.dojo.backdeepfamily.global.exception.socket;

import lombok.Getter;

/**
 * 예약을 찾을 수 없는 경우 발생하는 예외
 */
@Getter
public class ReservationNotFoundException extends RuntimeException {
    private final Long reservationId;

    public ReservationNotFoundException(Long reservationId) {
        super("예약을 찾을 수 없습니다. (예약 ID: " + reservationId + ")");
        this.reservationId = reservationId;
    }
}
