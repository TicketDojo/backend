package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service("basic")
@RequiredArgsConstructor
public class QueueServiceBasicImpl implements QueueService {

    private final QueueRepository queueRepository;
    private final QueuePolicy queuePolicy;
    private final QueueDomainService queueDomainService;

    @Transactional
    @Override
    public QueueEnterResponse enterQueue(Long userId) {
        log.info("대기열 진입 요청 - userId: {}", userId);

        // 1. 사용자 조회
        User user = queueDomainService.getUserById(userId);

        // 2. 기존 대기열 세션 정리
        queueDomainService.cleanupExistingQueue(user);

        // 3. 현재 활성 상태 확인 및 큐 생성
        int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);

        Queue queue = queueDomainService.createQueue(user, activeCount);

        // 4. 저장
        Queue savedQueue = queueRepository.save(queue);

        log.info("대기열 진입 완료 - Token: {}, Status: {}, Position: {}", savedQueue.getTokenValue(), savedQueue.getStatus(), savedQueue.getPositionValue());

        return QueueEnterResponse.from(savedQueue);
    }

    @Transactional(readOnly = true)
    @Override
    public QueueStatusResponse getQueueStatus(String token) {

        log.info("대기열 상태 조회 요청 - token: {}", token);

        Queue queue = queueDomainService.getQueueByToken(token);

        int currentPosition = queueDomainService.calculateCurrentPosition(queue);

        log.info("대기열 상태 조회 완료 - Token: {}, Position: {}, Status: {}", token, currentPosition, queue.getStatus());

        return QueueStatusResponse.of(queue, currentPosition);
    }

    @Transactional
    @Override
    public void activateNextInQueue() {
        int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);
        int availableSlots = queuePolicy.calculateAvailableSlots(activeCount);

        if (availableSlots <= 0) {
            log.debug("활성화 가능한 슬롯이 없습니다 - Active: {}", activeCount);
            return;
        }

        log.info("대기열 활성화 시작 - 빈자리: {}명", availableSlots);
        Pageable pageable = PageRequest.of(0, availableSlots);
        List<Queue> waitingQueues = queueRepository.findByStatusOrderByEnteredAtAsc(QueueStatus.WAITING, pageable);
        List<Queue> activatedQueues = queueDomainService.activateWaitingQueues(waitingQueues, availableSlots);

        if (!activatedQueues.isEmpty()) {
            queueRepository.saveAll(activatedQueues);
            log.info("대기열 활성화 완료 - 활성화된 인원: {}명", activatedQueues.size());
        }
    }

    @Transactional
    @Override
    public void exitQueue(String token) {
        log.info("대기열 퇴장 요청 - token: {}", token);
        Queue queue = queueDomainService.getQueueByToken(token);
        queueRepository.delete(queue);
        log.info("대기열 퇴장 완료 - token: {}, userId: {}", token, queue.getUser().getUserId());
        activateNextInQueue();
    }

    @Transactional
    @Override
    public void expireQueue(String token) {
        log.info("결제 진입 (만료 처리) 요청 - token: {}", token);
        Queue queue = queueDomainService.getQueueByToken(token);
        if (queueDomainService.canActivateNextAfterExpire(queue)) {
            queue.expire();
            log.info("토큰 만료 처리 완료 - token: {}", token);
            activateNextInQueue();
        } else {
            log.warn("Active 상태가 아닌 토큰에 대한 만료 요청 무시 - token: {}, status: {}", token, queue.getStatus());
        }
    }
}
