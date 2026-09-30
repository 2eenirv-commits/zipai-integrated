package com.onrender.zipai.dto.happyhousing.notice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class HousingNoticeDetailResponse {
    private Long noticeId;
    private String panId;
    private String source;
    private String title;
    private String region;
    private LocalDate noticeDate;
    private LocalDate postingDate;
    private LocalDate closingDate;
    private String status;
    private String housingType;
    private String pdfFileId;
    private String pdfFileName;
    private String hwpxFileId;
    private String hwpxFileName;
    private String detailEndpoint;
    private LocalDateTime crawledAt;
    private List<HousingNoticeRuleResponse> rules;
}
