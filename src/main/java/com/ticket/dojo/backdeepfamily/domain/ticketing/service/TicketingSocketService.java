package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

public interface TicketingSocketService {
    void holdSeat(Long seatId, Long reservationId, Long sequenceNum);

    void releaseSeat(Long reservationId, Long sequenceNum, Long seatId);
}
