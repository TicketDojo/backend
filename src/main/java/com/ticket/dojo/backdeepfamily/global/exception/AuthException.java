package com.ticket.dojo.backdeepfamily.global.exception;

/**
 * 인증/인가 관련 커스텀 예외
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Refresh 토큰 관련 예외
     */
    public static class InvalidRefreshTokenException extends AuthException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }

    /**
     * Access 토큰 관련 예외
     */
    public static class InvalidAccessTokenException extends AuthException {
        public InvalidAccessTokenException(String message) {
            super(message);
        }
    }

    /**
     * 토큰 만료 예외
     */
    public static class ExpiredTokenException extends AuthException {
        public ExpiredTokenException(String message) {
            super(message);
        }
    }
}
