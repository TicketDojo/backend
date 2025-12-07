package com.ticket.dojo.backdeepfamily.domain.queue.dto.response;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private Queue.QueueStatus status;   // 대기열 상태 (WAITING, ACTIVE, EXPIRED)
    private LocalDateTime enteredAt;    // 진입 시간
    private LocalDateTime activatedAt;  // 활성화 시간 (ACTIVE일 때만)
    private LocalDateTime expiresAt;    // 만료 시간 (ACTIVE일 때만)
}
