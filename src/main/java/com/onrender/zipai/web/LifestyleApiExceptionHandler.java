package com.onrender.zipai.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {
        RoomVisitController.class,
        RoomOfferController.class,
        LifestyleRecommendationController.class
})
public class LifestyleApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LifestyleApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(
            ResponseStatusException exception) {
        String message = exception.getReason() == null
                ? "요청을 처리할 권한이 없습니다."
                : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception) {
        // 진단 단계에서는 Spring 로깅 설정과 무관하게 실제 예외를 터미널에 반드시 출력한다.
        exception.printStackTrace(System.err);
        log.error("Lifestyle API unexpected error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "서버에서 요청을 처리하지 못했습니다."));
    }
}
