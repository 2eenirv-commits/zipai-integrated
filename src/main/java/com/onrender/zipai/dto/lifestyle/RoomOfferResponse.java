package com.onrender.zipai.dto.lifestyle;

import java.time.LocalDate;
import java.util.List;
import com.onrender.zipai.domain.RoomOffer;

public class RoomOfferResponse {
    private final Long id;
    private final String title;
    private final String district;
    private final Long deposit;
    private final Long monthly;
    private final Long maintenance;
    private final LocalDate contractEnd;
    private final LocalDate moveIn;
    private final String availableTime;
    private final String agreement;
    private final String description;
    private final String status;
    private final String propertyCode;
    private final List<String> imageUrls;

    public RoomOfferResponse(
            Long id, String title, String district, Long deposit, Long monthly,
            Long maintenance, LocalDate contractEnd, LocalDate moveIn,
            String availableTime, String agreement, String description,
            String status, String propertyCode, List<String> imageUrls) {
        this.id=id; this.title=title; this.district=district; this.deposit=deposit;
        this.monthly=monthly; this.maintenance=maintenance; this.contractEnd=contractEnd;
        this.moveIn=moveIn; this.availableTime=availableTime; this.agreement=agreement;
        this.description=description; this.status=status; this.propertyCode=propertyCode;
        this.imageUrls=imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }

    public static RoomOfferResponse from(RoomOffer offer) {
        return from(offer, null, List.of());
    }

    public static RoomOfferResponse from(
            RoomOffer offer, String propertyCode, List<String> imageUrls) {
        return new RoomOfferResponse(
                offer.getOfferId(), offer.getTitle(), offer.getDistrict(),
                offer.getDeposit(), offer.getMonthly(), offer.getMaintenance(),
                offer.getContractEnd(), offer.getMoveIn(), offer.getAvailableTime(),
                offer.getAgreement(), offer.getDescription(), offer.getStatus(),
                propertyCode, imageUrls);
    }

    public Long getId(){return id;} public String getTitle(){return title;}
    public String getDistrict(){return district;} public Long getDeposit(){return deposit;}
    public Long getMonthly(){return monthly;} public Long getMaintenance(){return maintenance;}
    public LocalDate getContractEnd(){return contractEnd;} public LocalDate getMoveIn(){return moveIn;}
    public String getAvailableTime(){return availableTime;} public String getAgreement(){return agreement;}
    public String getDescription(){return description;} public String getStatus(){return status;}
    public String getPropertyCode(){return propertyCode;} public List<String> getImageUrls(){return imageUrls;}
}
