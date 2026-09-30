package com.onrender.zipai.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.onrender.zipai.domain.LifestyleArea;

public interface LifestyleAreaRepository extends CrudRepository<LifestyleArea, Long> {
    List<LifestyleArea> findByActiveTrueOrderBySidoAscSigunguAscDongAsc();
}
