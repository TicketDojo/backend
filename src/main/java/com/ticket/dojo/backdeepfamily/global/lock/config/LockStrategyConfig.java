package com.ticket.dojo.backdeepfamily.global.lock.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 락 전략 설정 클래스
 * application.properties에서 각 도메인별 락 전략을 읽어옵니다.
 */
@Slf4j
@Getter
@Configuration
public class LockStrategyConfig {

    @Value("${lock.strategy.queue:NAMED}")
    private LockStrategy queueLockStrategy;

    @Value("${lock.strategy.seat:PESSIMISTIC}")
    private LockStrategy seatLockStrategy;

    @Value("${lock.strategy.reservation:OPTIMISTIC}")
    private LockStrategy reservationLockStrategy;

    @PostConstruct
    public void logConfiguration() {
        log.info("=== Lock Strategy Configuration ===");
        log.info("Queue Lock Strategy: {}", queueLockStrategy);
        log.info("Seat Lock Strategy: {}", seatLockStrategy);
        log.info("Reservation Lock Strategy: {}", reservationLockStrategy);
        log.info("===================================");
    }

    /**
     * 현재 설정된 락 전략 요약 반환
     */
    public String getSummary() {
        return String.format("Queue=%s, Seat=%s, Reservation=%s",
                queueLockStrategy, seatLockStrategy, reservationLockStrategy);
    }
}
