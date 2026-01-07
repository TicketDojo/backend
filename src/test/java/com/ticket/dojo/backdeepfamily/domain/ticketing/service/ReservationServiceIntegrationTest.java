package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetHoldingSeatsResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetRankingResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.global.exception.ReservationException;
import com.ticket.dojo.backdeepfamily.integration.base.BaseServiceIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation.ReservationState.*;
import static org.assertj.core.api.Assertions.*;

/**
 * ReservationService 통합 테스트
 *
 * 테스트 목적:
 * - ReservationService + Repository 통합 검증
 * - 예약 상태 전환 로직 검증
 * - 좌석 점유 및 해제 로직 검증
 * - 권한 검증
 *
 * 주요 테스트 케이스:
 * 1. enterTicketing - Reservation 생성 및 점유 좌석 조회
 * 2. enterPaying - PENDING에서 PAYING 상태로 변경
 * 3. completePaying - PAYING에서 CONFIRMED 상태로 변경 및 대기열 만료
 * 4. cancelReservation - 좌석 점유 해제 및 CANCELLED 상태로 변경
 * 5. cancelReservation 권한 검증
 * 6. getRanking - 동일 회차의 확정된 예약자 순위 조회
 */
@DisplayName("ReservationService 통합 테스트")
class ReservationServiceIntegrationTest extends BaseServiceIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private QueueService queueService;

    private User testUser;
    private List<Seat> testSeats;

    @BeforeEach
    void setUp() {
        // 데이터 정리
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        queueRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 사용자 생성
        testUser = createAndSaveTestUser("reservation-test@example.com", "password");

        // 테스트용 좌석 생성 (A1-A10)
        testSeats = createTestSeats(10);
    }

    @AfterEach
    void tearDown() {
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        queueRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * 테스트 1: 티켓팅 진입 - Reservation 생성 및 점유 좌석 조회
     */
    @Test
    @DisplayName("티켓팅 진입 - Reservation 생성 및 점유 좌석 조회")
    @Transactional
    void enterTicketing_CreatesReservation_AndReturnsHoldingSeats() {
        // when
        GetHoldingSeatsResponse response = reservationService.enterTicketing(testUser.getUserId());

        // then
        assertThat(response).isNotNull();
        assertThat(response.getReservationId()).isNotNull();
        assertThat(response.getSequenceNum()).isGreaterThanOrEqualTo(0);
        assertThat(response.getSeats()).isNotNull();

        // DB 검증
        Reservation reservation = reservationRepository.findById(response.getReservationId()).orElseThrow();
        assertThat(reservation.getReservationState()).isEqualTo(PENDING);
        assertThat(reservation.getUser().getUserId()).isEqualTo(testUser.getUserId());
    }

    /**
     * 테스트 2: 결제 진입 - PENDING에서 PAYING 상태로 변경
     */
    @Test
    @DisplayName("결제 진입 - PENDING에서 PAYING 상태로 변경")
    @Transactional
    void enterPaying_ChangesPendingToPaying() {
        // given
        GetHoldingSeatsResponse ticketingResponse = reservationService.enterTicketing(testUser.getUserId());
        Long reservationId = ticketingResponse.getReservationId();

        // when
        reservationService.enterPaying(reservationId);

        // then
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getReservationState()).isEqualTo(PAYING);
    }

    /**
     * 테스트 3: 결제 완료 - PAYING에서 CONFIRMED 상태로 변경 및 대기열 만료
     */
    @Test
    @DisplayName("결제 완료 - PAYING에서 CONFIRMED 상태로 변경 및 대기열 만료")
    @Transactional
    void completePaying_ChangesPayingToConfirmed_AndExpiresQueue() {
        // given
        // 대기열 진입
        var queueResponse = queueService.enterQueue(testUser.getUserId());
        String queueToken = queueResponse.getToken();

        // 티켓팅 진입
        GetHoldingSeatsResponse ticketingResponse = reservationService.enterTicketing(testUser.getUserId());
        Long reservationId = ticketingResponse.getReservationId();

        // 결제 진입
        reservationService.enterPaying(reservationId);

        // when
        reservationService.completePaying(testUser.getUserId(), reservationId, queueToken);

        // then
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getReservationState()).isEqualTo(CONFIRMED);

        // 대기열이 만료되었는지 확인
        Queue queue = queueRepository.findByTokenValue(queueToken).orElseThrow();
        assertThat(queue.isExpired()).isTrue();
    }

    /**
     * 테스트 4: 예약 취소 - 좌석 점유 해제 및 CANCELLED 상태로 변경
     */
    @Test
    @DisplayName("예약 취소 - 좌석 점유 해제 및 CANCELLED 상태로 변경")
    @Transactional
    void cancelReservation_ReleasesSeats_AndChangesToCancelled() {
        // given
        GetHoldingSeatsResponse ticketingResponse = reservationService.enterTicketing(testUser.getUserId());
        Long reservationId = ticketingResponse.getReservationId();

        // 좌석 점유 추가
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        ReservationSeat reservationSeat = ReservationSeat.builder()
                .reservation(reservation)
                .seat(testSeats.get(0))
                .expiredAt(LocalDateTime.now().plusSeconds(20))
                .build();
        reservationSeatRepository.save(reservationSeat);

        // when
        reservationService.cancelReservation(reservationId, testUser.getUserId());

        // then
        Reservation cancelledReservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(cancelledReservation.getReservationState()).isEqualTo(CANCELLED);

        // 좌석 점유 해제 확인
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findAllByReservation(reservation);
        assertThat(reservationSeats).isEmpty();
    }

    /**
     * 테스트 5: 예약 취소 - 다른 사용자가 취소 시도 시 권한 에러
     */
    @Test
    @DisplayName("예약 취소 - 다른 사용자가 취소 시도 시 권한 에러")
    @Transactional
    void cancelReservation_ByDifferentUser_ThrowsUnauthorizedException() {
        // given
        GetHoldingSeatsResponse ticketingResponse = reservationService.enterTicketing(testUser.getUserId());
        Long reservationId = ticketingResponse.getReservationId();

        // 다른 사용자 생성
        User otherUser = createAndSaveTestUser("other@example.com", "password");

        // when & then
        assertThatThrownBy(() -> reservationService.cancelReservation(reservationId, otherUser.getUserId()))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("권한");
    }

    /**
     * 테스트 6: 랭킹 조회 - 동일 회차의 확정된 예약자 순위 조회
     */
    @Test
    @DisplayName("랭킹 조회 - 동일 회차의 확정된 예약자 순위 조회")
    @Transactional
    void getRanking_ReturnsConfirmedReservations_InOrder() {
        // given
        // 여러 사용자의 예약 생성 및 확정
        User user1 = createAndSaveTestUser("rank1@example.com", "password");
        User user2 = createAndSaveTestUser("rank2@example.com", "password");
        User user3 = createAndSaveTestUser("rank3@example.com", "password");

        // 각 사용자의 티켓팅 진입 및 결제 완료
        Long reservationId1 = reservationService.enterTicketing(user1.getUserId()).getReservationId();
        Reservation res1 = reservationRepository.findById(reservationId1).orElseThrow();
        res1.changeState(CONFIRMED);
        reservationRepository.save(res1);

        Long reservationId2 = reservationService.enterTicketing(user2.getUserId()).getReservationId();
        Reservation res2 = reservationRepository.findById(reservationId2).orElseThrow();
        res2.changeState(CONFIRMED);
        reservationRepository.save(res2);

        Long reservationId3 = reservationService.enterTicketing(user3.getUserId()).getReservationId();
        Reservation res3 = reservationRepository.findById(reservationId3).orElseThrow();
        res3.changeState(CONFIRMED);
        reservationRepository.save(res3);

        // when
        GetRankingResponse response = reservationService.getRanking(reservationId1);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getRanks()).hasSize(3);
        assertThat(response.getRanks().get(0).getName()).isEqualTo(user1.getName());
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
