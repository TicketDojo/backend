package com.ticket.dojo.backdeepfamily.domain.auth.repository;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.BlackListToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface BlackListRepository extends JpaRepository<BlackListToken, Long> {

    // access 토큰으로 조회
    Optional<BlackListToken> findByAccessToken(String accessToken);

    // access 토큰 존재 여부 확인
    Boolean existsByAccessToken(String accessToken);

    // access 토큰 삭제
    @Transactional
    void deleteByAccessToken(String accessToken);

    // 만료된 블랙리스트 토큰 삭제 (스케줄러용)
    @Transactional
    void deleteByExpirationBefore(LocalDateTime dateTime);
}
