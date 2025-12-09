package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

public interface TicketingSocketService {
    void holdSeat(Long seatId, Long reservationId);

    void releaseSeat(Long reservationId, Long seatId);
}
