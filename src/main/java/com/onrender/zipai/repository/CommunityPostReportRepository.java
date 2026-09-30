package com.onrender.zipai.repository;

import com.onrender.zipai.domain.CommunityPostReport;
import org.springframework.data.repository.CrudRepository;

public interface CommunityPostReportRepository extends CrudRepository<CommunityPostReport, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
}
