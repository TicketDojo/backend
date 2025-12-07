package com.ticket.dojo.backdeepfamily.domain.queue.dto.response;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;

import java.time.LocalDateTime;

public class QueueStatusResponse {
    String token;
    int position; // 현재 대기 순번
    Queue.QueueStatus status; // 현재 상태
    LocalDateTime activateAt; // ACTIVE 인 경우
    LocalDateTime expiresAt; // ACTIVE 인 경우
}
