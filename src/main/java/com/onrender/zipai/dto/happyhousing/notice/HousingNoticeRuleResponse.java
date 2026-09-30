package com.onrender.zipai.dto.happyhousing.notice;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class HousingNoticeRuleResponse {
    private Long ruleId;
    private Long noticeId;
    private String panId;
    private String applicantType;
    private String sourcePdf;
    private String validationStatus;
    private String ruleJson;
    private LocalDateTime updatedAt;
}
