package com.onrender.zipai.domain;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("room_offer")
public class RoomOffer {

    @Id
    @Column("offer_id")
    private Long offerId;

    private String title;
    private String district;
    private Long deposit;
    private Long monthly;
    private Long maintenance;

    @Column("contract_end")
    private LocalDate contractEnd;

    @Column("move_in")
    private LocalDate moveIn;

    @Column("available_time")
    private String availableTime;

    private String agreement;
    private String description;
    private String status;

    public RoomOffer() {
    }

    public Long getOfferId() { return offerId; }
    public void setOfferId(Long offerId) { this.offerId = offerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public Long getDeposit() { return deposit; }
    public void setDeposit(Long deposit) { this.deposit = deposit; }
    public Long getMonthly() { return monthly; }
    public void setMonthly(Long monthly) { this.monthly = monthly; }
    public Long getMaintenance() { return maintenance; }
    public void setMaintenance(Long maintenance) { this.maintenance = maintenance; }
    public LocalDate getContractEnd() { return contractEnd; }
    public void setContractEnd(LocalDate contractEnd) { this.contractEnd = contractEnd; }
    public LocalDate getMoveIn() { return moveIn; }
    public void setMoveIn(LocalDate moveIn) { this.moveIn = moveIn; }
    public String getAvailableTime() { return availableTime; }
    public void setAvailableTime(String availableTime) { this.availableTime = availableTime; }
    public String getAgreement() { return agreement; }
    public void setAgreement(String agreement) { this.agreement = agreement; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
