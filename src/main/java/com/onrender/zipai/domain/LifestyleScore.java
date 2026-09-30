package com.onrender.zipai.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("lifestyle_score")
public class LifestyleScore {

    @Id
    @Column("score_id")
    private Long scoreId;

    @Column("area_id")
    private Long areaId;

    @Column("transport_score")
    private Integer transportScore;

    @Column("convenience_score")
    private Integer convenienceScore;

    @Column("medical_score")
    private Integer medicalScore;

    @Column("education_score")
    private Integer educationScore;

    @Column("park_score")
    private Integer parkScore;

    @Column("safety_score")
    private Integer safetyScore;

    @Column("commercial_score")
    private Integer commercialScore;

    @Column("quiet_score")
    private Integer quietScore;

    @Column("cost_score")
    private Integer costScore;

    @Column("source_name")
    private String sourceName;

    @Column("source_date")
    private LocalDate sourceDate;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getScoreId() { return scoreId; }
    public void setScoreId(Long scoreId) { this.scoreId = scoreId; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public Integer getTransportScore() { return transportScore; }
    public void setTransportScore(Integer transportScore) { this.transportScore = transportScore; }
    public Integer getConvenienceScore() { return convenienceScore; }
    public void setConvenienceScore(Integer convenienceScore) { this.convenienceScore = convenienceScore; }
    public Integer getMedicalScore() { return medicalScore; }
    public void setMedicalScore(Integer medicalScore) { this.medicalScore = medicalScore; }
    public Integer getEducationScore() { return educationScore; }
    public void setEducationScore(Integer educationScore) { this.educationScore = educationScore; }
    public Integer getParkScore() { return parkScore; }
    public void setParkScore(Integer parkScore) { this.parkScore = parkScore; }
    public Integer getSafetyScore() { return safetyScore; }
    public void setSafetyScore(Integer safetyScore) { this.safetyScore = safetyScore; }
    public Integer getCommercialScore() { return commercialScore; }
    public void setCommercialScore(Integer commercialScore) { this.commercialScore = commercialScore; }
    public Integer getQuietScore() { return quietScore; }
    public void setQuietScore(Integer quietScore) { this.quietScore = quietScore; }
    public Integer getCostScore() { return costScore; }
    public void setCostScore(Integer costScore) { this.costScore = costScore; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public LocalDate getSourceDate() { return sourceDate; }
    public void setSourceDate(LocalDate sourceDate) { this.sourceDate = sourceDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
