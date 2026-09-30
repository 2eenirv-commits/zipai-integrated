package com.onrender.zipai.dto.happyhousing.notice;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class HousingNoticeMatchResponse {
    private Long noticeId;
    private String panId;
    private String title;
    private String region;
    private LocalDate noticeDate;
    private LocalDate closingDate;
    private String noticeStatus;
    private String applicantType;
    private String validationStatus;
    private String recommendationStatus;
    private String recommendationTitle;
    private int passedCount;
    private int checkCount;
    private int failedCount;
    private List<HousingNoticeMatchCheckResponse> checks;
}
