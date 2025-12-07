package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService{

    private final QueueRepository queueRepository;
    //private final UserService userService;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public QueueEnterResponse enterQueue(Long userId) {

        log.info("대기열 진입 요청 - userId {}", userId);

        // 1. User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다." + userId));

        // 2. 현재 Waiting 상태인 Queue 개수 조회
        int waitingCount = queueRepository.countByStatus(Queue.QueueStatus.WAITING);
        int currentPosition = waitingCount + 1;

        log.info("현재 대기 중인 사람 : {}, 내 순번 {} : {}번 째", waitingCount, userId, currentPosition);

        // 3. 고유 토큰 생성
        String token = UUID.randomUUID().toString();

        // 4. Queue 엔티티 생성
        Queue queue = Queue.createWaitQueue(user, token, currentPosition);

        // 5. DB 저장
        Queue savedQueue = queueRepository.save(queue);

        log.info("대기열 진입 완료 - Token : {}, User : {}, 순번 : {}", token, userId, currentPosition);

        return new QueueEnterResponse(savedQueue.getToken(), savedQueue.getPosition(), savedQueue.getStatus(), savedQueue.getEnteredAt());
    }
}
