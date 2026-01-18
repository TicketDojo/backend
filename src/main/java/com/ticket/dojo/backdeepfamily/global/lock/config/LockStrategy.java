package com.ticket.dojo.backdeepfamily.global.lock.config;

/**
 * 락 전략 열거형
 * 각 도메인에서 사용할 수 있는 락 타입을 정의합니다.
 */
public enum LockStrategy {
    /**
     * 락 없음 (베이스라인 성능 측정용)
     */
    NONE,

    /**
     * MySQL Named Lock
     * 애플리케이션 레벨의 분산 락
     * 장점: 트랜잭션과 독립적, 명시적 락 범위
     * 단점: 추가 DB 커넥션 필요, 성능 오버헤드
     */
    NAMED,

    /**
     * 비관적 락 (Pessimistic Lock)
     * SELECT ... FOR UPDATE
     * 장점: 강력한 일관성, 데드락 감지
     * 단점: 동시성 감소, 락 대기 시간
     */
    PESSIMISTIC,

    /**
     * 낙관적 락 (Optimistic Lock)
     * @Version 기반 충돌 감지
     * 장점: 높은 동시성, 낮은 오버헤드
     * 단점: 충돌 시 재시도 필요
     */
    OPTIMISTIC
}
