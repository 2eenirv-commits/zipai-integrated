package com.onrender.zipai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onrender.zipai.domain.LifestyleProperty;
import com.onrender.zipai.domain.RoomOffer;
import com.onrender.zipai.domain.RoomVisit;
import com.onrender.zipai.dto.lifestyle.RoomVisitRequest;
import com.onrender.zipai.repository.LifestyleAreaRepository;
import com.onrender.zipai.repository.LifestylePropertyRepository;
import com.onrender.zipai.repository.PropertyImageRepository;
import com.onrender.zipai.repository.RoomOfferRepository;
import com.onrender.zipai.repository.RoomVisitRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RoomConnectServiceAuthorizationTest {
    private RoomVisitRepository roomVisitRepository;
    private RoomOfferRepository roomOfferRepository;
    private LifestylePropertyRepository lifestylePropertyRepository;
    private RoomConnectService service;

    @BeforeEach
    void setUp() {
        roomVisitRepository = mock(RoomVisitRepository.class);
        roomOfferRepository = mock(RoomOfferRepository.class);
        lifestylePropertyRepository = mock(LifestylePropertyRepository.class);
        service = new RoomConnectService(
                roomVisitRepository,
                roomOfferRepository,
                lifestylePropertyRepository,
                mock(LifestyleAreaRepository.class),
                mock(PropertyImageRepository.class),
                mock(PropertyImageStorageService.class));
    }

    @Test
    void authenticatedUserCreatesVisitAsSelf() {
        LifestyleProperty property = new LifestyleProperty();
        property.setPropertyCode("OFFER-7");
        property.setTitle("서버에 저장된 매물 제목");
        when(lifestylePropertyRepository.findByPropertyCodeAndActiveTrueAndStatus("OFFER-7", "ready"))
                .thenReturn(Optional.of(property));
        when(roomVisitRepository.save(any(RoomVisit.class))).thenAnswer(invocation -> {
            RoomVisit saved = invocation.getArgument(0);
            saved.setVisitId(99L);
            return saved;
        });

        RoomVisitRequest request = validVisitRequest();
        var response = service.createVisit(42L, request);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getTitle()).isEqualTo("서버에 저장된 매물 제목");
        verify(roomVisitRepository).save(org.mockito.ArgumentMatchers.argThat(
                visit -> Long.valueOf(42L).equals(visit.getRequesterUserId())));
    }

    @Test
    void anotherUsersVisitCannotBeRead() {
        RoomVisit visit = visit(99L, 2L, "OFFER-7");
        when(roomVisitRepository.findById(99L)).thenReturn(Optional.of(visit));

        assertForbidden(() -> service.getVisit(1L, 99L));
    }

    @Test
    void nonOwnerCannotApproveVisitForAnotherUsersProperty() {
        RoomVisit visit = visit(99L, 3L, "OFFER-7");
        RoomOffer offer = offer(7L, 2L);
        when(roomVisitRepository.findById(99L)).thenReturn(Optional.of(visit));
        when(roomOfferRepository.findById(7L)).thenReturn(Optional.of(offer));

        assertForbidden(() -> service.approveVisit(1L, 99L));
    }

    @Test
    void propertyOwnerCanApproveVisit() {
        RoomVisit visit = visit(99L, 3L, "OFFER-7");
        RoomOffer offer = offer(7L, 2L);
        when(roomVisitRepository.findById(99L)).thenReturn(Optional.of(visit));
        when(roomOfferRepository.findById(7L)).thenReturn(Optional.of(offer));
        when(roomVisitRepository.findByRoomIdAndVisitDateAndVisitTimeAndStatus(
                "OFFER-7", visit.getVisitDate(), visit.getVisitTime(), "approved"))
                .thenReturn(List.of());
        when(roomVisitRepository.save(visit)).thenReturn(visit);

        var response = service.approveVisit(2L, 99L);

        assertThat(response.getStatus()).isEqualTo("approved");
        verify(roomVisitRepository).save(visit);
    }

    private static RoomVisitRequest validVisitRequest() {
        RoomVisitRequest request = new RoomVisitRequest();
        request.setRoomId("OFFER-7");
        request.setTitle("클라이언트 제목");
        request.setDate(LocalDate.now().plusDays(2));
        request.setTime(LocalTime.of(14, 0));
        request.setPhone("010-1234-5678");
        return request;
    }

    private static RoomVisit visit(Long visitId, Long requesterUserId, String roomId) {
        RoomVisit visit = new RoomVisit();
        visit.setVisitId(visitId);
        visit.setRequesterUserId(requesterUserId);
        visit.setRoomId(roomId);
        visit.setTitle("테스트 매물");
        visit.setVisitDate(LocalDate.now().plusDays(2));
        visit.setVisitTime(LocalTime.of(14, 0));
        visit.setPhone("010-1234-5678");
        visit.setStatus("pending");
        return visit;
    }

    private static RoomOffer offer(Long offerId, Long ownerUserId) {
        RoomOffer offer = new RoomOffer();
        offer.setOfferId(offerId);
        offer.setOwnerUserId(ownerUserId);
        return offer;
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
