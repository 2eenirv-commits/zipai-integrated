package com.onrender.zipai.dto.happyhousing;

import java.time.LocalDate;

import com.onrender.zipai.domain.EligibilityRule;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HappyHousingRuleResponse {

    private Long ruleId;
    private String applicantType;
    private Integer ruleYear;

    private Integer minAge;
    private Integer maxAge;

    private Long incomeLimit;
    private Long assetLimit;
    private Long carLimit;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    public static HappyHousingRuleResponse from(EligibilityRule rule) {
        return new HappyHousingRuleResponse(
                rule.getRuleId(),
                rule.getApplicantType(),
                rule.getRuleYear(),
                rule.getMinAge(),
                rule.getMaxAge(),
                rule.getIncomeLimit(),
                rule.getAssetLimit(),
                rule.getCarLimit(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo());
    }
}
