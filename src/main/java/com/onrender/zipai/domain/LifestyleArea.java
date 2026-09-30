package com.onrender.zipai.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("lifestyle_area")
public class LifestyleArea {

    @Id
    @Column("area_id")
    private Long areaId;

    @Column("area_code")
    private String areaCode;

    private String sido;
    private String sigungu;
    private String dong;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean active;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getSido() { return sido; }
    public void setSido(String sido) { this.sido = sido; }
    public String getSigungu() { return sigungu; }
    public void setSigungu(String sigungu) { this.sigungu = sigungu; }
    public String getDong() { return dong; }
    public void setDong(String dong) { this.dong = dong; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
