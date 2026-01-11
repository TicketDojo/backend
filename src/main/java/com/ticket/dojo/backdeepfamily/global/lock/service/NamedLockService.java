package com.ticket.dojo.backdeepfamily.global.lock.service;

import com.ticket.dojo.backdeepfamily.global.lock.repository.NamedLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Named Lock 서비스
 *
 * MySQL의 GET_LOCK/RELEASE_LOCK을 사용하여 애플리케이션 레벨 동기화를 제공합니다.
 *
 * 주의사항:
 * - Propagation.REQUIRES_NEW를 사용하여 별도 트랜잭션에서 실행
 * - finally 블록에서 락 해제를 보장
 * - Named Lock은 세션 레벨 락이므로 트랜잭션과 무관하게 동작
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NamedLockService {

    private final NamedLockRepository namedLockRepository;
    private static final int DEFAULT_TIMEOUT_SECONDS = 3;

    /**
     * Named Lock을 획득하고 작업을 실행한 후 락을 해제합니다.
     *
     * @param lockKey 락 키 (예: "queue:assign:position")
     * @param supplier 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 결과
     * @throws IllegalStateException 락 획득 실패 시
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        return executeWithLock(lockKey, DEFAULT_TIMEOUT_SECONDS, supplier);
    }

    /**
     * Named Lock을 획득하고 작업을 실행한 후 락을 해제합니다.
     *
     * @param lockKey 락 키
     * @param timeoutSeconds 타임아웃 (초)
     * @param supplier 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 결과
     * @throws IllegalStateException 락 획득 실패 시
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T executeWithLock(String lockKey, int timeoutSeconds, Supplier<T> supplier) {
        try {
            log.debug("Named Lock 획득 시도: lockKey={}, timeout={}s", lockKey, timeoutSeconds);

            Integer lockResult = namedLockRepository.getLock(lockKey, timeoutSeconds);

            if (lockResult == null || lockResult != 1) {
                log.error("Named Lock 획득 실패: lockKey={}, result={}", lockKey, lockResult);
                throw new IllegalStateException("락 획득에 실패했습니다: " + lockKey);
            }

            log.debug("Named Lock 획득 성공: lockKey={}", lockKey);

            // 비즈니스 로직 실행
            return supplier.get();

        } finally {
            // 락 해제 보장
            Integer releaseResult = namedLockRepository.releaseLock(lockKey);
            log.debug("Named Lock 해제: lockKey={}, result={}", lockKey, releaseResult);
        }
    }

    /**
     * Named Lock을 획득하고 작업을 실행한 후 락을 해제합니다. (void 작업용)
     *
     * @param lockKey 락 키
     * @param runnable 실행할 작업
     * @throws IllegalStateException 락 획득 실패 시
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeWithLock(String lockKey, Runnable runnable) {
        executeWithLock(lockKey, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 특정 Named Lock이 사용 가능한지 확인합니다.
     *
     * @param lockKey 락 키
     * @return true: 사용 가능, false: 사용 중
     */
    public boolean isLockFree(String lockKey) {
        Integer result = namedLockRepository.isFreeLock(lockKey);
        return result != null && result == 1;
    }
}
