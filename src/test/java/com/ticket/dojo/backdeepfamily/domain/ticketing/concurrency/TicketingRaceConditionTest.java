package com.ticket.dojo.backdeepfamily.domain.ticketing.concurrency;

import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.SeatRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.service.TicketingSocketService;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class TicketingRaceConditionTest {

    @Autowired
    private TicketingSocketService ticketingSocketService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Race Condition: 같은 좌석을 동시에 점유 시도 (Double Booking)")
    void 동시_좌석_점유_시_중복_발생() throws InterruptedException {
        // given: 좌석 1개와 10명의 유저 및 예약 생성
        Seat seat = createAndSaveSeat("A1");
        
        List<Reservation> reservations = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User user = createAndSaveUser("user" + i);
            Reservation reservation = Reservation.createReservation(user, 1L);
            reservations.add(reservationRepository.save(reservation));
        }

        // when: 10명이 동시에 같은 좌석 점유 시도
        int concurrentUsers = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentUsers);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentUsers; i++) {
            final Reservation reservation = reservations.get(i);
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드 동시 시작
                    ticketingSocketService.holdSeat(seat.getId(), reservation.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("점유 실패: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 시작
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 1명만 점유 성공
        int actualHoldCount = reservationSeatRepository.findAll().size();

        System.out.println("====Race Condition 테스트 결과 (좌석 동시 점유)====");
        System.out.println("성공 횟수: " + successCount.get() + "\t기대: 1");
        System.out.println("실패 횟수: " + failCount.get() + "\t기대: 9");
        System.out.println("실제 점유된 좌석 수: " + actualHoldCount + "\t기대: 1");

        // Race Condition 발생 시 2명 이상이 같은 좌석 점유
        assertEquals(1, actualHoldCount, "좌석은 1개만 점유되어야 합니다. (동시성 제어 필요)");
        assertEquals(1, successCount.get(), "성공은 1명만 가능해야 합니다.");
    }

    // Helper methods
    private User createAndSaveUser(String name) {
        User user = User.builder()
                .email(name + "@test.com")
                .password("password123")
                .name(name)
                .build();
        return userRepository.save(user);
    }

    private Seat createAndSaveSeat(String seatNumber) {
        Seat seat = Seat.builder()
                .seatNumber(seatNumber)
                .build();
        return seatRepository.save(seat);
    }
}
