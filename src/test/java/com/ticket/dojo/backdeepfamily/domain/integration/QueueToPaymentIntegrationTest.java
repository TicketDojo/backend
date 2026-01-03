package com.ticket.dojo.backdeepfamily.domain.integration;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetHoldingSeatsResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.service.ReservationService;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus.*;
import static com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation.ReservationState.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 큐 진입 → 티켓팅 → 결제 완료 → 큐 EXPIRED 통합 테스트
 * 
 * 테스트 시나리오:
 * 1. 사용자가 대기열에 진입 (ACTIVE or WAITING)
 * 2. 티켓팅 페이지 진입 (Reservation 생성)
 * 3. 결제 페이지 진입 (PAYING)
 * 4. 결제 완료 (CONFIRMED + 큐 EXPIRED)
 * 5. WAITING 상태의 대기자 -> ACTIVE 상태로 변경
 */
@SpringBootTest
@DisplayName("큐 진입부터 결제 완료까지 통합 테스트")
class QueueToPaymentIntegrationTest {

    @Autowired
    private QueueService queueService;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private QueueRepository queueRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("시나리오 1: 사용자가 큐 진입(ACTIVE) → 결제 완료 → 큐 EXPIRED")
    @Transactional
    void completePaymentFlow_ActiveUser() {
        // given
        User user = createAndSaveUser("testuser");

        // when
        // 대기열 진입 (50명 미만이므로 즉시 ACTIVE)
        QueueEnterResponse queueResponse = queueService.enterQueue(user.getUserId());
        String queueToken = queueResponse.getToken();

        // 티켓팅 페이지 진입 (Reservation 생성)
        GetHoldingSeatsResponse ticketingResponse = reservationService.enterTicketing(user.getUserId());
        Long reservationId = ticketingResponse.getReservationId();

        // 결제 페이지 진입 (PAYING)
        reservationService.enterPaying(reservationId);

        // 결제 완료 (CONFIRMED, 큐 EXPIRED)
        reservationService.completePaying(user.getUserId(), reservationId, queueToken);

        // then
        // 결제 완료 확인
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getReservationState()).isEqualTo(CONFIRMED);

        // EXPIRED 상태 확인
        QueueStatusResponse queueStatus = queueService.getQueueStatus(queueToken);
        assertThat(queueStatus.getStatus()).isEqualTo(EXPIRED);
    }

    @Test
    @DisplayName("시나리오 2: 대기자가 있을 때 결제 완료 시 대기자 활성화")
    @Transactional
    void completePaymentFlow_WithWaitingUser() {
        // given: 50명의 ACTIVE 사용자 생성
        for (int i = 0; i < 50; i++) {
            User activeUser = createAndSaveUser("active" + i);
            queueService.enterQueue(activeUser.getUserId());
        }

        // 첫 번째 ACTIVE 사용자를 결제 진행할 사용자로 선택
        List<Queue> activeQueues = queueRepository.findByStatusOrderByEnteredAtAsc(ACTIVE, null);
        assertThat(activeQueues).hasSize(50); // 초반 50명은 ACTIVE 상태
        Queue firstActiveQueue = activeQueues.get(0);
        User firstActiveUser = firstActiveQueue.getUser();
        String firstActiveToken = firstActiveQueue.getTokenValue();

        // 대기자 생성 (WAITING 상태)
        User waitingUser = createAndSaveUser("waitingUser");
        QueueEnterResponse waitingUserQueue = queueService.enterQueue(waitingUser.getUserId());

        // 대기 상태 확인
        assertThat(waitingUserQueue.getStatus()).isEqualTo(WAITING);
        assertThat(waitingUserQueue.getPosition()).isEqualTo(1); // 첫 번째 대기자

        // when
        // 첫 번째 ACTIVE 사용자가 티켓팅 진입
        GetHoldingSeatsResponse ticketingResponse = reservationService.enterTicketing(firstActiveUser.getUserId());
        Long reservationId = ticketingResponse.getReservationId();

        // 결제 페이지 진입
        reservationService.enterPaying(reservationId);

        // 결제 완료 (큐 만료 → 대기자 활성화)
        reservationService.completePaying(firstActiveUser.getUserId(), reservationId, firstActiveToken);

        // then
        // 결제 완료 확인
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getReservationState()).isEqualTo(CONFIRMED);

        // 첫 번째 사용자의 큐가 EXPIRED 상태
        QueueStatusResponse expiredStatus = queueService.getQueueStatus(firstActiveToken);
        assertThat(expiredStatus.getStatus()).isEqualTo(EXPIRED);

        // 대기자가 ACTIVE 상태로 변경되었는지 확인
        QueueStatusResponse waitingUserStatus = queueService.getQueueStatus(waitingUserQueue.getToken());
        assertThat(waitingUserStatus.getStatus()).isEqualTo(ACTIVE);
        assertThat(waitingUserStatus.getPosition()).isEqualTo(0);
    }

    private User createAndSaveUser(String suffix) {
        String email = "integration_" + suffix + "@test.com";
        String name = "integration_" + suffix;
        User user = User.builder()
                .email(email)
                .password("password123")
                .name(name)
                .build();
        return userRepository.save(user);
    }
}
