package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.exception.QueueNotFoundException;
import com.ticket.dojo.backdeepfamily.global.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final QueueRepository queueRepository;
    // private final UserService userService;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public QueueEnterResponse enterQueue(Long userId) {

        log.info("대기열 진입 요청 - userId {}", userId);

        // 1. User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. 유저 ID : " + userId));

        // 2. 기존 대기열 확인 및 삭제 (중복 진입 방지)
        List<Queue> existingQueues = queueRepository.findByUser_UserIdAndStatusIn(
                userId,
                List.of(Queue.QueueStatus.WAITING, Queue.QueueStatus.ACTIVE));

        if (!existingQueues.isEmpty()) {
            log.info("기존 대기열 발견 - userId: {}, 개수: {}", userId, existingQueues.size());
            queueRepository.deleteByUser_UserIdAndStatusIn(
                    userId,
                    List.of(Queue.QueueStatus.WAITING, Queue.QueueStatus.ACTIVE));
            log.info("기존 대기열 삭제 완료 - userId: {}", userId);
        }

        // 3. 현재 Waiting 상태인 Queue 개수 조회 (삭제 후 재계산)
        int waitingCount = queueRepository.countByStatus(Queue.QueueStatus.WAITING);
        int currentPosition = waitingCount + 1;

        log.info("대기열 진입 - userId: {}, 현재 대기 인원: {}, 배정된 순번: {}", userId, waitingCount, currentPosition);

        // 4. 고유 토큰 생성
        String token = UUID.randomUUID().toString();

        // 5. Queue 엔티티 생성
        Queue queue = Queue.createWaitQueue(user, token, currentPosition);

        // 6. DB 저장
        Queue savedQueue = queueRepository.save(queue);

        log.info("대기열 진입 완료 - Token : {}, User : {}, 순번 : {}", token, userId, currentPosition);

        return new QueueEnterResponse(savedQueue.getToken(), savedQueue.getPosition(), savedQueue.getStatus(),
                savedQueue.getEnteredAt());
    }

    @Override
    public QueueStatusResponse getQueueStatus(String token) {

        log.info("대기열 상태 조회 요청 - token {}", token);

        // 1. 토큰으로 Queue 조회
        Queue queue = queueRepository.findByToken(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        // 2. 현재 Position 계산 (Waiting 상태일 때만)
        int currentPosition = 0;
        if (queue.getStatus() == Queue.QueueStatus.WAITING) {
            // 나보다 먼저 들어온 WAITING 상태의 개수 + 1
            currentPosition = queueRepository.countByStatusAndEnteredAtBefore(Queue.QueueStatus.WAITING, queue.getEnteredAt()) + 1;
        }

        log.info("대기열 상태 조회 완료 - Token : {}, 순번 : {}, 상태 : {} ", token, currentPosition, queue.getStatus());

        return new QueueStatusResponse(queue.getToken(), currentPosition, queue.getStatus(), queue.getEnteredAt(), queue.getActivatedAt(), queue.getExpiresAt());
    }

    @Override
    public void activateNextInQueue(int count) {

        log.info("대기열 활성화 시작 - 활성화할 인원 : {}명", count);

        // 1. Waiting 상태를 EnteredAt 순으로 조회 (최대 10명)
        List<Queue> waitingQueue = queueRepository.findTop10ByStatusOrderByEnteredAtAsc(Queue.QueueStatus.WAITING);

        if (waitingQueue.isEmpty()) {
            log.info("대기 중인 사람이 없습니다.");
            return;
        }

        // 2. 각 Queue를 Active로 전환
        LocalDateTime now = LocalDateTime.now();
        for (Queue queue : waitingQueue) {
            queue.activate(now);
        }

        // 3. 저장
        queueRepository.saveAll(waitingQueue);

        log.info("대기열 활성화 완료 - 활성화된 인원 : {}명", waitingQueue.size());
    }

    @Override
    public void deleteExpiredQueue() {

        log.info("만료된 Queue 정리 시작");

        // 1. 만료된 ACTIVE Queue 조회
        LocalDateTime now = LocalDateTime.now();
        List<Queue> expiredQueue = queueRepository.findByStatusAndExpiresAtBefore(Queue.QueueStatus.ACTIVE, now);

        if (expiredQueue.isEmpty()) {
            log.info("만료된 Queue가 없습니다.");
            return;
        }

        // 2. 삭제
        queueRepository.deleteAll(expiredQueue);

        log.info("만료된 Queue 정리 완료 - 삭제된 개수 : {}개", expiredQueue.size());
    }

    @Transactional
    @Override
    public void exitQueue(String token) {

        log.info("대기열 퇴장 요청 - token : {}", token);

        // 1. 토큰으로 Queue 조회
        Queue queue = queueRepository.findByToken(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다 : " + token));

        // 2. Queue 삭제
        queueRepository.delete(queue);

        log.info("대기열 퇴장 완료 - token : {}, userId : {}", token, queue.getUser().getUserId());
    }
}
