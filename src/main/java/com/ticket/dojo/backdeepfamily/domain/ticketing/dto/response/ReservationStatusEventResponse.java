package com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationStatusEventResponse {
    private String type; // TIMEOUT / CONFIRMED / CANCELLED
    private Long reservationId;
}
