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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service("pessimistic")
@RequiredArgsConstructor
public class QueueServicePessimisticImpl implements QueueService {

    private final QueueRepository queueRepository;
    private final UserRepository userRepository;
    private final QueuePolicy queuePolicy;

    /**
     * 대기열 진입 (비관적 락 적용)
     * - countByStatusWithLock()으로 ACTIVE 상태 조회 시 비관적 락 획득
     * - 다른 트랜잭션은 락이 해제될 때까지 대기
     * @param userId : 진입하는 사용자
     * @return QueueEnterResponse
     */
    @Transactional
    @Override
    public QueueEnterResponse enterQueue(Long userId) {

        log.info("[Pessimistic] 대기열 진입 요청 - userId: {}", userId);

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. 유저 ID : " + userId));

        // 2. 기존 대기열 세션 정리
        queueRepository.findByUserAndStatusIn(user, List.of(QueueStatus.ACTIVE, QueueStatus.WAITING))
                .ifPresent(existingQueue -> {
                    log.info("기존 대기열 세션 삭제 - 유저 ID : {}", user.getUserId());
                    queueRepository.delete(existingQueue);
                });

        // 3. 비관적 락으로 현재 활성 상태 확인
        // PESSIMISTIC_WRITE 락으로 다른 트랜잭션이 동시에 count를 읽지 못하게 함
        int activeCount = queueRepository.countByStatusWithLock(QueueStatus.ACTIVE);
        
        Queue createQueue;

        // 3.1 50명 미만이면 바로 입장
        if (queuePolicy.canActivateImmediately(activeCount)) {
            log.info("대기열 즉시 진입 (Active: {} < {})", activeCount, queuePolicy.getMaxActiveUsers());
            createQueue = Queue.createActive(user);
        }
        // 3.2 50명 이상이면 대기열 진입
        else {
            log.info("대기열 대기 상태 진입 (Active: {} >= {})", activeCount, queuePolicy.getMaxActiveUsers());
            createQueue = Queue.createWaiting(user);
        }

        // 4. 저장
        Queue savedQueue = queueRepository.save(createQueue);

        log.info("대기열 진입 완료 - Token: {}, Status: {}", savedQueue.getTokenValue(), savedQueue.getStatus());

        return QueueEnterResponse.from(savedQueue);
    }

    /**
     * 현재 대기열 상태 조회
     * @param token : 대기열 토큰
     * @return QueueStatusResponse
     */
    @Transactional(readOnly = true)
    @Override
    public QueueStatusResponse getQueueStatus(String token) {

        log.info("[Pessimistic] 대기열 상태 조회 요청 - token: {}", token);

        // 1. 대기열 조회
        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        // 2. 현재 대기 순번 계산
        int currentPosition = 0;

        if (queue.isWaiting()) {
            currentPosition = queueRepository.countByStatusAndEnteredAtBefore(QueueStatus.WAITING, queue.getEnteredAt()) + 1;
        }

        log.info("[Pessimistic] 대기열 상태 조회 완료 - Token: {}, Position: {}, Status: {}", token, currentPosition, queue.getStatus());

        return QueueStatusResponse.of(queue, currentPosition);
    }

    /**
     * 다음 사용자 활성화
     */
    @Transactional
    @Override
    public void activateNextInQueue() {

        int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);
        int availableSlots = queuePolicy.calculateAvailableSlots(activeCount);

        // 1. 활성 가능한 슬릇이 없으면
        if (availableSlots <= 0) {
            log.debug("[Pessimistic] 활성화 가능한 슬롯이 없습니다 - Active: {}", activeCount);
            return;
        }

        log.info("[Pessimistic] 대기열 활성화 시작 - 빈자리: {}명", availableSlots);
        Pageable pageable = PageRequest.of(0, availableSlots);

        // 2. 대기중인 입장 순 대기열 조회
        List<Queue> waitingQueues = queueRepository.findByStatusOrderByEnteredAtAsc(QueueStatus.WAITING, pageable);

        // 3. 대기중인 큐 활성 가능한 개수만큼 활성화
        if (waitingQueues.isEmpty()) {
            log.info("[Pessimistic] 활성화할 대기 중인 큐가 없습니다.");
            return;
        }

        for (Queue queue : waitingQueues) {
            queue.activate();
            log.debug("[Pessimistic] 큐 활성화 - Token : {}", queue.getTokenValue());
        }

        queueRepository.saveAll(waitingQueues);

        log.info("[Pessimistic] 대기열 활성화 완료 - 활성화된 인원: {}명", waitingQueues.size());
    }

    /**
     * 대기열 퇴장
     * @param token
     */
    @Transactional
    @Override
    public void exitQueue(String token) {

        log.info("[Pessimistic] 대기열 퇴장 요청 - token: {}", token);

        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        queueRepository.delete(queue);

        log.info("[Pessimistic] 대기열 퇴장 완료 - token: {}, userId: {}", token, queue.getUser().getUserId());

        activateNextInQueue();
    }

    @Transactional
    @Override
    public void expireQueue(String token) {

        log.info("[Pessimistic] 결제 진입 (만료 처리) 요청 - token: {}", token);

        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        if (queue.isActive()) {
            queue.expire();
            log.info("[Pessimistic] 토큰 만료 처리 완료 - token: {}", token);
            activateNextInQueue();
        } else {
            log.warn("[Pessimistic] Active 상태가 아닌 토큰에 대한 만료 요청 무시 - token: {}, status: {}", token, queue.getStatus());
        }
    }
}
