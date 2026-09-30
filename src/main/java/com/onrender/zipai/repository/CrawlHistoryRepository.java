package com.onrender.zipai.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.onrender.zipai.domain.CrawlHistory;

public interface CrawlHistoryRepository extends CrudRepository<CrawlHistory, Long> {

    Optional<CrawlHistory> findFirstByOrderByStartedAtDesc();
}
