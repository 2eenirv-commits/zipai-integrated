package com.onrender.zipai.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("lifestyle_property")
public class LifestyleProperty {
    @Id @Column("property_id") private Long propertyId;
    @Column("property_code") private String propertyCode;
    private String sido;
    private String sigungu;
    private String dong;
    private String title;
    private Long deposit;
    @Column("monthly_rent") private Long monthlyRent;
    @Column("maintenance_fee") private Long maintenanceFee;
    @Column("available_time") private String availableTime;
    @Column("thumbnail_url") private String thumbnailUrl;
    @Column("photo_credit") private String photoCredit;
    private String status;
    private Boolean active;
    @Column("sample_data") private Boolean sampleData;
    @Column("created_at") private LocalDateTime createdAt;
    @Column("updated_at") private LocalDateTime updatedAt;

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long v) { propertyId = v; }
    public String getPropertyCode() { return propertyCode; }
    public void setPropertyCode(String v) { propertyCode = v; }
    public String getSido() { return sido; }
    public void setSido(String v) { sido = v; }
    public String getSigungu() { return sigungu; }
    public void setSigungu(String v) { sigungu = v; }
    public String getDong() { return dong; }
    public void setDong(String v) { dong = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public Long getDeposit() { return deposit; }
    public void setDeposit(Long v) { deposit = v; }
    public Long getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(Long v) { monthlyRent = v; }
    public Long getMaintenanceFee() { return maintenanceFee; }
    public void setMaintenanceFee(Long v) { maintenanceFee = v; }
    public String getAvailableTime() { return availableTime; }
    public void setAvailableTime(String v) { availableTime = v; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String v) { thumbnailUrl = v; }
    public String getPhotoCredit() { return photoCredit; }
    public void setPhotoCredit(String v) { photoCredit = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { active = v; }
    public Boolean getSampleData() { return sampleData; }
    public void setSampleData(Boolean v) { sampleData = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
