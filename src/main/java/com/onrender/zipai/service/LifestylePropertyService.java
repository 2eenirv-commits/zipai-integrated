package com.onrender.zipai.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onrender.zipai.dto.lifestyle.LifestylePropertyResponse;
import com.onrender.zipai.repository.LifestylePropertyRepository;
import com.onrender.zipai.repository.PropertyImageRepository;

@Service
public class LifestylePropertyService {
    private static final String READY = "ready";

    private final LifestylePropertyRepository repository;
    private final PropertyImageRepository propertyImageRepository;

    public LifestylePropertyService(
            LifestylePropertyRepository repository,
            PropertyImageRepository propertyImageRepository) {
        this.repository = repository;
        this.propertyImageRepository = propertyImageRepository;
    }

    @Transactional(readOnly = true)
    public List<LifestylePropertyResponse> getProperties(String sido, String sigungu) {
        String s = (sido == null || sido.isBlank()) ? "경기도" : sido.trim();
        if (sigungu == null || sigungu.isBlank()) {
            throw new IllegalArgumentException("매물을 조회할 시·군·구를 선택해 주세요.");
        }

        return repository
                .findBySidoAndSigunguAndActiveTrueAndStatusOrderByPropertyIdAsc(
                        s, sigungu.trim(), READY)
                .stream()
                .map(property -> LifestylePropertyResponse.from(
                        property,
                        propertyImageRepository
                                .findByPropertyIdOrderBySortOrderAscImageIdAsc(
                                        property.getPropertyId())
                                .stream()
                                .map(image -> image.getImageUrl())
                                .toList()))
                .toList();
    }
}
