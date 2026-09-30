package com.onrender.zipai.service;

import com.onrender.zipai.domain.FinancePolicy;
import com.onrender.zipai.dto.finance.LoanCalculationRequest;
import com.onrender.zipai.dto.finance.LoanCalculationResult;
import com.onrender.zipai.repository.FinancePolicyRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FinancePolicyService {
    private final FinancePolicyRepository policies;

    public FinancePolicyService(FinancePolicyRepository policies) {
        this.policies = policies;
    }

    public List<FinancePolicy> getPolicies(String category, String targetType) {
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasTarget = targetType != null && !targetType.isBlank();
        if (hasCategory && hasTarget) return policies.findByCategoryAndTargetTypeOrderByIdAsc(category, targetType);
        if (hasCategory) return policies.findByCategoryOrderByIdAsc(category);
        if (hasTarget) return policies.findByTargetTypeOrderByIdAsc(targetType);
        return policies.findAllByOrderByIdAsc();
    }

    public FinancePolicy getPolicyById(Long id) {
        return policies.findById(id).orElse(null);
    }

    public FinancePolicy createPolicy(FinancePolicy policy) {
        LocalDateTime now = LocalDateTime.now();
        policy.setId(null);
        if (policy.getUpdateStatus() == null || policy.getUpdateStatus().isBlank()) {
            policy.setUpdateStatus("current");
        }
        policy.setCreatedAt(now);
        policy.setUpdatedAt(now);
        return policies.save(policy);
    }

    public FinancePolicy updatePolicy(Long id, FinancePolicy details) {
        FinancePolicy policy = policies.findById(id).orElse(null);
        if (policy == null) return null;
        policy.setCategory(details.getCategory());
        policy.setName(details.getName());
        policy.setTargetType(details.getTargetType());
        policy.setLimitInfo(details.getLimitInfo());
        policy.setRateInfo(details.getRateInfo());
        policy.setDescription(details.getDescription());
        policy.setUpdatedAt(LocalDateTime.now());
        return policies.save(policy);
    }

    public void deletePolicy(Long id) {
        policies.deleteById(id);
    }

    public LoanCalculationResult calculateLoan(LoanCalculationRequest request) {
        long principal = request.principal * 10000L;
        double baseRate = request.baseRate != null ? request.baseRate : 0.0;
        int termYears = request.termYears != null ? request.termYears : 30;
        int months = termYears * 12;
        String repayType = request.repayType != null ? request.repayType : "level-both";

        double discount = 0.0;
        if (Boolean.TRUE.equals(request.chkSubscription)) discount += 0.20;
        if (Boolean.TRUE.equals(request.chkMultiChildren)) discount += 0.30;
        if (Boolean.TRUE.equals(request.chkNewborn)) discount += 0.10;
        if (Boolean.TRUE.equals(request.chkElectronic)) discount += 0.10;

        double finalRatePercent = Math.max(0.1, baseRate - discount);
        double rate = (finalRatePercent / 100.0) / 12.0;
        double balance = principal;
        double totalInterest = 0.0;
        List<LoanCalculationResult.ScheduleEntry> schedule = new ArrayList<>();

        for (int m = 1; m <= months; m++) {
            double interest;
            double principalPaid;
            double totalPaid;

            if ("level-both".equals(repayType)) {
                if (rate > 0) {
                    totalPaid = (principal * rate * Math.pow(1 + rate, months)) / (Math.pow(1 + rate, months) - 1);
                    interest = balance * rate;
                    principalPaid = totalPaid - interest;
                } else {
                    totalPaid = (double) principal / months;
                    interest = 0.0;
                    principalPaid = totalPaid;
                }
            } else if ("level-principal".equals(repayType)) {
                principalPaid = (double) principal / months;
                interest = balance * rate;
                totalPaid = principalPaid + interest;
            } else if ("bullet".equals(repayType)) {
                interest = balance * rate;
                principalPaid = m == months ? principal : 0.0;
                totalPaid = principalPaid + interest;
            } else {
                throw new IllegalArgumentException("지원하지 않는 상환 방식입니다.");
            }

            if (m == months) {
                principalPaid = balance;
                totalPaid = principalPaid + interest;
                balance = 0.0;
            } else {
                balance = Math.max(0.0, balance - principalPaid);
            }

            totalInterest += interest;
            schedule.add(new LoanCalculationResult.ScheduleEntry(
                m,
                Math.round(principalPaid),
                Math.round(interest),
                Math.round(totalPaid),
                Math.round(balance)
            ));
        }

        LoanCalculationResult result = new LoanCalculationResult();
        result.firstMonthAmount = schedule.isEmpty() ? 0L : schedule.get(0).totalPaid;
        result.totalPrincipal = principal;
        result.totalInterest = Math.round(totalInterest);
        result.totalRepayment = principal + result.totalInterest;
        result.appliedRate = finalRatePercent;
        result.schedule = schedule;
        return result;
    }
}
