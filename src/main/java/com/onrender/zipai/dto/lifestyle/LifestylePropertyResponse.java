package com.onrender.zipai.dto.lifestyle;

import java.util.List;
import com.onrender.zipai.domain.LifestyleProperty;

public class LifestylePropertyResponse {
    private final String id;
    private final String title;
    private final String sido;
    private final String sigungu;
    private final String dong;
    private final Long deposit;
    private final Long monthlyRent;
    private final Long maintenanceFee;
    private final String availableTime;
    private final String thumbnailUrl;
    private final String photoCredit;
    private final Boolean sampleData;
    private final List<String> imageUrls;

    public LifestylePropertyResponse(
            String id, String title, String sido, String sigungu, String dong,
            Long deposit, Long monthlyRent, Long maintenanceFee, String availableTime,
            String thumbnailUrl, String photoCredit, Boolean sampleData,
            List<String> imageUrls) {
        this.id=id; this.title=title; this.sido=sido; this.sigungu=sigungu; this.dong=dong;
        this.deposit=deposit; this.monthlyRent=monthlyRent; this.maintenanceFee=maintenanceFee;
        this.availableTime=availableTime; this.thumbnailUrl=thumbnailUrl;
        this.photoCredit=photoCredit; this.sampleData=sampleData;
        this.imageUrls=imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }

    public static LifestylePropertyResponse from(LifestyleProperty p) {
        return from(p, List.of());
    }

    public static LifestylePropertyResponse from(LifestyleProperty p, List<String> imageUrls) {
        return new LifestylePropertyResponse(
            p.getPropertyCode(), p.getTitle(), p.getSido(), p.getSigungu(), p.getDong(),
            p.getDeposit(), p.getMonthlyRent(), p.getMaintenanceFee(), p.getAvailableTime(),
            p.getThumbnailUrl(), p.getPhotoCredit(), Boolean.TRUE.equals(p.getSampleData()),
            imageUrls);
    }

    public String getId(){return id;} public String getTitle(){return title;}
    public String getSido(){return sido;} public String getSigungu(){return sigungu;}
    public String getDong(){return dong;} public Long getDeposit(){return deposit;}
    public Long getMonthlyRent(){return monthlyRent;} public Long getMaintenanceFee(){return maintenanceFee;}
    public String getAvailableTime(){return availableTime;} public String getThumbnailUrl(){return thumbnailUrl;}
    public String getPhotoCredit(){return photoCredit;} public Boolean getSampleData(){return sampleData;}
    public List<String> getImageUrls(){return imageUrls;}
}
