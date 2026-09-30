package com.onrender.zipai.dto.happyhousing.notice;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CrawlHistoryResponse {
    private Long crawlId;
    private String crawlerName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
    private Integer noticeCount;
    private Integer ruleCount;
    private Integer parsedRuleCount;
    private Integer validationRuleCount;
    private String errorMessage;
}
