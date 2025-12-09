package com.ticket.dojo.backdeepfamily.global.exception.socket;

import lombok.Getter;

/**
 * 좌석이 이미 점유된 경우 발생하는 예외
 */
@Getter
public class SeatAlreadyHeldException extends RuntimeException {
    private final Long seatId;
    private final Long sequenceNum;

    public SeatAlreadyHeldException(Long seatId, Long sequenceNum) {
        super("이미 점유된 좌석입니다. (좌석 ID: " + seatId + ", 회차: " + sequenceNum + ")");
        this.seatId = seatId;
        this.sequenceNum = sequenceNum;
    }

    public SeatAlreadyHeldException(String message, Long seatId, Long sequenceNum) {
        super(message);
        this.seatId = seatId;
        this.sequenceNum = sequenceNum;
    }
}
