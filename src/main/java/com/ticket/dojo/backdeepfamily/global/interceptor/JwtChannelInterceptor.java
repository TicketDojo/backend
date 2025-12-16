package com.ticket.dojo.backdeepfamily.global.interceptor;

import com.ticket.dojo.backdeepfamily.domain.user.entity.CustomUserDetails;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.util.jwt.JWTUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * WebSocket JWT 인증 인터셉터
 * 동작 과정:
 * 1. STOMP CONNECT 메시지 감지
 * 2. Authorization 헤더에서 JWT 토큰 추출
 * 3. 토큰 유효성 검증
 * 4. 사용자 정보 조회 및 인증 객체 생성
 * 5. WebSocket 세션에 인증 정보 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JWTUtil jwtUtil;
    private final UserRepository userRepository;

    /**
     * @param message STOMP 메시지
     * @param channel 메시지 채널
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // CONNECT 메시지일 때만 JWT 검증
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {

            // Authorization 헤더에서 JWT 토큰 추출
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("인증 토큰이 필요합니다.");
            }

            // "Bearer " 접두사 제거
            String token = authHeader.substring(7);

            try {
                // 토큰 카테고리 확인
                String category = jwtUtil.getCategory(token);
                if (!"access".equals(category)) {
                    throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
                }

                // 토큰 만료 여부 확인
                if (jwtUtil.isExpired(token)) {
                    throw new IllegalArgumentException("만료된 토큰입니다.");
                }

                String username = jwtUtil.getUsername(token);

                User user = userRepository.findByEmail(username);
                if (user == null) {
                    throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                }

                CustomUserDetails userDetails = new CustomUserDetails(user);
                Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                // WebSocket 세션에 인증 정보 저장
                accessor.setUser(authentication);

                log.info("WebSocket authentication successful for user: {}", username);

            } catch (Exception e) {
                log.error("WebSocket authentication failed: {}", e.getMessage());
                throw new IllegalArgumentException("인증에 실패했습니다: " + e.getMessage());
            }
        }

        return message;
    }
}
