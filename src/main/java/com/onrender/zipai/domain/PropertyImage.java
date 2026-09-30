package com.onrender.zipai.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("property_image")
public class PropertyImage {
    @Id
    @Column("image_id")
    private Long imageId;

    @Column("property_id")
    private Long propertyId;

    @Column("image_url")
    private String imageUrl;

    @Column("original_name")
    private String originalName;

    @Column("stored_name")
    private String storedName;

    @Column("sort_order")
    private Integer sortOrder;

    private Boolean representative;

    @Column("created_at")
    private LocalDateTime createdAt;

    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getRepresentative() { return representative; }
    public void setRepresentative(Boolean representative) { this.representative = representative; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
