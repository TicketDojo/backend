package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetHoldingSeatsResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetRankingResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.exception.ReservationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation.ReservationState.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService 단위 테스트")
class ReservationServiceImplTest {

        @Mock
        private UserRepository userRepository;

        @Mock
        private ReservationRepository reservationRepository;

        @Mock
        private ReservationSeatRepository reservationSeatRepository;

        @Mock
        private QueueService queueService;

        @InjectMocks
        private ReservationServiceImpl reservationService;

        private User testUser;
        private Reservation testReservation;
        private Seat testSeat;
        private ReservationSeat testReservationSeat;
        private Long testUserId;
        private Long testReservationId;
        private Long testSequenceNum;
        private String testQueueToken;

        @BeforeEach
        void setUp() {
                testUserId = 1L;
                testReservationId = 1L;
                testSequenceNum = 1L;
                testQueueToken = "test-queue-token-123";

                testUser = User.builder()
                                .userId(testUserId)
                                .name("테스트유저")
                                .email("test@example.com")
                                .build();

                testReservation = Reservation.builder()
                                .id(testReservationId)
                                .user(testUser)
                                .sequenceNum(testSequenceNum)
                                .reservationState(PENDING)
                                .build();

                testSeat = Seat.builder()
                                .id(1L)
                                .seatNumber("A1")
                                .build();

                testReservationSeat = ReservationSeat.builder()
                                .id(1L)
                                .seat(testSeat)
                                .reservation(testReservation)
                                .expiredAt(LocalDateTime.now().plusSeconds(20))
                                .build();
        }

        @Test
        @DisplayName("결제 진입 성공 - PAYING 상태로 변경 및 좌석 만료시간 갱신")
        void enterPaying_Success() {
                // given
                given(reservationRepository.findById(testReservationId))
                                .willReturn(Optional.of(testReservation));
                given(reservationSeatRepository.findAllByReservation(testReservation))
                                .willReturn(List.of(testReservationSeat));

                // when
                reservationService.enterPaying(testReservationId);

                // then
                assertThat(testReservation.getReservationState()).isEqualTo(PAYING);
                verify(reservationRepository, times(1)).findById(testReservationId);
                verify(reservationSeatRepository, times(1)).findAllByReservation(testReservation);
        }

        @Test
        @DisplayName("결제 진입 실패 - 예약을 찾을 수 없음")
        void enterPaying_ReservationNotFound() {
                // given
                given(reservationRepository.findById(testReservationId))
                                .willReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> reservationService.enterPaying(testReservationId))
                                .isInstanceOf(ReservationException.class)
                                .hasMessageContaining("예약을 찾을 수 없습니다");

