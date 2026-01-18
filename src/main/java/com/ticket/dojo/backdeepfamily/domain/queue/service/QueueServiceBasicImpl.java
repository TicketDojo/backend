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
import com.ticket.dojo.backdeepfamily.global.lock.config.LockStrategy;
import com.ticket.dojo.backdeepfamily.global.lock.config.LockStrategyConfig;
import com.ticket.dojo.backdeepfamily.global.lock.service.NamedLockService;
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
    private final UserRepository userRepository;
    private final QueuePolicy queuePolicy;
    private final NamedLockService namedLockService;
    private final LockStrategyConfig lockStrategyConfig;

    /**
     * 대기열 진입
     * @param userId : 진입하는 사용자
     * @return
     */
    @Transactional
    @Override
    public QueueEnterResponse enterQueue(Long userId) {

        log.info("대기열 진입 요청 - userId: {}, LockStrategy: {}", userId, lockStrategyConfig.getQueueLockStrategy());

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. 유저 ID : " + userId));


        // 2. 기존 대기열 세션 정리
        queueRepository.findByUserAndStatusIn(user, List.of(QueueStatus.ACTIVE, QueueStatus.WAITING))
                .ifPresent(existingQueue -> {
                    log.info("기존 대기열 세션 삭제 - 유저 ID : {}", user.getUserId());
                    queueRepository.delete(existingQueue);
                });

        // 3. 락 전략에 따른 큐 생성
        Queue createQueue = createQueueWithStrategy(user);

        // 4. 저장
        Queue savedQueue = queueRepository.save(createQueue);

        log.info("대기열 진입 완료 - Token: {}, Status: {}", savedQueue.getTokenValue(), savedQueue.getStatus());

        return QueueEnterResponse.from(savedQueue);
    }

    /**
     * 락 전략에 따른 Queue 생성
     *
     * @param user 대기열에 진입하는 사용자
     * @return 생성된 Queue
     */
    private Queue createQueueWithStrategy(User user) {
        LockStrategy strategy = lockStrategyConfig.getQueueLockStrategy();

        return switch (strategy) {
            case NAMED -> createQueueWithNamedLock(user);
            case PESSIMISTIC -> createQueueWithPessimisticLock(user);
            case NONE -> createQueueWithoutLock(user);
            default -> {
                log.warn("지원하지 않는 락 전략입니다: {}. NONE으로 대체합니다.", strategy);
                yield createQueueWithoutLock(user);
            }
        };
    }

    /**
     * Named Lock으로 보호되는 Queue 생성
     */
    private Queue createQueueWithNamedLock(User user) {
        return namedLockService.executeWithLock("queue:assign:position", () -> {
            return determineQueueStatus(user);
        });
    }

    /**
     * Pessimistic Lock으로 보호되는 Queue 생성
     * countByStatus에 FOR UPDATE 락을 적용
     */
    private Queue createQueueWithPessimisticLock(User user) {
        // countByStatusWithLock은 FOR UPDATE가 적용된 쿼리
        int activeCount = queueRepository.countByStatusWithLock(QueueStatus.ACTIVE);
        return determineQueueStatusByCount(user, activeCount);
    }

    /**
     * 락 없이 Queue 생성 (베이스라인)
     */
    private Queue createQueueWithoutLock(User user) {
        return determineQueueStatus(user);
    }

    /**
     * 활성 사용자 수에 따라 Queue 상태 결정
     */
    private Queue determineQueueStatus(User user) {
        int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);
        return determineQueueStatusByCount(user, activeCount);
    }

    /**
     * 주어진 활성 사용자 수로 Queue 상태 결정
     */
    private Queue determineQueueStatusByCount(User user, int activeCount) {
        if (queuePolicy.canActivateImmediately(activeCount)) {
            log.info("대기열 즉시 진입 (Active < {})", queuePolicy.getMaxActiveUsers());
            return Queue.createActive(user);
        } else {
            log.info("대기열 대기 진입 (Active >= {})", queuePolicy.getMaxActiveUsers());
            return Queue.createWaiting(user);
        }
    }

    /**
     * 현재 대기열 상태 조회
     * @param token : 대기열 토큰
     * @return
     */
    @Transactional(readOnly = true)
    @Override
    public QueueStatusResponse getQueueStatus(String token) {

        log.info("대기열 상태 조회 요청 - token: {}", token);

        // 1. 대기열 조회
        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        // 2. 현재 대기 순번 계산
        int currentPosition = 0;

        if(queue.isWaiting()){
            currentPosition = queueRepository.countByStatusAndEnteredAtBefore(QueueStatus.WAITING, queue.getEnteredAt()) + 1;
        }

        log.info("대기열 상태 조회 완료 - Token: {}, Position: {}, Status: {}", token, currentPosition, queue.getStatus());

        return QueueStatusResponse.of(queue, currentPosition);
    }

    /**
     * 다음 사용자 활성화
     *
     * Scheduler와 exitQueue()가 동시에 호출할 수 있으므로
     * Named Lock으로 동시성을 제어합니다.
     */
    @Transactional
    @Override
    public void activateNextInQueue() {
        LockStrategy strategy = lockStrategyConfig.getQueueLockStrategy();

        switch (strategy) {
            case NAMED -> activateNextWithNamedLock();
            case PESSIMISTIC -> activateNextWithPessimisticLock();
            default -> activateNextWithoutLock();
        }
    }

    private void activateNextWithNamedLock() {
        namedLockService.executeWithLock("queue:activate:slots", this::doActivateNext);
    }

    private void activateNextWithPessimisticLock() {
        // 비관적 락은 countByStatusWithLock에서 처리
        doActivateNext();
    }

    private void activateNextWithoutLock() {
        doActivateNext();
    }

    private void doActivateNext() {
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

    /**
     * 대기열 퇴장
     * @param token
     */
    @Transactional
    @Override
    public void exitQueue(String token) {

        log.info("대기열 퇴장 요청 - token: {}", token);

        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        queueRepository.delete(queue);

        log.info("대기열 퇴장 완료 - token: {}, userId: {}", token, queue.getUser().getUserId());

        activateNextInQueue();
    }

    @Transactional
    @Override
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
