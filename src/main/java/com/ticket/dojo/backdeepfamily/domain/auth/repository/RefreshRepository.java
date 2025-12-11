package com.ticket.dojo.backdeepfamily.domain.auth.repository;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {

    // refresh 토큰으로 조회
    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    // 이메일로 조회
    Optional<RefreshToken> findByEmail(String email);

    // refresh 토큰 존재 여부 확인
    Boolean existsByRefreshToken(String refreshToken);

    // refresh 토큰 삭제
    void deleteByRefreshToken(String refreshToken);

    // 이메일로 refresh 토큰 삭제 (로그아웃)
    void deleteByEmail(String email);
}
