package com.onrender.zipai.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("crawl_history")
public class CrawlHistory {

    @Id
    @Column("crawl_id")
    private Long crawlId;

    @Column("crawler_name")
    private String crawlerName;

    @Column("started_at")
    private LocalDateTime startedAt;

    @Column("finished_at")
    private LocalDateTime finishedAt;

    private String status;

    @Column("notice_count")
    private Integer noticeCount;

    @Column("rule_count")
    private Integer ruleCount;

    @Column("parsed_rule_count")
    private Integer parsedRuleCount;

    @Column("validation_rule_count")
    private Integer validationRuleCount;

    @Column("error_message")
    private String errorMessage;

    @Column("raw_json_path")
    private String rawJsonPath;

    @Column("processed_json_path")
    private String processedJsonPath;

    @Column("created_at")
    private LocalDateTime createdAt;
}
