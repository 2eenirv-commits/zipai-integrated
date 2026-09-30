package com.onrender.zipai.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import com.onrender.zipai.domain.LifestyleProperty;

public interface LifestylePropertyRepository extends CrudRepository<LifestyleProperty, Long> {
    List<LifestyleProperty> findBySidoAndSigunguAndActiveTrueAndStatusOrderByPropertyIdAsc(
            String sido, String sigungu, String status);
    Optional<LifestyleProperty> findByPropertyCodeAndActiveTrueAndStatus(String propertyCode, String status);
}
