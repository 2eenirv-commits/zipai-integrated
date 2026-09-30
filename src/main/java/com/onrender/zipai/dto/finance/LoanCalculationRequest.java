package com.onrender.zipai.dto.finance;

public class LoanCalculationRequest {
    public Long principal;
    public Double baseRate;
    public Integer termYears;
    public String repayType;
    public Boolean chkSubscription = false;
    public Boolean chkMultiChildren = false;
    public Boolean chkNewborn = false;
    public Boolean chkElectronic = false;
}
