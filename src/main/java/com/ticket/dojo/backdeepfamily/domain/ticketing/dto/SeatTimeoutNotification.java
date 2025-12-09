package com.ticket.dojo.backdeepfamily.domain.ticketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatTimeoutNotification {
    private String type; //TIMEOUT
    private Long reservationId;
    private List<Long> seatIds;
    /**
     * TIMEOUT이면 프론트에서 결제 중단 처리
     */
    private String reservationStatus;
    private String message;
}
