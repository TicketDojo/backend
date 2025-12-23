package com.ticket.dojo.backdeepfamily.domain.queue.entity;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position {

    private int value;

    private Position(int value){
        validatePosition(value);
        this.value = value;
    }

    public static Position of(int value){
        return new Position(value);
    }

    public static Position zero() {
        return new Position(0);
    }

    private void validatePosition(int value){
        if(value < 0){
            throw  new IllegalArgumentException("대기 순번은 0 이상이여야 합니다.");
        }
    }

    public Position increment() {
        return new Position(this.value + 1);
    }

    public boolean isZero() {
        return this.value == 0;
    }
}
