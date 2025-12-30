package com.ticket.dojo.backdeepfamily.domain.ticketing.concurrency;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticket.dojo.backdeepfamily.domain.ticketing.service.TicketingSocketService;
import com.ticket.dojo.backdeepfamily.domain.ticketing.service.ReservationService;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.SeatRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Slf4j
public class TicketingConcurrencyTest {

    @Autowired
    private TicketingSocketService ticketingSocketService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private UserRepository userRepository;

    private Seat testSeat;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 좌석 생성
        testSeat = Seat.builder()
            .seatNumber("A-1")
            .build();
        testSeat = seatRepository.save(testSeat);

        log.info("▶▶▶ 기존 데이터 정리 및 테스트 좌석 생성 완료: seatId={}", testSeat.getId());
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 데이터 정리
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();
        log.info("▶▶▶ 테스트 데이터 정리 완료");
    }

    @Test
    @DisplayName("동일한 좌석을 10명이 동시에 점유 시도할 때, 1명만 성공해야 한다.")
    void testSeatHoldRace() throws InterruptedException {
        // 1. 테스트용 유저 10명과 예약 10개 생성
        List<Long> reservationIds = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User user = userRepository.save(User.builder()
                .email("user" + i + "@test.com")
                .password("1234")
                .name("User" + i)
                .build());

            Reservation reservation = Reservation.createReservation(user, 1L);
            reservation = reservationRepository.save(reservation);
            reservationIds.add(reservation.getId());
        }
        log.info("▶▶▶ 테스트용 유저 10명 및 예약 10개 생성 완료");

        // 2. 10개의 스레드가 동시에 동일한 좌석 점유 시도
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        Set<String> errors = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final Long reservationId = reservationIds.get(i);
            final int threadNum = i + 1;

            executorService.submit(() -> {
                try {
                    latch.await();
                    log.info("[스레드 {}] 좌석 점유 시도: seatId={}, reservationId={}",
                        threadNum, testSeat.getId(), reservationId);

                    ticketingSocketService.holdSeat(testSeat.getId(), reservationId);

                    successCount.incrementAndGet();
                    log.info("[스레드 {}] 좌석 점유 성공!", threadNum);

                } catch (InterruptedException e) {
                    log.error("[스레드 {} 인터럽트] {}", threadNum, e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add(e.getClass().getSimpleName());
                    log.info("[스레드 {}] 좌석 점유 실패 (예상된 동작): {}", threadNum, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        log.info("▶▶▶ 모든 스레드 동시 holdSeat() 호출 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 3. 검증: 해당 좌석의 ReservationSeat는 1개만 존재해야 함
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findAll().stream()
            .filter(rs -> rs.getSeat().getId().equals(testSeat.getId()))
            .toList();

        log.info("성공 횟수: {}", successCount.get());
        log.info("실패 횟수: {}", failCount.get());
        log.info("최종 ReservationSeat 개수: {}", reservationSeats.size());
        log.info("발생한 예외 타입들: {}", errors);

        assertThat(reservationSeats)
            .as("동일 좌석을 10명이 동시 점유 시도 시 ReservationSeat는 1개만 생성되어야 합니다")
            .hasSize(1);

        assertThat(successCount.get())
            .as("정확히 1명만 좌석 점유에 성공해야 합니다")
            .isEqualTo(1);

        assertThat(failCount.get())
            .as("나머지 9명은 실패해야 합니다")
            .isEqualTo(9);
    }

    @Test
    @DisplayName("서로 다른 좌석을 10명이 동시에 점유 시도할 때, 모두 성공해야 한다.")
    void testDifferentSeatsHoldRace() throws InterruptedException {
        // 1. 테스트용 좌석 10개 생성
        List<Seat> seats = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Seat seat = Seat.builder()
                .seatNumber("A-" + (i + 1))
                .build();
            seats.add(seatRepository.save(seat));
        }
        log.info("▶▶▶ 테스트용 좌석 10개 생성 완료");

        // 2. 테스트용 유저 10명과 예약 10개 생성
        List<Long> reservationIds = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User user = userRepository.save(User.builder()
                .email("user" + i + "@test.com")
                .password("1234")
                .name("User" + i)
                .build());

            Reservation reservation = Reservation.createReservation(user, 1L);
            reservation = reservationRepository.save(reservation);
            reservationIds.add(reservation.getId());
        }
        log.info("▶▶▶ 테스트용 유저 10명 및 예약 10개 생성 완료");

        // 3. 10개의 스레드가 각각 다른 좌석을 동시에 점유 시도
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        Set<String> errors = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final Long seatId = seats.get(i).getId();
            final Long reservationId = reservationIds.get(i);
            final int threadNum = i + 1;

            executorService.submit(() -> {
                try {
                    latch.await();
                    log.info("[스레드 {}] 좌석 점유 시도: seatId={}, reservationId={}",
                        threadNum, seatId, reservationId);

                    ticketingSocketService.holdSeat(seatId, reservationId);

                    successCount.incrementAndGet();
                    log.info("[스레드 {}] 좌석 점유 성공!", threadNum);

                } catch (InterruptedException e) {
                    log.error("[스레드 {} 인터럽트] {}", threadNum, e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    errors.add(e.getMessage());
                    log.error("[스레드 {}] 좌석 점유 실패: {}", threadNum, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        log.info("▶▶▶ 모든 스레드 동시 holdSeat() 호출 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 4. 검증: 10개의 ReservationSeat가 모두 생성되어야 함
        long reservationSeatCount = reservationSeatRepository.count();

        log.info("성공 횟수: {}", successCount.get());
        log.info("최종 ReservationSeat 개수: {}", reservationSeatCount);
        log.info("발생한 에러들: {}", errors);

        assertThat(reservationSeatCount)
            .as("서로 다른 좌석 10개를 동시 점유 시 모두 성공해야 합니다")
            .isEqualTo(10);

        assertThat(successCount.get())
            .as("모든 스레드가 좌석 점유에 성공해야 합니다")
            .isEqualTo(10);
    }

    @Test
    @DisplayName("좌석 점유와 해제가 동시에 발생할 때, 데이터 일관성이 유지되어야 한다.")
    void testHoldAndReleaseRace() throws InterruptedException {
        // 1. 테스트용 유저와 예약 생성
        User user = userRepository.save(User.builder()
            .email("test@test.com")
            .password("1234")
            .name("TestUser")
            .build());

        Reservation reservation = Reservation.createReservation(user, 1L);
        Reservation savedReservation = reservationRepository.save(reservation);
        final Long finalReservationId = savedReservation.getId();
        log.info("▶▶▶ 테스트용 유저 및 예약 생성 완료");

        // 2. 먼저 좌석을 점유
        ticketingSocketService.holdSeat(testSeat.getId(), finalReservationId);
        log.info("▶▶▶ 초기 좌석 점유 완료");

        // 3. 5개의 스레드가 동시에 해제와 점유를 반복
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i + 1;
            executorService.submit(() -> {
                try {
                    latch.await();

                    // 해제 시도
                    log.info("[스레드 {}] 좌석 해제 시도", threadNum);
                    ticketingSocketService.releaseSeat(finalReservationId, testSeat.getId());

                    // 약간의 딜레이
                    Thread.sleep(10);

                    // 다시 점유 시도
                    log.info("[스레드 {}] 좌석 재점유 시도", threadNum);
                    ticketingSocketService.holdSeat(testSeat.getId(), finalReservationId);

                } catch (InterruptedException e) {
                    log.error("[스레드 {} 인터럽트] {}", threadNum, e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.info("[스레드 {}] 에러: {}", threadNum, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        log.info("▶▶▶ 모든 스레드 동시 해제/점유 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 4. 검증: 최종적으로 좌석이 점유되어 있거나 비어있어야 함 (중복 점유 없음)
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findAll();

        log.info("최종 ReservationSeat 개수: {}", reservationSeats.size());

        assertThat(reservationSeats.size())
            .as("동시 해제/점유 후 ReservationSeat는 0개 또는 1개여야 합니다")
            .isLessThanOrEqualTo(1);
    }
}
