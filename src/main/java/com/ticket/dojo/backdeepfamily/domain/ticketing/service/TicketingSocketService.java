package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

public interface TicketingSocketService {
    long holdSeat(Long seatId, Long reservationId);

    long releaseSeat(Long reservationId, Long seatId);
}
