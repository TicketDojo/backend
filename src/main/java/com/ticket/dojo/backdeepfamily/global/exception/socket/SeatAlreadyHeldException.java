package com.ticket.dojo.backdeepfamily.global.exception.socket;

import lombok.Getter;

/**
 * 좌석이 이미 점유된 경우 발생하는 예외
 */
@Getter
public class SeatAlreadyHeldException extends RuntimeException {
    private final Long seatId;

    public SeatAlreadyHeldException(Long seatId) {
        super("이미 점유된 좌석입니다. (좌석 ID: " + seatId + ")");
        this.seatId = seatId;
    }

    public SeatAlreadyHeldException(String message, Long seatId) {
        super(message);
        this.seatId = seatId;
    }
}
