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
@Service("synchronized")
@RequiredArgsConstructor
public class QueueServiceSynchronizedImpl implements QueueService {

    private final QueueRepository queueRepository;
    private final UserRepository userRepository;
    private final QueuePolicy queuePolicy;

    @Override
    public synchronized QueueEnterResponse enterQueue(Long userId) {

        log.info("[Synchronized] 대기열 진입 요청 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. 유저 ID : " + userId));

        queueRepository.findByUserAndStatusIn(user, List.of(QueueStatus.ACTIVE, QueueStatus.WAITING))
                .ifPresent(existingQueue -> {
                    queueRepository.delete(existingQueue);
                });

        int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);
        Queue createQueue;

        if(queuePolicy.canActivateImmediately(activeCount)){
            createQueue = Queue.createActive(user);
        } else {
            createQueue = Queue.createWaiting(user);
        }

        Queue savedQueue = queueRepository.save(createQueue);

        return QueueEnterResponse.from(savedQueue);
    }

    @Transactional(readOnly = true)
    @Override
    public QueueStatusResponse getQueueStatus(String token) {
        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        int currentPosition = 0;
        if(queue.isWaiting()){
            currentPosition = queueRepository.countByStatusAndEnteredAtBefore(QueueStatus.WAITING, queue.getEnteredAt()) + 1;
        }

        return QueueStatusResponse.of(queue, currentPosition);
    }

    @Override
    public synchronized void activateNextInQueue() {
        int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);
        int availableSlots = queuePolicy.calculateAvailableSlots(activeCount);

        if (availableSlots <= 0) return;

        Pageable pageable = PageRequest.of(0, availableSlots);
        List<Queue> waitingQueues = queueRepository.findByStatusOrderByEnteredAtAsc(QueueStatus.WAITING, pageable);

        if(waitingQueues.isEmpty()) return;

        for(Queue queue : waitingQueues){
            queue.activate();
        }
        queueRepository.saveAll(waitingQueues);
    }

    @Transactional
    @Override
    public void exitQueue(String token) {
        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));
        queueRepository.delete(queue);
        activateNextInQueue();
    }

    @Transactional
    @Override
    public void expireQueue(String token) {
        Queue queue = queueRepository.findByTokenValue(token)
                .orElseThrow(() -> new QueueNotFoundException("대기열을 찾을 수 없습니다. 토큰 : " + token));

        if (queue.isActive()) {
            queue.expire();
            activateNextInQueue();
        }
    }
}
