package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.exception.QueueNotFoundException;
import com.ticket.dojo.backdeepfamily.global.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueServiceOptimisticImpl {

    private final QueueRepository queueRepository;
    private final UserRepository userRepository;
    private final QueuePolicy queuePolicy;

    /**
     * 대기열 진입 (낙관적 락 - 내부 메서드)
     * Facade에서 호출됨
     */
    @Transactional
    public QueueEnterResponse enterQueueInternal(Long userId) {

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. 유저 ID : " + userId));

        // 2. 기존 대기열 세션 정리
        queueRepository.findByUserAndStatusIn(user, List.of(QueueStatus.ACTIVE, QueueStatus.WAITING))
                .ifPresent(existingQueue -> {
                    log.info("기존 대기열 세션 삭제 - 유저 ID : {}", user.getUserId());
                    queueRepository.delete(existingQueue);
                    queueRepository.flush();
                });

        // 3. 현재 활성 상태 확인 (낙관적으로 읽기 - 락 없음)
        int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);

        Queue createQueue;

        // 3.1 50명 미만이면 바로 입장 시도
        if (queuePolicy.canActivateImmediately(activeCount)) {
            log.info("대기열 즉시 진입 시도 (Active: {} < {})", activeCount, queuePolicy.getMaxActiveUsers());
            createQueue = Queue.createActive(user);
        }
        // 3.2 50명 이상이면 대기열 진입
        else {
            log.info("대기열 대기 상태 진입 (Active: {} >= {})", activeCount, queuePolicy.getMaxActiveUsers());
            createQueue = Queue.createWaiting(user);
        }

        // 4. 저장
        Queue savedQueue = queueRepository.saveAndFlush(createQueue);

        // 5. 낙관적 검증: 저장 후 다시 Active 개수 확인
        if (savedQueue.isActive()) {
            int newActiveCount = queueRepository.countByStatus(QueueStatus.ACTIVE);

            if (newActiveCount > queuePolicy.getMaxActiveUsers()) {
                // 초과 감지 → 충돌 예외 발생 (Facade에서 재시도 처리)
                log.warn("Active 초과 감지 ({}명) - 충돌 예외 발생", newActiveCount);
                throw new ConcurrencyFailureException("Active 사용자 수 초과로 인한 충돌");
            }
        }

        log.info("대기열 진입 완료 - Token: {}, Status: {}", savedQueue.getTokenValue(), savedQueue.getStatus());
        return QueueEnterResponse.from(savedQueue);
    }

    /**
     * 폴백: 무조건 WAITING으로 생성 (재시도 실패 시 호출)
     */
    @Transactional
    public QueueEnterResponse createWaitingFallback(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. 유저 ID : " + userId));

        // 기존 세션 정리
        queueRepository.findByUserAndStatusIn(user, List.of(QueueStatus.ACTIVE, QueueStatus.WAITING))
                .ifPresent(existingQueue -> {
                    queueRepository.delete(existingQueue);
                    queueRepository.flush();
                });

        Queue waitingQueue = Queue.createWaiting(user);
        Queue savedQueue = queueRepository.save(waitingQueue);

        log.info("폴백 WAITING 처리 완료 - Token: {}, Status: {}", savedQueue.getTokenValue(), savedQueue.getStatus());
        return QueueEnterResponse.from(savedQueue);
    }

    @Transactional(readOnly = true)
    public QueueStatusResponse getQueueStatus(String token) {

        log.info("대기열 상태 조회 요청 - token: {}", token);

        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        int currentPosition = 0;

        if (queue.isWaiting()) {
            currentPosition = queueRepository.countByStatusAndEnteredAtBefore(QueueStatus.WAITING, queue.getEnteredAt()) + 1;
        }

        log.info("대기열 상태 조회 완료 - Token: {}, Position: {}, Status: {}", token, currentPosition, queue.getStatus());
        return QueueStatusResponse.of(queue, currentPosition);
    }

    @Transactional
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

        if (waitingQueues.isEmpty()) {
            log.info("활성화할 대기 중인 큐가 없습니다.");
            return;
        }

        for (Queue queue : waitingQueues) {
            queue.activate();
            log.debug("큐 활성화 - Token : {}", queue.getTokenValue());
        }

        queueRepository.saveAll(waitingQueues);
        log.info("대기열 활성화 완료 - 활성화된 인원: {}명", waitingQueues.size());
    }

    @Transactional
    public void exitQueue(String token) {

        log.info("대기열 퇴장 요청 - token: {}", token);

        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        queueRepository.delete(queue);
        log.info("대기열 퇴장 완료 - token: {}, userId: {}", token, queue.getUser().getUserId());

        activateNextInQueue();
    }

    @Transactional
    public void expireQueue(String token) {

        log.info("결제 진입 (만료 처리) 요청 - token: {}", token);

        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        if (queue.isActive()) {
            queue.expire();
            log.info("토큰 만료 처리 완료 - token: {}", token);
            activateNextInQueue();
        } else {
            log.warn("Active 상태가 아닌 토큰에 대한 만료 요청 무시 - token: {}, status: {}", token, queue.getStatus());
        }
    }
}
