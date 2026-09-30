package com.onrender.zipai.domain;

import java.time.LocalDate;
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
@Table("housing_notice")
public class HousingNotice {

    @Id
    @Column("notice_id")
    private Long noticeId;

    @Column("pan_id")
    private String panId;

    private String source;
    private String title;
    private String region;

    @Column("notice_date")
    private LocalDate noticeDate;

    @Column("posting_date")
    private LocalDate postingDate;

    @Column("closing_date")
    private LocalDate closingDate;

    private String status;

    @Column("housing_type")
    private String housingType;

    @Column("ccr_cnnt_sys_ds_cd")
    private String ccrCnntSysDsCd;

    @Column("upp_ais_tp_cd")
    private String uppAisTpCd;

    @Column("ais_tp_cd")
    private String aisTpCd;

    @Column("pdf_file_id")
    private String pdfFileId;

    @Column("pdf_file_name")
    private String pdfFileName;

    @Column("hwpx_file_id")
    private String hwpxFileId;

    @Column("hwpx_file_name")
    private String hwpxFileName;

    @Column("pdf_text_path")
    private String pdfTextPath;

    @Column("detail_endpoint")
    private String detailEndpoint;

    @Column("crawled_at")
    private LocalDateTime crawledAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
