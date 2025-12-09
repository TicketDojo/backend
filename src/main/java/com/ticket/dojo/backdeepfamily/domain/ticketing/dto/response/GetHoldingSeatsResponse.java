package com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetHoldingSeatsResponse {
    private List<HoldingSeatDto> seats;
    private Long reservationId;
    private Long sequenceNum;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingSeatDto {
        private Long seatId;
        private String seatNumber;
    }
}
