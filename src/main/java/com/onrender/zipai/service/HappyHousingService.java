package com.onrender.zipai.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.onrender.zipai.domain.EligibilityRule;
import com.onrender.zipai.dto.happyhousing.EligibilityCheckResult;
import com.onrender.zipai.dto.happyhousing.HappyHousingDiagnoseRequest;
import com.onrender.zipai.dto.happyhousing.HappyHousingDiagnoseResponse;
import com.onrender.zipai.dto.happyhousing.HappyHousingRuleResponse;
import com.onrender.zipai.repository.EligibilityRuleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HappyHousingService {

    private final EligibilityRuleRepository eligibilityRuleRepository;

    public HappyHousingRuleResponse getRule(String applicantType, LocalDate noticeDate) {
        if (applicantType == null || applicantType.isBlank()) {
            throw new IllegalArgumentException("신청자 유형을 선택해 주세요.");
        }

        LocalDate targetDate = noticeDate != null ? noticeDate : LocalDate.now();

        EligibilityRule rule = eligibilityRuleRepository
                .findActiveRule(applicantType, targetDate)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 신청유형과 적용일에 사용할 자격기준이 없습니다."));

        return HappyHousingRuleResponse.from(rule);
    }

    public HappyHousingDiagnoseResponse diagnose(HappyHousingDiagnoseRequest request) {
        if (request.getNoticeDate() == null) {
            throw new IllegalArgumentException("모집공고일을 입력해 주세요.");
        }

        EligibilityRule rule = eligibilityRuleRepository
                .findActiveRule(request.getApplicantType(), request.getNoticeDate())
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 신청유형과 모집공고일에 적용할 자격기준이 없습니다."));

        List<EligibilityCheckResult> checks = new ArrayList<>();

        checks.add(checkAge(request, rule));
        checks.add(checkRequiredRadio("HOMELESS", "무주택 요건",
                request.getHomeless(), rule.getHomelessRequired()));
        checks.add(checkRequiredRadio("CATEGORY", "계층 기본요건",
                request.getCategory(), rule.getCategoryRequired()));
        checks.add(checkLimit("INCOME", "월평균소득",
                request.getMonthlyIncome(), rule.getIncomeLimit()));
        checks.add(checkLimit("ASSET", "총자산",
                request.getTotalAssets(), rule.getAssetLimit()));
        checks.add(checkLimit("CAR", "자동차가액",
                request.getCarValue(), rule.getCarLimit()));
        checks.add(checkRequiredRadio("CONNECTION", "지역·직장 연계",
                request.getConnection(), rule.getConnectionRequired()));

        int passed = (int) checks.stream().filter(c -> "yes".equals(c.getState())).count();
        int failed = (int) checks.stream().filter(c -> "no".equals(c.getState())).count();
        int unknown = checks.size() - passed - failed;

        String result = failed > 0 ? "FAIL" : unknown > 0 ? "CHECK" : "PASS";

        String title = failed > 0
                ? failed + "개 항목이 기준을 충족하지 못했어요"
                : unknown > 0
                    ? unknown + "개 항목을 더 확인해 주세요"
                    : "기본요건에 해당할 가능성이 높아요";

        return new HappyHousingDiagnoseResponse(
                result, title, passed, unknown, failed, checks);
    }

    private EligibilityCheckResult checkAge(
            HappyHousingDiagnoseRequest request,
            EligibilityRule rule) {

        if (request.getBirthDate() == null || request.getNoticeDate() == null) {
            return new EligibilityCheckResult(
                    "AGE", "unknown", "생년월일과 모집공고일을 입력해 주세요.");
        }

        if (request.getBirthDate().isAfter(request.getNoticeDate())) {
            return new EligibilityCheckResult(
                    "AGE", "unknown", "생년월일은 모집공고일보다 이후일 수 없습니다.");
        }

        int age = Period.between(
                request.getBirthDate(), request.getNoticeDate()).getYears();

        Integer minAge = rule.getMinAge();
        Integer maxAge = rule.getMaxAge();

        if (minAge == null && maxAge == null) {
            return new EligibilityCheckResult(
                    "AGE", "yes",
                    "공고일 기준 만 " + age + "세입니다. 별도 연령 상한·하한이 없는 유형입니다.");
        }

        boolean passed =
                (minAge == null || age >= minAge) &&
                (maxAge == null || age <= maxAge);

        String range = "";
        if (minAge != null && maxAge != null) {
            range = " (기준 만 " + minAge + "~" + maxAge + "세)";
        } else if (minAge != null) {
            range = " (기준 만 " + minAge + "세 이상)";
        } else if (maxAge != null) {
            range = " (기준 만 " + maxAge + "세 이하)";
        }

        return new EligibilityCheckResult(
                "AGE", passed ? "yes" : "no",
                "공고일 기준 만 " + age + "세입니다." + range);
    }

    private EligibilityCheckResult checkLimit(
            String type,
            String label,
            Long actual,
            Long limit) {

        if (limit == null) {
            return new EligibilityCheckResult(
                    type, "yes", label + "은 현재 신청유형의 별도 상한 기준이 없습니다.");
        }

        if (actual == null) {
            return new EligibilityCheckResult(
                    type, "unknown", label + "을 입력해 주세요.");
        }

        boolean passed = actual <= limit;

        return new EligibilityCheckResult(
                type, passed ? "yes" : "no",
                label + " " + actual + " / 기준 " + limit);
    }

    private EligibilityCheckResult checkRequiredRadio(
            String type,
            String label,
            String state,
            Boolean required) {

        if (!Boolean.TRUE.equals(required)) {
            return new EligibilityCheckResult(
                    type, "yes", label + "은 현재 신청유형의 필수 판정항목이 아닙니다.");
        }

        if (state == null || "unknown".equalsIgnoreCase(state)) {
            return new EligibilityCheckResult(
                    type, "unknown", label + "을 확인해 주세요.");
        }

        if ("yes".equalsIgnoreCase(state)) {
            return new EligibilityCheckResult(
                    type, "yes", label + " 충족");
        }

        if ("no".equalsIgnoreCase(state)) {
            return new EligibilityCheckResult(
                    type, "no", label + " 미충족");
        }

        return new EligibilityCheckResult(
                type, "unknown", label + " 값이 올바르지 않습니다.");
    }
}
