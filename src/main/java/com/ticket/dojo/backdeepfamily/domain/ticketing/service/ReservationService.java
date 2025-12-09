package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetHoldingSeatsResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetRankingResponse;

public interface ReservationService {
    void enterPaying(Long reservationId);

    GetHoldingSeatsResponse enterTicketing(Long userId);

    void completePaying(Long reservationId, Long userId);

    GetRankingResponse getRanking(Long reservationId);

    void cancelReservation(Long reservationId, Long userId);
}
