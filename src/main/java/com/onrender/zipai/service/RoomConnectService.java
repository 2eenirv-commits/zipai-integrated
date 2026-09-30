package com.onrender.zipai.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.onrender.zipai.domain.LifestyleProperty;
import com.onrender.zipai.domain.PropertyImage;
import com.onrender.zipai.domain.RoomOffer;
import com.onrender.zipai.domain.RoomVisit;
import com.onrender.zipai.dto.lifestyle.RoomOfferRequest;
import com.onrender.zipai.dto.lifestyle.RoomOfferResponse;
import com.onrender.zipai.dto.lifestyle.RoomVisitRequest;
import com.onrender.zipai.dto.lifestyle.RoomVisitResponse;
import com.onrender.zipai.repository.LifestyleAreaRepository;
import com.onrender.zipai.repository.LifestylePropertyRepository;
import com.onrender.zipai.repository.PropertyImageRepository;
import com.onrender.zipai.repository.RoomOfferRepository;
import com.onrender.zipai.repository.RoomVisitRepository;

@Service
public class RoomConnectService {

    private static final String PENDING = "pending";
    private static final String APPROVED = "approved";
    private static final String REJECTED = "rejected";
    private static final String READY = "ready";


    private final RoomVisitRepository roomVisitRepository;
    private final RoomOfferRepository roomOfferRepository;
    private final LifestylePropertyRepository lifestylePropertyRepository;
    private final LifestyleAreaRepository lifestyleAreaRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final PropertyImageStorageService imageStorageService;

    public RoomConnectService(
            RoomVisitRepository roomVisitRepository,
            RoomOfferRepository roomOfferRepository,
            LifestylePropertyRepository lifestylePropertyRepository,
            LifestyleAreaRepository lifestyleAreaRepository,
            PropertyImageRepository propertyImageRepository,
            PropertyImageStorageService imageStorageService) {
        this.roomVisitRepository = roomVisitRepository;
        this.roomOfferRepository = roomOfferRepository;
        this.lifestylePropertyRepository = lifestylePropertyRepository;
        this.lifestyleAreaRepository = lifestyleAreaRepository;
        this.propertyImageRepository = propertyImageRepository;
        this.imageStorageService = imageStorageService;
    }

    @Transactional(readOnly = true)
    public List<RoomVisitResponse> getVisits() {
        return roomVisitRepository.findAllByOrderByVisitIdDesc()
                .stream()
                .map(RoomVisitResponse::from)
                .toList();
    }

    @Transactional
    public RoomVisitResponse createVisit(RoomVisitRequest request) {
        validateVisitRequest(request);

        LifestyleProperty property = lifestylePropertyRepository
                .findByPropertyCodeAndActiveTrueAndStatus(request.getRoomId().trim(), READY)
                .orElseThrow(() -> new IllegalArgumentException(
                        "현재 등록된 방문 가능 매물이 아닙니다."));

        RoomVisit visit = new RoomVisit();
        visit.setRoomId(property.getPropertyCode());
        visit.setTitle(property.getTitle());
        visit.setVisitDate(request.getDate());
        visit.setVisitTime(request.getTime());
        visit.setPhone(request.getPhone().trim());
        visit.setQuestion(normalizeOptional(request.getQuestion()));
        visit.setStatus(PENDING);

        return RoomVisitResponse.from(roomVisitRepository.save(visit));
    }

    @Transactional
    public RoomVisitResponse approveVisit(Long visitId) {
        RoomVisit visit = findVisit(visitId);
        requirePending(visit);

        boolean slotTaken = roomVisitRepository
                .findByRoomIdAndVisitDateAndVisitTimeAndStatus(
                        visit.getRoomId(),
                        visit.getVisitDate(),
                        visit.getVisitTime(),
                        APPROVED)
                .stream()
                .anyMatch(item -> !item.getVisitId().equals(visitId));

        if (slotTaken) {
            throw new IllegalArgumentException(
                    "이미 승인된 방문 일정입니다. 다른 시간을 선택해 주세요.");
        }

        RoomVisit updated = copyVisitWithStatus(visit, APPROVED);
        return RoomVisitResponse.from(roomVisitRepository.save(updated));
    }

