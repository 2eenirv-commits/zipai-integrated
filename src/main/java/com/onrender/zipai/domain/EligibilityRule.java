package com.onrender.zipai.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("eligibility_rule")
public class EligibilityRule {

    @Id
    private Long ruleId;

    private String applicantType;
    private Integer ruleYear;

    private Integer minAge;
    private Integer maxAge;

    private Long incomeLimit;
    private Long assetLimit;
    private Long carLimit;

    private Boolean homelessRequired;
    private Boolean categoryRequired;
    private Boolean connectionRequired;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private Boolean active;

    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
