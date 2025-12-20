package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Position;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.exception.QueueNotFoundException;
import com.ticket.dojo.backdeepfamily.global.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueDomainService {

    private final QueueRepository queueRepository;
    private final UserRepository userRepository;
    private final QueuePolicy queuePolicy;

    /**
     * 사용자 조회 (도메인 예외 포함)
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. 유저 ID: " + userId));
    }

    /**
     * 대기열 조회 (도메인 예외 포함)
     */
    public Queue getQueueByToken(String token) {
        return queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰: " + token));
    }

    /**
     * 기존 대기열 세션 정리
     */
    public void cleanupExistingQueue(User user) {
        queueRepository.findByUserAndStatusIn(user, List.of(QueueStatus.ACTIVE, QueueStatus.WAITING))
                .ifPresent(existingQueue -> {
                    log.info("기존 대기열 세션 삭제 - userId: {}", user.getUserId());
                    queueRepository.delete(existingQueue);
                });
    }

    /**
     * 대기열 생성 (정책에 따라)
     */
    public Queue createQueue(User user, int activeCount) {
        if (queuePolicy.canActivateImmediately(activeCount)) {
            log.info("대기열 즉시 진입 (Active < {})", queuePolicy.getMaxActiveUsers());
            return Queue.createActive(user);
        } else {
            int waitingCount = queueRepository.countByStatus(QueueStatus.WAITING);
            Position position = queuePolicy.calculateWaitingPosition(waitingCount);
            log.info("대기열 대기 진입 (Active >= {}) - 순번: {}",
                    queuePolicy.getMaxActiveUsers(), position.getValue());
            return Queue.createWaiting(user, position);
        }
    }

    /**
     * 현재 대기 순번 계산
     */
    public int calculateCurrentPosition(Queue queue) {
        if (!queue.isWaiting()) {
            return 0;
        }
        return queueRepository.countByStatusAndEnteredAtBefore(
                QueueStatus.WAITING, queue.getEnteredAt()) + 1;
    }

    /**
     * 대기 중인 큐들을 활성화
     */
    public List<Queue> activateWaitingQueues(List<Queue> waitingQueues, int availableSlots) {
        if (waitingQueues.isEmpty()) {
            log.info("활성화할 대기 중인 큐가 없습니다");
            return List.of();
        }
        List<Queue> queuesToActivate = waitingQueues.stream()
                .limit(availableSlots)
                .toList();
        queuesToActivate.forEach(queue -> {
            queue.activate();
            log.debug("큐 활성화 - Token: {}", queue.getTokenValue());
        });
        log.info("{}개의 대기열이 활성화되었습니다", queuesToActivate.size());
        return queuesToActivate;
    }

    /**
     * 특정 큐를 만료시키고 대기자를 활성화할 수 있는지 판단
     */
    public boolean canActivateNextAfterExpire(Queue queue) {
        return queue.isActive();
    }
}