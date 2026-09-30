package com.onrender.zipai.dto.finance;

import java.util.List;

public class LoanCalculationResult {
    public Long firstMonthAmount;
    public Long totalPrincipal;
    public Long totalInterest;
    public Long totalRepayment;
    public Double appliedRate;
    public List<ScheduleEntry> schedule;

    public static class ScheduleEntry {
        public Integer month;
        public Long principalPaid;
        public Long interest;
        public Long totalPaid;
        public Long balance;

        public ScheduleEntry(Integer month, Long principalPaid, Long interest, Long totalPaid, Long balance) {
            this.month = month;
            this.principalPaid = principalPaid;
            this.interest = interest;
            this.totalPaid = totalPaid;
            this.balance = balance;
        }
    }
}
