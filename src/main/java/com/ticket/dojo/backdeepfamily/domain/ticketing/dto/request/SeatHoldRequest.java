package com.ticket.dojo.backdeepfamily.domain.ticketing.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldRequest {
    private Long reservationId;
    private Long sequenceNum;
    private Long seatId;
}
