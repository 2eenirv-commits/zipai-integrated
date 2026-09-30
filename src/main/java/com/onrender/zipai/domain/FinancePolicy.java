package com.onrender.zipai.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("finance_policies")
public class FinancePolicy {
    @Id
    private Long id;
    private String category;
    private String name;
    @Column("target_type")
    private String targetType;
    @Column("limit_info")
    private String limitInfo;
    @Column("rate_info")
    private String rateInfo;
    private String description;
    @Column("source_name")
    private String sourceName;
    @Column("source_url")
    private String sourceUrl;
    @Column("source_checked_at")
    private LocalDateTime sourceCheckedAt;
    @Column("source_hash")
    private String sourceHash;
    @Column("update_status")
    private String updateStatus;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getLimitInfo() { return limitInfo; }
    public void setLimitInfo(String limitInfo) { this.limitInfo = limitInfo; }
    public String getRateInfo() { return rateInfo; }
    public void setRateInfo(String rateInfo) { this.rateInfo = rateInfo; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public LocalDateTime getSourceCheckedAt() { return sourceCheckedAt; }
    public void setSourceCheckedAt(LocalDateTime sourceCheckedAt) { this.sourceCheckedAt = sourceCheckedAt; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
    public String getUpdateStatus() { return updateStatus; }
    public void setUpdateStatus(String updateStatus) { this.updateStatus = updateStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
