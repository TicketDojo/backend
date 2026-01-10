package com.ticket.dojo.backdeepfamily.domain.ticketing.scheduler;

import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매분 정각에 ReservationSeat 테이블을 초기화하고 새 회차 시작
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoundResetScheduler {

    private final ReservationSeatRepository reservationSeatRepository;

    /**
     * 매분 0초에 실행 - 모든 좌석 점유 초기화
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void resetForNewRound() {
        long count = reservationSeatRepository.count();

        if (count > 0) {
            reservationSeatRepository.deleteAll();
            log.info("새 회차 시작");
        }
    }
}
