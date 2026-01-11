package com.ticket.dojo.backdeepfamily.global.lock.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * MySQL Named Lock을 사용하기 위한 Repository
 *
 * Named Lock은 데이터베이스 세션 레벨의 락으로,
 * 여러 테이블에 걸친 논리적 단위의 동시성 제어가 필요할 때 사용합니다.
 */
@Repository
@RequiredArgsConstructor
public class NamedLockRepository {

    private final EntityManager entityManager;

    /**
     * Named Lock 획득
     *
     * @param lockKey 락 키 (예: "queue:assign:position")
     * @param timeoutSeconds 타임아웃 시간 (초)
     * @return 1: 성공, 0: 타임아웃, null: 에러
     */
    public Integer getLock(String lockKey, int timeoutSeconds) {
        Object result = entityManager.createNativeQuery("SELECT GET_LOCK(:lockKey, :timeoutSeconds)")
                .setParameter("lockKey", lockKey)
                .setParameter("timeoutSeconds", timeoutSeconds)
                .getSingleResult();

        if (result == null) {
            return null;
        }
        return ((Number) result).intValue();
    }

    /**
     * Named Lock 해제
     *
     * @param lockKey 락 키
     * @return 1: 성공, 0: 락이 현재 스레드에 의해 설정되지 않음, null: 락이 존재하지 않음
     */
    public Integer releaseLock(String lockKey) {
        Object result = entityManager.createNativeQuery("SELECT RELEASE_LOCK(:lockKey)")
                .setParameter("lockKey", lockKey)
                .getSingleResult();

        if (result == null) {
            return null;
        }
        return ((Number) result).intValue();
    }

    /**
     * Named Lock이 사용 가능한지 확인
     *
     * @param lockKey 락 키
     * @return 1: 사용 가능 (락이 없음), 0: 사용 중, null: 에러
     */
    public Integer isFreeLock(String lockKey) {
        Object result = entityManager.createNativeQuery("SELECT IS_FREE_LOCK(:lockKey)")
                .setParameter("lockKey", lockKey)
                .getSingleResult();

        if (result == null) {
            return null;
        }
        return ((Number) result).intValue();
    }
}
