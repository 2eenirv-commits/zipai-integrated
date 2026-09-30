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
@Table("housing_notice_rule")
public class HousingNoticeRule {

    @Id
    @Column("rule_id")
    private Long ruleId;

    @Column("notice_id")
    private Long noticeId;

    @Column("pan_id")
    private String panId;

    @Column("applicant_type")
    private String applicantType;

    @Column("source_pdf")
    private String sourcePdf;

    @Column("source_pdf_hash")
    private String sourcePdfHash;

    @Column("validation_status")
    private String validationStatus;

    @Column("rule_json")
    private String ruleJson;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
