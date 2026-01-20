package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

@Slf4j
@Service("optimistic")
@RequiredArgsConstructor
public class OptimisticLockQueueFacade implements QueueService {

    private final QueueServiceOptimisticImpl queueServiceOptimistic;

    private static final int MAX_RETRY = 3;

    @Override
    public QueueEnterResponse enterQueue(Long userId) throws InterruptedException {

        log.info("대기열 진입 요청 - userId: {}", userId);

        int attempt = 0;
        while (attempt < MAX_RETRY) {
            try {
                attempt++;
                return queueServiceOptimistic.enterQueueInternal(userId);
            } catch (ConcurrencyFailureException e) {
                log.warn("충돌 발생 - 시도 {}/{}, userId: {}", attempt, MAX_RETRY, userId);
                Thread.sleep(50); // 재시도 전 백오프
            }
        }

        // 최대 재시도 초과 시 WAITING으로 폴백
        log.warn("최대 재시도 초과 - WAITING으로 폴백 처리, userId: {}", userId);
        return queueServiceOptimistic.createWaitingFallback(userId);
    }

    @Override
    public com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse getQueueStatus(String token) {
        return queueServiceOptimistic.getQueueStatus(token);
    }

    @Override
    public void activateNextInQueue() {
        queueServiceOptimistic.activateNextInQueue();
    }

    @Override
    public void exitQueue(String token) {
        queueServiceOptimistic.exitQueue(token);
    }

    @Override
    public void expireQueue(String token) {
        queueServiceOptimistic.expireQueue(token);
    }
}
