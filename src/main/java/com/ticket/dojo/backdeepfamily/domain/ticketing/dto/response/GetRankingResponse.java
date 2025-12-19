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
public class GetRankingResponse {
    private List<RankDto> ranks;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankDto {
        private String name;
        private String completedAt;
    }
}
