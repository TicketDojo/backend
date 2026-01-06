package com.ticket.dojo.backdeepfamily.domain.ticketing.racecondition;

import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.SeatRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.service.TicketingSocketService;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("티켓팅 Race Condition 테스트")
class TicketingRaceConditionTest {

    @Autowired
    private TicketingSocketService ticketingSocketService;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();
    }

    // db unique 제약으로 성공
    @Test
    @DisplayName("Race Condition: 동일 좌석 동시 선택 시 중복 허용")
    void sameSeatConcurrentSelection() {
        // given
        // 좌석 1개 생성
        Seat seat = createAndSaveSeat("AA1");

        // 10명의 사용자와 예약 생성
        int concurrentUsers = 10;
        List<Reservation> reservations = new ArrayList<>();
        for (int i = 0; i < concurrentUsers; i++) {
            User user = createAndSaveUser("user" + i);
            Reservation reservation = createAndSaveReservation(user);
            reservations.add(reservation);
        }

        // when
        // 10명이 동시에 같은 좌석 선택 시도
        ExecutorService executorService = Executors.newCachedThreadPool();
        Phaser phaser = new Phaser(concurrentUsers + 1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (Reservation reservation : reservations) {
            executorService.submit(() -> {
                try {
                    phaser.arriveAndAwaitAdvance();
                    ticketingSocketService.holdSeat(seat.getId(), reservation.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("예외 발생: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }

        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndAwaitAdvance();

        executorService.shutdown();

        // then
        // 해당 좌석에 대한 모든 ReservationSeat 조회 (ID로 비교)
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findAll().stream()
                .filter(rs -> rs.getSeat().getId().equals(seat.getId()))
                .toList();
        int actualCount = reservationSeats.size();

        System.err.println("=== Results ===");
        System.err.println("동시 시도 인원: " + concurrentUsers);
        System.err.println("성공 인원: " + successCount.get());
        System.err.println("실패 인원: " + failureCount.get());
        System.err.println("실제 예약 좌석 수: " + actualCount);
        System.err.println("좌석 ID: " + seat.getId());

        // Race Condition 발생 시: 여러 명이 같은 좌석을 선택할 수 있음
        // 정상: 1명만 선택할 수 있어야 함
        assertThat(actualCount).isEqualTo(1);
    }

    private Seat createAndSaveSeat(String seatNumber) {
        Seat seat = Seat.builder()
                .seatNumber(seatNumber)
                .build();
        return seatRepository.save(seat);
    }

    private User createAndSaveUser(String suffix) {
        String email = "race_" + suffix + "@test.com";
        String name = "race_" + suffix;
        User user = User.builder()
                .email(email)
                .password("password123")
                .name(name)
                .build();
        return userRepository.save(user);
    }

    private Reservation createAndSaveReservation(User user) {
        Reservation reservation = Reservation.createReservation(user, System.currentTimeMillis());
        return reservationRepository.save(reservation);
    }
}
