package com.ticket.dojo.backdeepfamily.domain.queue.dto.response;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueEnterResponse {

    /**
     *  대기열 진입 시 사용자에게 반환할 응답 객체
     */

    private String token; // 대기열 고유 토큰
    private QueueStatus status; // 대기열 상태 (WAITING)
    private LocalDateTime enteredAt; // 진입 시간

    public static QueueEnterResponse from(Queue savedQueue) {
        return QueueEnterResponse.builder()
                .token(savedQueue.getTokenValue())
                .position(savedQueue.getPositionValue())
                .status(savedQueue.getStatus())
                .enteredAt(savedQueue.getEnteredAt())
                .build();
    }
}