                verify(reservationRepository, times(1)).findById(testReservationId);
                verify(reservationSeatRepository, never()).findAllByReservation(any());
        }

        @Test
        @DisplayName("티켓팅 진입 성공 - Reservation 생성 및 점유 좌석 조회")
        void enterTicketing_Success() {
                // given
                given(userRepository.findById(testUserId))
                                .willReturn(Optional.of(testUser));

                given(reservationSeatRepository.findAll())
                                .willReturn(List.of(testReservationSeat));

                given(reservationRepository.save(any(Reservation.class)))
                                .willAnswer(invocation -> invocation.getArgument(0));

                // when
                GetHoldingSeatsResponse response = reservationService.enterTicketing(testUserId);

                // then
                assertThat(response).isNotNull();
                // reservationId는 JPA save 후에 설정되므로 null일 수 있음 (mock 환경)
                assertThat(response.getSequenceNum()).isNotNull();
                assertThat(response.getSeats()).isNotNull();
                assertThat(response.getSeats()).hasSize(1);
                assertThat(response.getSeats().get(0).getSeatId()).isEqualTo(testSeat.getId());
                assertThat(response.getSeats().get(0).getSeatNumber()).isEqualTo(testSeat.getSeatNumber());

                verify(userRepository, times(1)).findById(testUserId);
                verify(reservationRepository, times(1)).save(any(Reservation.class));
                verify(reservationSeatRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("결제 완료 성공 - CONFIRMED 상태로 변경 및 대기열 만료")
        void completePaying_Success() {
                // given
                given(reservationRepository.findById(testReservationId))
                                .willReturn(Optional.of(testReservation));

                // when
                reservationService.completePaying(testUserId, testReservationId, testQueueToken);

                // then
                assertThat(testReservation.getReservationState()).isEqualTo(CONFIRMED);
                verify(reservationRepository, times(1)).findById(testReservationId);
                verify(queueService, times(1)).expireQueue(testQueueToken);
        }

        @Test
        @DisplayName("랭킹 조회 성공")
        void getRanking_Success() {
                // given
                Reservation confirmedReservation1 = Reservation.builder()
                                .id(1L)
                                .user(testUser)
                                .sequenceNum(testSequenceNum)
                                .reservationState(CONFIRMED)
                                .build();

                User user2 = User.builder()
                                .userId(2L)
                                .name("유저2")
                                .email("user2@example.com")
                                .build();

                Reservation confirmedReservation2 = Reservation.builder()
                                .id(2L)
                                .user(user2)
                                .sequenceNum(testSequenceNum)
                                .reservationState(CONFIRMED)
                                .build();

                given(reservationRepository.findById(testReservationId))
                                .willReturn(Optional.of(testReservation));
                given(reservationRepository.findAllBySequenceNumAndReservationStateOrderByUpdatedAtAsc(
                                testSequenceNum, CONFIRMED))
                                .willReturn(List.of(confirmedReservation1, confirmedReservation2));

                // when
                GetRankingResponse response = reservationService.getRanking(testReservationId);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getRanks()).hasSize(2);
                assertThat(response.getRanks().get(0).getName()).isEqualTo("테스트유저");
                assertThat(response.getRanks().get(1).getName()).isEqualTo("유저2");

                verify(reservationRepository, times(1)).findById(testReservationId);
                verify(reservationRepository, times(1))
                                .findAllBySequenceNumAndReservationStateOrderByUpdatedAtAsc(testSequenceNum, CONFIRMED);
        }

        @Test
        @DisplayName("예약 취소 성공")
        void cancelReservation_Success() {
                // given
                given(reservationRepository.findById(testReservationId))
                                .willReturn(Optional.of(testReservation));
                given(reservationSeatRepository.findAllByReservation(testReservation))
                                .willReturn(List.of(testReservationSeat));

                // when
                reservationService.cancelReservation(testReservationId, testUserId);

                // then
                assertThat(testReservation.getReservationState()).isEqualTo(Reservation.ReservationState.CANCELLED);
                verify(reservationRepository, times(1)).findById(testReservationId);
                verify(reservationSeatRepository, times(1)).findAllByReservation(testReservation);
                verify(reservationSeatRepository, times(1)).deleteAll(anyList());
        }

        @Test
        @DisplayName("예약 취소 실패 - 권한 없음")
        void cancelReservation_Unauthorized() {
                // given
                Long otherUserId = 999L;
                given(reservationRepository.findById(testReservationId))
                                .willReturn(Optional.of(testReservation));

                // when & then
                assertThatThrownBy(() -> reservationService.cancelReservation(testReservationId, otherUserId))
                                .isInstanceOf(ReservationException.class)
                                .hasMessageContaining("예약 취소 권한이 없습니다");

                verify(reservationRepository, times(1)).findById(testReservationId);
                verify(reservationSeatRepository, never()).deleteAll(anyList());
        }

        @Test
        @DisplayName("예약 취소 실패 - 확정된 예약은 취소 불가")
        void cancelReservation_ConfirmedReservation() {
                // given
                testReservation.changeState(CONFIRMED);
                given(reservationRepository.findById(testReservationId))
                                .willReturn(Optional.of(testReservation));

                // when & then
                assertThatThrownBy(() -> reservationService.cancelReservation(testReservationId, testUserId))
                                .isInstanceOf(ReservationException.class)
                                .hasMessageContaining("확정된 예약은 취소할 수 없습니다");

                verify(reservationRepository, times(1)).findById(testReservationId);
                verify(reservationSeatRepository, never()).deleteAll(anyList());
        }
}
