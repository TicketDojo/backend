package com.ticket.dojo.backdeepfamily.domain.queue.scheduler;

import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final QueueService queueService;

    /***
     * 5초마다 대기자들 Active로 전환
     */
//    @Scheduled(fixedDelay = 5000)
//    public void activateNextInQueue() {
//        log.debug("스케줄러 실행 : 대기자 Active로 전환");
//        queueService.activateNextInQueue();
//    }
}
