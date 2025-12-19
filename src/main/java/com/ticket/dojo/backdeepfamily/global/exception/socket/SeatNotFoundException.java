package com.ticket.dojo.backdeepfamily.global.exception.socket;

import lombok.Getter;

/**
 * 좌석을 찾을 수 없는 경우 발생하는 예외
 */
@Getter
public class SeatNotFoundException extends RuntimeException {
    private final Long seatId;

    public SeatNotFoundException(Long seatId) {
        super("좌석을 찾을 수 없습니다. (좌석 ID: " + seatId + ")");
        this.seatId = seatId;
    }
}
