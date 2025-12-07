package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;

public interface QueueService {

    /***
     * 대기열 진입
     * @param userId : 진입하는 사용자
     * @return : 대기열 진입 응답 (토큰, 순번, 상태, 진입 시간)
     */
    QueueEnterResponse enterQueue(Long userId);
}
