package com.onrender.zipai.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.onrender.zipai.domain.EligibilityRule;

public interface EligibilityRuleRepository extends CrudRepository<EligibilityRule, Long> {

    @Query("""
        SELECT *
        FROM eligibility_rule
        WHERE applicant_type = :applicantType
          AND active = TRUE
          AND effective_from <= :noticeDate
          AND (effective_to IS NULL OR effective_to >= :noticeDate)
        ORDER BY effective_from DESC
        LIMIT 1
        """)
    Optional<EligibilityRule> findActiveRule(
            @Param("applicantType") String applicantType,
            @Param("noticeDate") LocalDate noticeDate);
}
