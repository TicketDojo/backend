package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Position;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class QueuePolicy {

    // 최대 동시 접속 가능 인원
    private static final int MAX_ACTIVE_USERS = 50;

    /**
     * 즉시 확성화 가능 여부 판단
     */
    public boolean canActivateImmediately(int currentActiveCount){
        return currentActiveCount < MAX_ACTIVE_USERS;
    }

    /**
     * 활성화 가능한 슬릇 수 계산
     */
    public int calculateAvailableSlots(int currentActiveCount){
        int slots = MAX_ACTIVE_USERS - currentActiveCount;
        return Math.max(0, slots);
    }

    /**
     * 대기 순번 계산
     */
    public Position calculateWaitingPosition(int currentWaitingCount){
        return Position.of(currentWaitingCount + 1);
    }

    /**
     * 최대 동시 접속 가능 인원 수 얻기
     */
    public int getMaxActiveUsers(){
        return MAX_ACTIVE_USERS;
    }
}
