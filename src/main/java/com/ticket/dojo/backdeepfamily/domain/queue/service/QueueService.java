package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;

public interface QueueService {

    /***
     * 대기열 진입
     * @param userId : 진입하는 사용자
     * @return : 대기열 진입 응답 (토큰, 순번, 상태, 진입 시간)
     */
    QueueEnterResponse enterQueue(Long userId);

    /***
     * 대기열 상태 조회
     * @param token : 대기열 토큰
     * @return : 대기열 상태 응답 (토큰, 순번, 상태, 진입, 활성시간, 만료시간)
     */
    QueueStatusResponse getQueueStatus(String token);
}
