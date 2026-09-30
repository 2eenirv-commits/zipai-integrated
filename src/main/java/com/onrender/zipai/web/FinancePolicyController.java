package com.onrender.zipai.web;

import com.onrender.zipai.domain.FinancePolicy;
import com.onrender.zipai.dto.finance.LoanCalculationRequest;
import com.onrender.zipai.dto.finance.LoanCalculationResult;
import com.onrender.zipai.service.FinancePolicyService;
import com.onrender.zipai.service.FinancePolicyUpdateService;
import com.onrender.zipai.service.AdminOperationsService;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/finance")
public class FinancePolicyController {
    private final FinancePolicyService finance;
    private final ZipaiAuthService auth;
    private final FinancePolicyUpdateService updates;
    private final AdminOperationsService adminOperations;

    public FinancePolicyController(FinancePolicyService finance, ZipaiAuthService auth,
                                   FinancePolicyUpdateService updates, AdminOperationsService adminOperations) {
        this.finance = finance;
        this.auth = auth;
        this.updates = updates;
        this.adminOperations = adminOperations;
    }

    @PostMapping("/policy-updates/import")
    public Map<String, Object> importPolicyUpdates(
        @RequestHeader(value = "X-Finance-Import-Token", required = false) String token,
        @RequestBody List<Map<String, Object>> snapshots
    ) {
        updates.verifyImportToken(token);
        return updates.importSnapshots(snapshots);
    }

    @GetMapping("/policy-updates")
    public Map<String, Object> policyUpdates(HttpSession session) {
        auth.admin(session);
        return updates.candidates();
    }

    @PostMapping("/policy-updates/{id}/review")
    public Map<String, Object> reviewPolicyUpdate(
        @PathVariable long id, @RequestBody Map<String, Object> body, HttpSession session
    ) {
        var admin = auth.admin(session);
        String decision = String.valueOf(body.getOrDefault("decision", ""));
        Map<String, Object> result = updates.review(id, admin.getId(), decision);
        adminOperations.audit(admin.getId(), "FINANCE_UPDATE_REVIEWED", "finance_update", String.valueOf(id),
            "decision=" + decision);
        return result;
    }

    @GetMapping("/policies")
    public List<FinancePolicy> listPolicies(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String targetType
    ) {
        return finance.getPolicies(category, targetType);
    }

    @PostMapping("/calculate")
    public LoanCalculationResult calculate(@RequestBody LoanCalculationRequest request) {
        if (request.principal == null || request.principal <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대출 원금은 0보다 커야 합니다.");
        }
        if (request.baseRate == null || request.baseRate < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연 이자율은 0 이상이어야 합니다.");
        }
        if (request.termYears == null || request.termYears <= 0 || request.termYears > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대출 기간은 1~50년으로 입력해 주세요.");
        }
        try {
            return finance.calculateLoan(request);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
    }

    @PostMapping("/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public FinancePolicy createPolicy(@RequestBody FinancePolicy policy, HttpSession session) {
        var admin = auth.admin(session);
        validatePolicy(policy);
        FinancePolicy created = finance.createPolicy(policy);
        adminOperations.audit(admin.getId(), "FINANCE_POLICY_CREATED", "finance_policy", String.valueOf(created.getId()),
            "name=" + created.getName());
        return created;
    }

    @PutMapping("/policies/{id}")
    public FinancePolicy updatePolicy(@PathVariable Long id, @RequestBody FinancePolicy policy, HttpSession session) {
        var admin = auth.admin(session);
        validatePolicy(policy);
        FinancePolicy updated = finance.updatePolicy(id, policy);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 정책을 찾을 수 없습니다.");
        }
        adminOperations.audit(admin.getId(), "FINANCE_POLICY_UPDATED", "finance_policy", String.valueOf(id),
            "name=" + updated.getName());
        return updated;
    }

    @DeleteMapping("/policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePolicy(@PathVariable Long id, HttpSession session) {
        var admin = auth.admin(session);
        if (finance.getPolicyById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 정책을 찾을 수 없습니다.");
        }
        finance.deletePolicy(id);
        adminOperations.audit(admin.getId(), "FINANCE_POLICY_DELETED", "finance_policy", String.valueOf(id), null);
    }

    private static void validatePolicy(FinancePolicy policy) {
        if (blank(policy.getCategory()) || blank(policy.getName()) || blank(policy.getTargetType()) ||
            blank(policy.getLimitInfo()) || blank(policy.getRateInfo()) || blank(policy.getDescription())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정책 필수 항목을 모두 입력해 주세요.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
