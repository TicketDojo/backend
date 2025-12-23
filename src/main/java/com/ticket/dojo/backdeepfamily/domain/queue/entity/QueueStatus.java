package com.ticket.dojo.backdeepfamily.domain.queue.entity;

public enum QueueStatus {
    WAITING("대기 중"),
    ACTIVE("활성화"),
    EXPIRED("만료됨");

    private final String description;

    QueueStatus(String description){
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    // 상태 전환 가능 여부
    public boolean canTransitionTo(QueueStatus targetStatus){
        if(this == WAITING){
            return targetStatus == ACTIVE || targetStatus == EXPIRED;
        }

        if(this == ACTIVE){
            return targetStatus == EXPIRED;
        }

        if(this == EXPIRED){
            return false;
        }

        return false;
    }

    public boolean isWaiting() {
        return this == WAITING;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isExpired() {
        return this == EXPIRED;
    }

}
