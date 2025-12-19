package com.ticket.dojo.backdeepfamily.global.exception.socket;

import org.springframework.stereotype.Component;

@Component
public class SocketExceptionMapper {
    public String toErrorCode(Exception ex) {
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
