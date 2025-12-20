package com.ticket.dojo.backdeepfamily.domain.queue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Token {

    @Column(name = "token", unique = true, nullable = false)
    private String value;

    private Token(String value){
        validateToken(value);
        this.value = value;
    }

    public static Token generate(){
        return new Token(UUID.randomUUID().toString());
    }

    public static Token of(String value){
        return new Token(value);
    }

    private void validateToken(String value){
        if(value == null || value.isBlank()){
            throw new IllegalStateException("토큰이 비어있을 수 없습니다.");
        }
    }
}
