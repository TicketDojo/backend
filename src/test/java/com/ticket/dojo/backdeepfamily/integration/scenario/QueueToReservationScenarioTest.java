package com.ticket.dojo.backdeepfamily.integration.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetHoldingSeatsResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.integration.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대기열 → 티켓팅 시나리오 E2E 테스트
 *
 * 테스트 목적:
 * - 대기열 시스템과 티켓팅 시스템의 통합 동작 검증
 * - 대기열 활성화 → 티켓팅 진입 플로우 검증
 * - 다수의 사용자 대기 및 순차 활성화 검증
 *
 * 테스트 시나리오:
 * 1. 대기열 WAITING → ACTIVE → 티켓팅 진입
 * 2. 대기열 만료 후 다음 대기자 자동 활성화
 * 3. 50명 이상 동시 진입 시 순차적 활성화
 */
@SpringBootTest
@DisplayName("대기열 → 티켓팅 시나리오 E2E 테스트")
class QueueToReservationScenarioTest extends BaseControllerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QueueService queueService;

    private List<Seat> testSeats;

    @BeforeEach
    void setUp() {
        cleanupAllDomains();
        testSeats = createTestSeats(100);
    }

    @AfterEach
    void tearDown() {
        cleanupAllDomains();
    }

    /**
     * 시나리오 1: 대기열 활성화 → 티켓팅 진입 전체 플로우
     *
     * 플로우:
     * 1. 사용자가 대기열 진입 (ACTIVE)
     * 2. 대기열 상태 조회 (ACTIVE 확인)
     * 3. 티켓팅 진입
     * 4. Reservation 생성 확인
     */
    @Test
    @DisplayName("대기열 활성화 → 티켓팅 진입 전체 플로우")
    void queueActivation_ToTicketing_Scenario() throws Exception {
        // 1. 회원가입 및 로그인
        String email = "queue-ticket@example.com";
        String password = "password";

        UserLoginRequest joinRequest = new UserLoginRequest();
        joinRequest.setEmail(email);
        joinRequest.setPassword(password);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequest)))
                .andExpect(status().isOk());

        String accessToken = performLoginAndGetAccessToken(email, password);

        // 2. 대기열 진입
        MvcResult queueResult = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        QueueEnterResponse queueResponse = objectMapper.readValue(
                queueResult.getResponse().getContentAsString(), QueueEnterResponse.class);
        String queueToken = queueResponse.getToken();

        assertThat(queueResponse.getStatus()).isEqualTo(QueueStatus.ACTIVE);

        // 3. 대기열 상태 조회
        MvcResult statusResult = mockMvc.perform(
                        authenticatedGet("/queue/status?token=" + queueToken, accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        QueueStatusResponse statusResponse = objectMapper.readValue(
                statusResult.getResponse().getContentAsString(), QueueStatusResponse.class);

        assertThat(statusResponse.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(statusResponse.getPosition()).isEqualTo(0);

        // 4. 티켓팅 진입
        MvcResult ticketingResult = mockMvc.perform(
                        authenticatedPost("/reservations", accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        GetHoldingSeatsResponse ticketingResponse = objectMapper.readValue(
                ticketingResult.getResponse().getContentAsString(), GetHoldingSeatsResponse.class);

        assertThat(ticketingResponse.getReservationId()).isNotNull();

        // 5. Reservation 생성 확인
        Reservation reservation = reservationRepository.findById(ticketingResponse.getReservationId()).orElseThrow();
        assertThat(reservation).isNotNull();
        assertThat(reservation.getReservationState()).isEqualTo(Reservation.ReservationState.PENDING);
    }

    /**
     * 시나리오 2: 대기열 만료 후 다음 대기자 자동 활성화
     *
     * 플로우:
     * 1. 50명의 사용자가 대기열 진입 (모두 ACTIVE)
     * 2. 1명의 추가 사용자 진입 (WAITING)
     * 3. ACTIVE 사용자 중 1명이 결제 완료 (대기열 만료)
     * 4. WAITING 사용자가 자동으로 ACTIVE로 전환
     * 5. ACTIVE된 사용자가 티켓팅 진입
     */
    @Test
    @DisplayName("대기열 만료 후 다음 대기자가 자동 활성화되어 티켓팅 진입")
    void queueExpire_ActivatesNextUser_ThenEnterTicketing() throws Exception {
        // 1. 50명의 ACTIVE 사용자 생성
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveTestUser("active" + i + "@example.com", "password");
            queueService.enterQueue(user.getUserId());
        }

        // 2. WAITING 사용자 생성
        User waitingUser = createAndSaveTestUser("waiting@example.com", "password");
        String waitingAccessToken = performLoginAndGetAccessToken(waitingUser.getEmail(), "password");

        MvcResult queueResult = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", waitingAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        QueueEnterResponse waitingQueueResponse = objectMapper.readValue(
                queueResult.getResponse().getContentAsString(), QueueEnterResponse.class);
        String waitingToken = waitingQueueResponse.getToken();

        // WAITING 상태 확인
        assertThat(waitingQueueResponse.getStatus()).isEqualTo(QueueStatus.WAITING);

        // 3. ACTIVE 사용자 중 1명의 대기열 만료 (exitQueue)
        var activeQueues = queueRepository.findByStatusOrderByEnteredAtAsc(QueueStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, 1));
        String activeToken = activeQueues.get(0).getTokenValue();

        mockMvc.perform(
                        authenticatedDelete("/queue/exit?token=" + activeToken, waitingAccessToken))
                .andExpect(status().isNoContent());

        // 4. WAITING 사용자가 ACTIVE로 전환 확인
        MvcResult statusResult = mockMvc.perform(
                        authenticatedGet("/queue/status?token=" + waitingToken, waitingAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        QueueStatusResponse statusResponse = objectMapper.readValue(
                statusResult.getResponse().getContentAsString(), QueueStatusResponse.class);

        assertThat(statusResponse.getStatus()).isEqualTo(QueueStatus.ACTIVE);

        // 5. 이제 티켓팅 진입 가능
        MvcResult ticketingResult = mockMvc.perform(
                        authenticatedPost("/reservations", waitingAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        GetHoldingSeatsResponse ticketingResponse = objectMapper.readValue(
                ticketingResult.getResponse().getContentAsString(), GetHoldingSeatsResponse.class);

        assertThat(ticketingResponse.getReservationId()).isNotNull();
    }

    /**
     * 시나리오 3: 50명 이상 동시 진입 시 순차적 활성화
     *
     * 플로우:
     * 1. 60명의 사용자가 순차적으로 대기열 진입
     * 2. 처음 50명은 ACTIVE, 나머지 10명은 WAITING
     * 3. ACTIVE 사용자들이 순차적으로 퇴장
     * 4. WAITING 사용자들이 순차적으로 ACTIVE로 전환
     * 5. 모든 사용자가 결국 티켓팅에 진입 가능
     */
    @Test
    @DisplayName("50명 이상 동시 진입 시 순차적 활성화 검증")
    void fiftyPlusUsers_SequentialActivation_Scenario() throws Exception {
        // 1. 60명의 사용자 생성 및 대기열 진입
        for (int i = 0; i < 60; i++) {
            User user = createAndSaveTestUser("user" + i + "@example.com", "password");
            QueueEnterResponse response = queueService.enterQueue(user.getUserId());

            if (i < 50) {
                // 처음 50명은 ACTIVE
                assertThat(response.getStatus()).isEqualTo(QueueStatus.ACTIVE);
            } else {
                // 나머지 10명은 WAITING
                assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);
            }
        }

        // 2. 상태 확인
        int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);
        int waitingCount = queueRepository.countByStatus(QueueStatus.WAITING);

        assertThat(activeCount).isEqualTo(50);
        assertThat(waitingCount).isEqualTo(10);

        // 3. ACTIVE 사용자 5명 퇴장
        var activeQueues = queueRepository.findByStatusOrderByEnteredAtAsc(QueueStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, 5));

        for (var queue : activeQueues) {
            queueRepository.delete(queue);
        }

        // 4. activateNextInQueue 호출 (스케줄러 시뮬레이션)
        queueService.activateNextInQueue();

        // 5. 검증: ACTIVE 50명 유지, WAITING 5명으로 감소
        int activeCountAfter = queueRepository.countByStatus(QueueStatus.ACTIVE);
        int waitingCountAfter = queueRepository.countByStatus(QueueStatus.WAITING);

        assertThat(activeCountAfter).isEqualTo(50);
        assertThat(waitingCountAfter).isEqualTo(5);
    }

    /**
     * 테스트용 좌석 생성 헬퍼 메서드
     */
    private List<Seat> createTestSeats(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    Seat seat = Seat.builder()
                            .seatNumber("A" + (i + 1))
                            .build();
                    return seatRepository.save(seat);
                })
                .toList();
    }
}
