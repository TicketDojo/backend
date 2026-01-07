package com.ticket.dojo.backdeepfamily.integration.base;

/**
 * Service 통합 테스트를 위한 Base 클래스
 *
 * 목적:
 * - Service layer 통합 테스트 지원
 * - 실제 Spring Context와 DB를 사용한 테스트
 * - Transaction 관리
 *
 * 특징:
 * - @SpringBootTest를 통해 전체 Context 로딩
 * - Service, Repository 실제 Bean 사용
 * - 실제 MySQL + Redis 사용
 *
 * Transaction 전략:
 * - 테스트 메서드에 @Transactional 추가 시 자동 롤백
 * - @Transactional 없으면 실제 commit 발생
 * - @BeforeEach/@AfterEach에서 명시적 cleanup 권장
 *
 * 사용 예:
 * ```java
 * @DisplayName("User Service 통합 테스트")
 * class UserServiceIntegrationTest extends BaseServiceIntegrationTest {
 *
 *     @Autowired
 *     private UserService userService;
 *
 *     @BeforeEach
 *     void setUp() {
 *         userRepository.deleteAll();
 *     }
 *
 *     @Test
 *     @Transactional  // 선택사항: 자동 롤백 원하면 추가
 *     @DisplayName("회원가입 테스트")
 *     void join_Success() {
 *         // given
 *         UserJoinRequest request = ...;
 *
 *         // when
 *         Long userId = userService.join(request);
 *
 *         // then
 *         User user = userRepository.findById(userId).get();
 *         assertThat(user.getEmail()).isEqualTo(request.getEmail());
 *     }
 * }
 * ```
 */
public abstract class BaseServiceIntegrationTest extends BaseIntegrationTest {

    /**
     * Service 통합 테스트는 BaseIntegrationTest의 모든 기능을 상속받습니다.
     *
     * 제공되는 기능:
     * - userRepository, queueRepository 등 모든 Repository
     * - passwordEncoder
     * - cleanupAllDomains() - 전체 데이터 정리
     * - createTestUser() - 테스트 사용자 생성
     * - createAndSaveTestUser() - 테스트 사용자 생성 및 저장
     *
     * 추가로 필요한 기능이 있다면 여기에 구현하세요.
     */
}
