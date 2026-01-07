package com.ticket.dojo.backdeepfamily.integration.base;

import com.ticket.dojo.backdeepfamily.domain.auth.repository.BlackListRepository;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.RefreshRepository;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.SeatRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User.Role;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 모든 통합 테스트의 공통 부모 클래스
 *
 * 목적:
 * - Spring Boot 전체 컨텍스트 로딩 (@SpringBootTest)
 * - 공통 테스트 유틸리티 제공
 * - 데이터베이스 정리 유틸리티
 * - 테스트 데이터 빌더
 *
 * 사용법:
 * - Controller 테스트는 BaseControllerIntegrationTest 상속
 * - Service 테스트는 BaseServiceIntegrationTest 상속
 * - 직접 상속도 가능 (특수한 경우)
 *
 * 주의사항:
 * - cleanup 메서드는 FK 제약 조건을 고려한 순서로 실행
 * - 각 테스트는 @BeforeEach/@AfterEach에서 필요한 cleanup 호출
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    // === 도메인별 Repository ===

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected QueueRepository queueRepository;

    @Autowired
    protected ReservationRepository reservationRepository;

    @Autowired
    protected ReservationSeatRepository reservationSeatRepository;

    @Autowired
    protected SeatRepository seatRepository;

    @Autowired
    protected RefreshRepository refreshRepository;

    @Autowired
    protected BlackListRepository blackListRepository;

    // === 유틸리티 ===

    @Autowired
    protected BCryptPasswordEncoder passwordEncoder;

    /**
     * 모든 도메인 데이터 정리
     *
     * 실행 순서:
     * 1. 자식 엔티티부터 삭제 (FK 제약 조건)
     * 2. 부모 엔티티 삭제
     *
     * 주의사항:
     * - FK 제약 조건 때문에 순서가 중요함
     * - 순서 변경 시 에러 발생 가능
     */
    protected void cleanupAllDomains() {
        // 1. ReservationSeat (자식: Reservation과 Seat를 참조)
        reservationSeatRepository.deleteAll();

        // 2. Reservation (Seat 참조, User 참조)
        reservationRepository.deleteAll();

        // 3. Queue (User 참조)
        queueRepository.deleteAll();

        // 4. Seat (독립 엔티티)
        seatRepository.deleteAll();

        // 5. User (많은 엔티티가 참조하지만 위에서 이미 정리됨)
        userRepository.deleteAll();

        // 6. Refresh (Redis - User 참조)
        refreshRepository.deleteAll();

        // 7. BlackList (Redis - 독립)
        blackListRepository.deleteAll();
    }

    /**
     * 테스트용 사용자 생성 (암호화된 비밀번호)
     *
     * @param email    이메일
     * @param password 평문 비밀번호 (BCrypt로 암호화됨)
     * @return 생성된 User (DB에 저장되지 않음, save 필요)
     */
    protected User createTestUser(String email, String password) {
        return User.builder()
                .email(email)
                .name(email)  // name을 email과 동일하게 설정
                .password(passwordEncoder.encode(password))
                .role(Role.USER)
                .build();
    }

    /**
     * 테스트용 사용자 생성 (suffix 기반)
     *
     * 사용 예:
     * - createTestUser("u1") -> "ts_u1@test.com" / "pw"
     *
     * @param suffix 사용자 식별자 (예: "u1", "user1")
     * @return 생성된 User (DB에 저장되지 않음, save 필요)
     */
    protected User createTestUser(String suffix) {
        String email = "ts_" + suffix + "@test.com";
        String password = "pw";
        return User.builder()
                .email(email)
                .name("test_" + suffix)
                .password(passwordEncoder.encode(password))
                .role(Role.USER)
                .build();
    }

    /**
     * 테스트용 사용자 생성 및 저장
     *
     * @param email    이메일
     * @param password 평문 비밀번호
     * @return DB에 저장된 User
     */
    protected User createAndSaveTestUser(String email, String password) {
        User user = createTestUser(email, password);
        return userRepository.save(user);
    }

    /**
     * 테스트용 사용자 생성 및 저장 (suffix 기반)
     *
     * @param suffix 사용자 식별자
     * @return DB에 저장된 User
     */
    protected User createAndSaveTestUser(String suffix) {
        User user = createTestUser(suffix);
        return userRepository.save(user);
    }
}
