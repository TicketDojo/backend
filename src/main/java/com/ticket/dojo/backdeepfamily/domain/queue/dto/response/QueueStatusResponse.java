package com.ticket.dojo.backdeepfamily.domain.queue.dto.response;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusResponse {

    /**
     *  대기열 상태 조회 시 사용자에게 반환할 응답 객체
     */

    private String token;               // 대기열 토큰
    private int position;               // 현재 대기 순번
    private QueueStatus status;   // 대기열 상태 (WAITING, ACTIVE, EXPIRED)

    public static QueueStatusResponse of(Queue queue, int currentPosition) {
        return QueueStatusResponse.builder()
                .token(queue.getTokenValue())
                .position(currentPosition)
                .status(queue.getStatus())
                .build();
    }
}
