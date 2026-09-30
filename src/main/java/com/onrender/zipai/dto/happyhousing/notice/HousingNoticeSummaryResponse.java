package com.onrender.zipai.dto.happyhousing.notice;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class HousingNoticeSummaryResponse {
    private Long noticeId;
    private String panId;
    private String title;
    private String region;
    private LocalDate noticeDate;
    private LocalDate closingDate;
    private String status;
    private String housingType;
    private LocalDateTime crawledAt;
}
