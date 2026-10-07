package com.onrender.zipai.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.dto.lifestyle.RoomVisitRequest;
import com.onrender.zipai.dto.lifestyle.RoomVisitResponse;
import com.onrender.zipai.service.RoomConnectService;
import com.onrender.zipai.service.ZipaiAuthService;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

class RoomVisitControllerTest {
    private RoomConnectService roomConnectService;
    private ZipaiAuthService auth;
    private RoomVisitController controller;
    private MockHttpSession session;
    private RoomVisitRequest request;

    @BeforeEach
    void setUp() {
        roomConnectService = mock(RoomConnectService.class);
        auth = mock(ZipaiAuthService.class);
        controller = new RoomVisitController(roomConnectService, auth);
        session = new MockHttpSession();
        request = new RoomVisitRequest();
        request.setRoomId("OFFER-7");
        request.setTitle("클라이언트가 보낸 제목");
        request.setDate(LocalDate.of(2030, 1, 2));
        request.setTime(LocalTime.of(14, 0));
        request.setPhone("010-1234-5678");
    }

    @Test
    void anonymousVisitCreationIsRejected() {
        when(auth.required(session)).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        assertThatThrownBy(() -> controller.create(request, session))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(roomConnectService, never()).createVisit(any(), any());
    }

    @Test
    void guestVisitCreationIsRejected() {
        when(auth.required(session)).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "게스트 모드에서는 조회만 가능합니다."));

        assertThatThrownBy(() -> controller.create(request, session))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));
        verify(roomConnectService, never()).createVisit(any(), any());
    }

    @Test
    void authenticatedVisitCreationUsesSessionUserId() {
        ZipaiUser user = new ZipaiUser();
        user.setId(42L);
        when(auth.required(session)).thenReturn(user);
        when(roomConnectService.createVisit(42L, request)).thenReturn(
                new RoomVisitResponse(1L, "OFFER-7", "등록된 매물", request.getDate(),
                        "14:00", request.getPhone(), null, "pending"));

        controller.create(request, session);

        verify(roomConnectService).createVisit(42L, request);
    }
}
