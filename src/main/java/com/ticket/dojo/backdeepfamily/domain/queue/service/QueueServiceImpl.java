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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    // 최대 동시 접속 가능 인원
    private static final int MAX_ACTIVE_USERS = 50;

    /***
     * 대기열 진입 로직
     * @param userId : 진입하는 사용자
     * @return
     */
    @Transactional
    @Override
    public QueueEnterResponse enterQueue(Long userId) {

        log.info("대기열 진입 요청 - userId {}", userId);

        // 1. User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. 유저 ID : " + userId));

        // 2. 이미 들어왔던 대기열이 있는지 확인
        Queue existing = queueRepository.findByUserAndStatusIn(user, List.of(Queue.QueueStatus.ACTIVE, Queue.QueueStatus.WAITING))
                .orElse(null);

        if(existing != null){
            log.info("기존 대기열 세션 삭제 - userId {}", userId);
            queueRepository.delete(existing);
        }

        // 3. 현재 Active 상태인 Queue 개수 조회
        int activeCount = queueRepository.countByStatus(Queue.QueueStatus.ACTIVE);

        String token = UUID.randomUUID().toString();
        Queue.QueueStatus status;
        LocalDateTime now = LocalDateTime.now();
        Queue queue;

        // 4. 50명 미만이면 바로 Active
        if (activeCount < MAX_ACTIVE_USERS) {
            status = Queue.QueueStatus.ACTIVE;
            // Active는 바로 입장하므로 순번은 의미 없지만 0으로 설정
            queue = Queue.builder().user(user).token(token).status(status).position(0).enteredAt(now).build();
            log.info("대기열 즉시 진입 (Active < 50) - Token : {}", token);
        }
        // 5. 50명 이상이면 Waiting
        else {
            status = Queue.QueueStatus.WAITING;
            int waitingCount = queueRepository.countByStatus(Queue.QueueStatus.WAITING);
            int currentPosition = waitingCount + 1;

            queue = Queue.createWaitQueue(user, token, currentPosition);
            log.info("대기열 대기 진입 (Active >= 50) - Token : {}, 순번 : {}", token, currentPosition);
        }

        // 6. DB 저장
        Queue savedQueue = queueRepository.save(queue);

        return new QueueEnterResponse(savedQueue.getToken(), savedQueue.getPosition(), savedQueue.getStatus(), savedQueue.getEnteredAt());
    }

    /***
     * 현재 상태를 반환하는 로직
     * @param token : 대기열 토큰
     * @return
     */
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
            currentPosition = queueRepository.countByStatusAndEnteredAtBefore(Queue.QueueStatus.WAITING,
                    queue.getEnteredAt()) + 1;
        }

        log.info("대기열 상태 조회 완료 - Token : {}, 순번 : {}, 상태 : {} ", token, currentPosition, queue.getStatus());

        return new QueueStatusResponse(queue.getToken(), currentPosition, queue.getStatus());
    }

    /***
     * 스케줄링에 사용되는 로직
     * 사용자가 결제 버튼을 누르지 않고 브라우저를 닫거나, 가만히 있다가 시간 초과로 만료된 경우, 서버는 이를 모르기에 주기적으로 빈자리를 채워줌
     */
    @Transactional
    @Override
    public void activateNextInQueue() {

        // 1. 현재 Active 수 확인
        int activeCount = queueRepository.countByStatus(Queue.QueueStatus.ACTIVE);
        int availableSlots = MAX_ACTIVE_USERS - activeCount;

        if (availableSlots <= 0) {
            log.debug("Active 슬롯이 꽉 찼습니다. (Active: {})", activeCount);
            return;
        }

        log.info("대기열 활성화 시작 - 빈자리 : {}명", availableSlots);

        // 2. 빈자리만큼 Waiting -> Active
        Pageable pageable = PageRequest.of(0, availableSlots);
        List<Queue> waitingQueue = queueRepository.findByStatusOrderByEnteredAtAsc(Queue.QueueStatus.WAITING, pageable);

        if (waitingQueue.isEmpty()) {
            log.info("대기 중인 사람이 없습니다.");
            return;
        }

        // 3. 각 Queue를 Active로 전환
        LocalDateTime now = LocalDateTime.now();
        for (Queue queue : waitingQueue) {
            queue.activate(now);
        }

        // 4. 저장
        queueRepository.saveAll(waitingQueue);

        log.info("대기열 활성화 완료 - 활성화된 인원 : {}명", waitingQueue.size());
    }


    /***
     * 브라우저에서 닫기 이벤트가 발생하거나, 다른 메뉴를 누르면 대기열 포기하는 로직
     * @param token
     */
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

        // 3. 즉시 대기자 입장 시도
        activateNextInQueue();
    }

    /***
     * 티켓 예매 사이트 -> 결제 페이지로 이동하면 사용될 로직
     * @param token
     */
    @Transactional
    @Override
    public void expireQueue(String token) {
        log.info("결제 진입 (만료 처리) 요청 - token : {}", token);

        Queue queue = queueRepository.findByToken(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다 : " + token));

        // Active 상태인 경우 Expired로 변경
        if (queue.getStatus() == Queue.QueueStatus.ACTIVE) {
            queue.expire();
            log.info("토큰 만료 처리 완료 (Active -> Expired) - token : {}", token);

            // 즉시 대기자 입장 시도
            activateNextInQueue();

        } else {
            log.warn("Active 상태가 아닌 토큰에 대한 만료 요청 무시 - token : {}, status : {}", token, queue.getStatus());
        }
    }
}
