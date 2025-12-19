package com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatStatusEventResponse {
    private String type; // HOLD / RELEASE
    private Long seatId;
    private Long reservationId;
}
