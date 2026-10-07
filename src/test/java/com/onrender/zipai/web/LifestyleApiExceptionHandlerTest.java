package com.onrender.zipai.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LifestyleApiExceptionHandlerTest {
    private final LifestyleApiExceptionHandler handler = new LifestyleApiExceptionHandler();

    @Test
    void authorizationStatusIsNotConvertedToServerError() {
        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "접근할 수 없습니다."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "접근할 수 없습니다.");
    }
}