    @Transactional
    public RoomVisitResponse rejectVisit(Long visitId) {
        RoomVisit visit = findVisit(visitId);
        requirePending(visit);

        RoomVisit updated = copyVisitWithStatus(visit, REJECTED);
        return RoomVisitResponse.from(roomVisitRepository.save(updated));
    }

    @Transactional(readOnly = true)
    public List<RoomOfferResponse> getOffers() {
        return roomOfferRepository.findAllByOrderByOfferIdDesc()
                .stream()
                .map(this::toOfferResponse)
                .toList();
    }

    @Transactional
    public RoomOfferResponse createOffer(RoomOfferRequest request) {
        return createOffer(request, new MultipartFile[0]);
    }

    @Transactional
    public RoomOfferResponse createOffer(
            RoomOfferRequest request,
            MultipartFile[] images) {
        validateOfferRequest(request);
        validateOfferImages(images);

        RoomOffer offer = new RoomOffer();
        offer.setTitle(request.getTitle().trim());
        offer.setDistrict(request.getDistrict().trim());
        offer.setDeposit(request.getDeposit());
        offer.setMonthly(request.getMonthly());
        offer.setMaintenance(request.getMaintenance());
        offer.setContractEnd(request.getContractEnd());
        offer.setMoveIn(request.getMoveIn());
        offer.setAvailableTime(request.getAvailableTime().trim());
        offer.setAgreement(request.getAgreement().trim());
        offer.setDescription(normalizeOptional(request.getDescription()));
        offer.setStatus(READY);

        offer = roomOfferRepository.save(offer);
        RegionParts region = resolveRegion(request.getDistrict());

        LifestyleProperty property = new LifestyleProperty();
        property.setPropertyCode("OFFER-" + offer.getOfferId());
        property.setSido(region.sido());
        property.setSigungu(region.sigungu());
        property.setDong(region.detail());
        property.setTitle(offer.getTitle());
        property.setDeposit(offer.getDeposit());
        property.setMonthlyRent(offer.getMonthly());
        property.setMaintenanceFee(offer.getMaintenance());
        property.setAvailableTime(offer.getAvailableTime());
        property.setStatus(READY);
        property.setActive(Boolean.TRUE);
        property.setSampleData(Boolean.FALSE);

        List<PropertyImageStorageService.StoredImage> storedImages = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (image != null && !image.isEmpty()) {
                    storedImages.add(imageStorageService.store(image));
                }
            }
        }

        if (!storedImages.isEmpty()) {
            property.setThumbnailUrl(storedImages.get(0).imageUrl());
            property.setPhotoCredit("등록자 업로드");
        }

        property = lifestylePropertyRepository.save(property);

        List<String> imageUrls = new ArrayList<>();
        for (int i = 0; i < storedImages.size(); i++) {
            PropertyImageStorageService.StoredImage stored = storedImages.get(i);

            PropertyImage image = new PropertyImage();
            image.setPropertyId(property.getPropertyId());
            image.setImageUrl(stored.imageUrl());
            image.setOriginalName(stored.originalName());
            image.setStoredName(stored.storedName());
            image.setSortOrder(i);
            image.setRepresentative(i == 0);
            propertyImageRepository.save(image);

            imageUrls.add(stored.imageUrl());
        }

        return RoomOfferResponse.from(offer, property.getPropertyCode(), imageUrls);
    }

    private RoomOfferResponse toOfferResponse(RoomOffer offer) {
        String propertyCode = "OFFER-" + offer.getOfferId();

        return lifestylePropertyRepository
                .findByPropertyCodeAndActiveTrueAndStatus(propertyCode, READY)
                .map(property -> RoomOfferResponse.from(
                        offer,
                        property.getPropertyCode(),
                        propertyImageRepository
                                .findByPropertyIdOrderBySortOrderAscImageIdAsc(
                                        property.getPropertyId())
                                .stream()
                                .map(PropertyImage::getImageUrl)
                                .toList()))
                .orElseGet(() -> RoomOfferResponse.from(offer));
    }

    private void validateOfferImages(MultipartFile[] images) {
        if (images == null || images.length == 0) return;

        int count = 0;
        for (MultipartFile image : images) {
            if (image != null && !image.isEmpty()) count++;
        }
        if (count > 5) {
            throw new IllegalArgumentException("방 사진은 최대 5장까지 업로드할 수 있습니다.");
        }
    }

    private RegionParts resolveRegion(String district) {
        String normalized = district == null ? "" : district.trim();

        return lifestyleAreaRepository
                .findByActiveTrueOrderBySidoAscSigunguAscDongAsc()
                .stream()
                .filter(area -> normalized.contains(area.getSigungu()))
                .findFirst()
                .map(area -> {
                    String detail = normalized
                            .replace(area.getSido(), "")
                            .replace(area.getSigungu(), "")
                            .trim();
                    return new RegionParts(
                            area.getSido(),
                            area.getSigungu(),
                            detail.isBlank() ? null : detail);
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        "Lifestyle 추천 지역과 연결할 수 있도록 지역에 시·군명을 정확히 입력해 주세요. 예: 성남시 분당구"));
    }

    private record RegionParts(String sido, String sigungu, String detail) {}

    private RoomVisit findVisit(Long visitId) {
        if (visitId == null) {
            throw new IllegalArgumentException("방문 요청 번호가 필요합니다.");
        }
        return roomVisitRepository.findById(visitId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "방문 요청을 찾을 수 없습니다."));
    }

    private void requirePending(RoomVisit visit) {
        if (!PENDING.equals(visit.getStatus())) {
            throw new IllegalArgumentException(
                    "이미 처리된 방문 요청입니다.");
        }
    }

    private RoomVisit copyVisitWithStatus(RoomVisit visit, String status) {
        visit.setStatus(status);
        return visit;
    }

    private void validateVisitRequest(RoomVisitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("방문 신청 내용이 없습니다.");
        }
        requireText(request.getRoomId(), "방을 선택해 주세요.");
        requireText(request.getTitle(), "방 제목이 필요합니다.");
        if (request.getDate() == null) {
            throw new IllegalArgumentException("방문 희망일을 선택해 주세요.");
        }
        if (request.getDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("지난 날짜에는 방문 신청을 할 수 없습니다.");
        }
        if (request.getTime() == null) {
            throw new IllegalArgumentException("방문 시간을 선택해 주세요.");
        }
        requireText(request.getPhone(), "연락처를 입력해 주세요.");
        if (!request.getPhone().trim().matches("[0-9+() -]{8,20}")) {
            throw new IllegalArgumentException("연락처 형식을 확인해 주세요.");
        }
    }

    private void validateOfferRequest(RoomOfferRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("방 등록 내용이 없습니다.");
        }
        requireText(request.getTitle(), "방 제목을 입력해 주세요.");
        requireText(request.getDistrict(), "지역을 입력해 주세요.");
        requireNonNegative(request.getDeposit(), "보증금");
        requireNonNegative(request.getMonthly(), "월세");
        requireNonNegative(request.getMaintenance(), "관리비");
        if (request.getContractEnd() == null) {
            throw new IllegalArgumentException("계약 종료일을 입력해 주세요.");
        }
        if (request.getMoveIn() == null) {
            throw new IllegalArgumentException("입주 가능일을 입력해 주세요.");
        }
        requireText(request.getAvailableTime(), "방문 가능 시간을 입력해 주세요.");
        requireText(request.getAgreement(), "집주인·중개사 협의 상태를 선택해 주세요.");
    }

    private void requireNonNegative(Long value, String label) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(label + "은(는) 0 이상의 값이어야 합니다.");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
