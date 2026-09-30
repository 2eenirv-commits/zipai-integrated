package com.onrender.zipai.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.onrender.zipai.domain.LifestyleScore;

public interface LifestyleScoreRepository extends CrudRepository<LifestyleScore, Long> {
    Optional<LifestyleScore> findTopByAreaIdOrderBySourceDateDescScoreIdDesc(Long areaId);
}
