package com.onrender.zipai.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.onrender.zipai.domain.CrawlHistory;
import com.onrender.zipai.domain.HousingNotice;
import com.onrender.zipai.domain.HousingNoticeRule;
import com.onrender.zipai.dto.happyhousing.notice.CrawlHistoryResponse;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeDetailResponse;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeRuleResponse;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeMatchRequest;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeMatchResponse;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeMatchCheckResponse;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeSummaryResponse;
import com.onrender.zipai.repository.CrawlHistoryRepository;
import com.onrender.zipai.repository.HousingNoticeRepository;
import com.onrender.zipai.repository.HousingNoticeRuleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HappyHousingNoticeService {

    private final HousingNoticeRepository housingNoticeRepository;
    private final HousingNoticeRuleRepository housingNoticeRuleRepository;
    private final CrawlHistoryRepository crawlHistoryRepository;
    private final ObjectMapper objectMapper;

    public List<HousingNoticeSummaryResponse> getNotices(
            String query,
            String region,
            String status,
            String housingType,
            String applicantType) {

        String normalizedQuery = normalize(query);
        String normalizedRegion = normalize(region);
        String normalizedStatus = normalize(status);
        String normalizedHousingType = normalize(housingType);
        String normalizedApplicantType = normalize(applicantType);

        return housingNoticeRepository
                .findAllByOrderByNoticeDateDescNoticeIdDesc()
                .stream()
                .filter(notice -> matchesAny(normalizedQuery,
                        notice.getTitle(), notice.getRegion(), notice.getHousingType(), notice.getPanId()))
                .filter(notice -> matches(notice.getRegion(), normalizedRegion))
                .filter(notice -> matches(notice.getStatus(), normalizedStatus))
                .filter(notice -> matches(notice.getHousingType(), normalizedHousingType))
                .filter(notice -> normalizedApplicantType == null
                        || housingNoticeRuleRepository.existsByNoticeIdAndApplicantType(
                                notice.getNoticeId(), normalizedApplicantType))
                .map(this::toSummary)
                .toList();
    }

    private boolean matchesAny(String keyword, String... values) {
        if (keyword == null) return true;
        String compactKeyword = keyword.replaceAll("\\s+", "");
        for (String value : values) {
            if (value == null) continue;
            String normalizedValue = value.toLowerCase(java.util.Locale.ROOT);
            if (normalizedValue.contains(keyword)
                    || normalizedValue.replaceAll("\\s+", "").contains(compactKeyword)) return true;
        }
        return false;
    }

    public HousingNoticeDetailResponse getNotice(Long noticeId) {
        HousingNotice notice = housingNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("해당 행복주택 공고를 찾을 수 없습니다."));

        List<HousingNoticeRuleResponse> rules = housingNoticeRuleRepository
                .findByNoticeIdOrderByApplicantTypeAscSourcePdfAsc(noticeId)
                .stream()
                .map(this::toRuleResponse)
                .toList();

        return toDetail(notice, rules);
    }

    public HousingNoticeDetailResponse getNoticeByPanId(String panId) {
        HousingNotice notice = housingNoticeRepository.findByPanId(panId)
                .orElseThrow(() -> new IllegalArgumentException("해당 pan_id의 행복주택 공고를 찾을 수 없습니다."));
        return getNotice(notice.getNoticeId());
    }

    public List<HousingNoticeRuleResponse> getRules(Long noticeId, String applicantType) {
        if (!housingNoticeRepository.existsById(noticeId)) {
            throw new IllegalArgumentException("해당 행복주택 공고를 찾을 수 없습니다.");
        }

        List<HousingNoticeRule> rules;
        if (applicantType == null || applicantType.isBlank()) {
            rules = housingNoticeRuleRepository
                    .findByNoticeIdOrderByApplicantTypeAscSourcePdfAsc(noticeId);
        } else {
            rules = housingNoticeRuleRepository
                    .findByNoticeIdAndApplicantTypeOrderByUpdatedAtDesc(noticeId, applicantType.trim());
        }

        return rules.stream()
                .map(this::toRuleResponse)
                .toList();
    }


    public List<HousingNoticeMatchResponse> matchNotices(HousingNoticeMatchRequest request) {
        String applicantType = mapApplicantType(request.getApplicantType());
        if (applicantType == null) {
            throw new IllegalArgumentException("신청 계층을 선택해 주세요.");
        }

        return housingNoticeRepository.findAllByOrderByNoticeDateDescNoticeIdDesc()
                .stream()
                .filter(notice -> housingNoticeRuleRepository
                        .existsByNoticeIdAndApplicantType(notice.getNoticeId(), applicantType))
                .map(notice -> matchNotice(notice, applicantType, request))
                .toList();
    }

    private HousingNoticeMatchResponse matchNotice(
            HousingNotice notice,
            String applicantType,
            HousingNoticeMatchRequest request) {

        List<HousingNoticeRule> candidates = housingNoticeRuleRepository
                .findByNoticeIdAndApplicantTypeOrderByUpdatedAtDesc(notice.getNoticeId(), applicantType);

        HousingNoticeRule selectedRule = candidates.stream()
                .sorted(Comparator.comparing((HousingNoticeRule rule) ->
                        !"parsed".equalsIgnoreCase(rule.getValidationStatus())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("공고 규칙을 찾을 수 없습니다."));

        List<HousingNoticeMatchCheckResponse> checks = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(selectedRule.getRuleJson());
            JsonNode rules = root;

            boolean structuredValidationAdded = false;
            if (!"parsed".equalsIgnoreCase(selectedRule.getValidationStatus())) {
                structuredValidationAdded = addStructuredValidationChecks(checks, root);
                if (!structuredValidationAdded) {
                    addCheck(checks, "RULE_VALIDATION", "CHECK",
                            "이 공고는 복합·완화·순위별 조건이 있어 원문 최종 확인이 필요합니다.");
                }
            }

            evaluateAge(checks, notice, request, rules);
            evaluateRequiredRadio(checks, "HOMELESS", request.getHomeless(),
                    bool(rules, "homeless_required") || bool(rules, "homeless_household_required"),
                    "무주택 요건");

            evaluateCategory(checks, request.getCategory());
            evaluateIncome(checks, request, rules);
            evaluateAssets(checks, request, rules);
            evaluateCar(checks, request, rules);

            if (bool(rules, "employment_in_industrial_area_required")) {
                evaluateRequiredRadio(checks, "CONNECTION", request.getConnection(), true,
                        "산업단지 재직·연계 요건");
            }

            if (bool(rules, "subscription_required")) {
                addCheck(checks, "SUBSCRIPTION", "CHECK",
                        "주택청약종합저축 가입 또는 가입사실 증빙 여부를 공고문에서 확인해야 합니다.");
            }

            if (!structuredValidationAdded
                    && (bool(rules, "complex_specific_rules_detected") || root.path("special_notice").asBoolean(false))) {
                addCheck(checks, "SPECIAL_RULE", "CHECK",
                        "단지별·순위별 세부조건이 있어 구조화 규칙과 공고 원문을 함께 확인해야 합니다.");
            }
        } catch (Exception ex) {
            addCheck(checks, "RULE_JSON", "CHECK",
                    "구조화 규칙을 완전히 해석하지 못해 공고 원문 확인이 필요합니다.");
        }

        int passed = (int) checks.stream().filter(c -> "PASS".equals(c.getState())).count();
        int failed = (int) checks.stream().filter(c -> "FAIL".equals(c.getState())).count();
        int review = checks.size() - passed - failed;

        String recommendationStatus;
        String recommendationTitle;
        if (failed > 0) {
            recommendationStatus = "NOT_ELIGIBLE";
            recommendationTitle = "조건 불충족";
        } else if (review > 0) {
            recommendationStatus = "REVIEW";
            recommendationTitle = "추가 확인 필요";
        } else {
            recommendationStatus = "CANDIDATE";
            recommendationTitle = "추천 가능";
        }

        return HousingNoticeMatchResponse.builder()
                .noticeId(notice.getNoticeId())
                .panId(notice.getPanId())
                .title(notice.getTitle())
                .region(notice.getRegion())
                .noticeDate(notice.getNoticeDate())
                .closingDate(notice.getClosingDate())
                .noticeStatus(notice.getStatus())
                .applicantType(applicantType)
                .validationStatus(selectedRule.getValidationStatus())
                .recommendationStatus(recommendationStatus)
                .recommendationTitle(recommendationTitle)
                .passedCount(passed)
                .checkCount(review)
                .failedCount(failed)
                .checks(checks)
                .build();
    }

    private boolean addStructuredValidationChecks(
            List<HousingNoticeMatchCheckResponse> checks,
            JsonNode root) {
        JsonNode validation = root.get("validation");
        if (validation == null || validation.isNull()) {
            return false;
        }

        JsonNode reasons = validation.get("reasons");
        if (reasons == null || !reasons.isArray() || reasons.isEmpty()) {
            return false;
        }

        int added = 0;
        for (JsonNode reason : reasons) {
            JsonNode messageNode = reason.get("message");
            if (messageNode == null || messageNode.isNull()) {
                continue;
            }
            String message = messageNode.asText();
            if (message == null || message.isBlank()) {
                continue;
            }
            JsonNode codeNode = reason.get("code");
            String code = codeNode == null || codeNode.isNull()
                    ? "MANUAL_REVIEW"
                    : codeNode.asText();
            addCheck(checks, "VALIDATION_" + code, "CHECK", message);
            added++;
        }
        return added > 0;
    }

    private void evaluateAge(
            List<HousingNoticeMatchCheckResponse> checks,
            HousingNotice notice,
            HousingNoticeMatchRequest request,
            JsonNode rules) {
        Integer minAge = integer(rules, "age_min");
        Integer maxAge = integer(rules, "age_max");
        if (minAge == null && maxAge == null) {
            return;
        }
        if (request.getBirthDate() == null || notice.getNoticeDate() == null) {
            addCheck(checks, "AGE", "CHECK", "생년월일 또는 공고일이 없어 연령을 자동 확인할 수 없습니다.");
            return;
        }
        LocalDate birthDate = request.getBirthDate();
        LocalDate noticeDate = notice.getNoticeDate();
        if (birthDate.isAfter(noticeDate)) {
            addCheck(checks, "AGE", "FAIL", "생년월일이 모집공고일보다 늦습니다.");
            return;
        }
        int age = Period.between(birthDate, noticeDate).getYears();
        boolean pass = (minAge == null || age >= minAge) && (maxAge == null || age <= maxAge);
        String range = minAge != null && maxAge != null
                ? "만 " + minAge + "~" + maxAge + "세"
                : minAge != null ? "만 " + minAge + "세 이상" : "만 " + maxAge + "세 이하";
        addCheck(checks, "AGE", pass ? "PASS" : "FAIL",
                "공고일 기준 만 " + age + "세 / 기준 " + range);
    }

    private void evaluateCategory(
            List<HousingNoticeMatchCheckResponse> checks,
            String value) {
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
            addCheck(checks, "CATEGORY", "CHECK", "선택 계층의 기본요건을 추가 확인해야 합니다.");
        } else if ("no".equalsIgnoreCase(value)) {
            addCheck(checks, "CATEGORY", "FAIL", "선택 계층의 기본요건을 충족하지 않는 것으로 입력했습니다.");
        } else {
            addCheck(checks, "CATEGORY", "PASS", "선택 계층의 기본요건을 충족하는 것으로 입력했습니다.");
        }
    }

    private void evaluateIncome(
            List<HousingNoticeMatchCheckResponse> checks,
            HousingNoticeMatchRequest request,
            JsonNode rules) {
        if (bool(rules, "income_requirement_exempt")) {
            addCheck(checks, "INCOME", "PASS", "이 공고의 구조화 규칙에서는 소득요건이 배제되어 있습니다.");
            return;
        }

        if (request.getMonthlyIncome() == null) {
            addCheck(checks, "INCOME", "CHECK", "실제 월평균소득을 입력하면 공고 기준과 비교할 수 있습니다.");
            return;
        }
        if (request.getHouseholdSize() == null || request.getHouseholdSize() < 1) {
            addCheck(checks, "INCOME", "CHECK", "공고 소득표를 적용할 가구원수를 입력해야 합니다.");
            return;
        }

        JsonNode table = fieldNode(rules, "income_amount_table");
        if (table == null || table.get("rows") == null || !table.get("rows").isArray()) {
            addCheck(checks, "INCOME", "CHECK", "가구원수별 월평균소득 금액표가 아직 구조화되지 않았습니다.");
            return;
        }

        int householdSize = request.getHouseholdSize();
        JsonNode selectedRow = null;
        JsonNode sixPersonRow = null;
        for (JsonNode row : table.get("rows")) {
            JsonNode sizeNode = row.get("household_size");
            if (sizeNode == null || !sizeNode.isNumber()) continue;
            int size = sizeNode.asInt();
            if (size == householdSize) selectedRow = row;
            if (size == 6) sixPersonRow = row;
        }

        double generalLimitWon = -1;
        double dualLimitWon = -1;
        if (selectedRow != null) {
            JsonNode generalNode = selectedRow.get("general_limit_won");
            JsonNode dualNode = selectedRow.get("dual_income_limit_won");
            if (generalNode != null && generalNode.isNumber()) generalLimitWon = generalNode.asDouble();
            if (dualNode != null && dualNode.isNumber()) dualLimitWon = dualNode.asDouble();
        } else if (householdSize > 6 && sixPersonRow != null) {
            JsonNode baseGeneral = sixPersonRow.get("general_limit_won");
            JsonNode baseDual = sixPersonRow.get("dual_income_limit_won");
            JsonNode extraNode = table.get("extra_per_person_won");
            if (baseGeneral != null && baseGeneral.isNumber() && extraNode != null && extraNode.isNumber()) {
                double extra = extraNode.asDouble() * (householdSize - 6);
                generalLimitWon = baseGeneral.asDouble() + extra;
                if (baseDual != null && baseDual.isNumber()) dualLimitWon = baseDual.asDouble() + extra;
            }
        }

        if (generalLimitWon < 0) {
            addCheck(checks, "INCOME", "CHECK", householdSize + "인 가구의 소득 상한금액을 자동 확인하지 못했습니다.");
            return;
        }

        double actualWon = request.getMonthlyIncome() * 10000.0;
        String generalManwon = formatManwon(generalLimitWon);
        if (actualWon <= generalLimitWon) {
            addCheck(checks, "INCOME", "PASS",
                    "입력 " + request.getMonthlyIncome() + "만원 / 공고 " + householdSize + "인 기준 " + generalManwon + "만원 이하");
            return;
        }

        if (dualLimitWon > 0) {
            String dualManwon = formatManwon(dualLimitWon);
            if (actualWon <= dualLimitWon) {
                addCheck(checks, "INCOME", "CHECK",
                        "일반가구 상한 " + generalManwon + "만원은 초과하지만 맞벌이 상한 " + dualManwon
                                + "만원 이내입니다. 맞벌이 적용 여부를 확인해야 합니다.");
            } else {
                addCheck(checks, "INCOME", "FAIL",
                        "입력 " + request.getMonthlyIncome() + "만원 / 공고 최대 확인 상한 " + dualManwon + "만원 초과");
            }
            return;
        }

        if (integer(rules, "income_percent_dual_income") != null) {
            addCheck(checks, "INCOME", "CHECK",
                    "일반가구 상한 " + generalManwon + "만원을 초과했습니다. 맞벌이 기준 적용 여부와 금액을 원문에서 확인해야 합니다.");
            return;
        }

        addCheck(checks, "INCOME", "FAIL",
                "입력 " + request.getMonthlyIncome() + "만원 / 공고 " + householdSize + "인 기준 " + generalManwon + "만원 초과");
    }

    private String formatManwon(double won) {
        double manwon = won / 10000.0;
        String text = String.format(java.util.Locale.ROOT, "%.4f", manwon);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void evaluateAssets(
            List<HousingNoticeMatchCheckResponse> checks,
            HousingNoticeMatchRequest request,
            JsonNode rules) {
        if (bool(rules, "asset_requirement_exempt")) {
            addCheck(checks, "ASSET", "PASS", "이 공고의 구조화 규칙에서는 총자산 요건이 배제되어 있습니다.");
            return;
        }
        Long limit = number(rules, "asset_limit_manwon");
        if (limit == null) {
            addCheck(checks, "ASSET", "CHECK", "총자산 기준을 자동 확인하지 못했습니다.");
            return;
        }
        if (request.getTotalAssets() == null) {
            addCheck(checks, "ASSET", "CHECK", "총자산을 입력하면 공고 기준과 비교할 수 있습니다.");
            return;
        }
        boolean pass = request.getTotalAssets() <= limit;
        addCheck(checks, "ASSET", pass ? "PASS" : "FAIL",
                "입력 " + request.getTotalAssets() + "만원 / 공고 기준 " + limit + "만원 이하");
    }

    private void evaluateCar(
            List<HousingNoticeMatchCheckResponse> checks,
            HousingNoticeMatchRequest request,
            JsonNode rules) {
        if (bool(rules, "car_no_ownership_required")) {
            if (request.getCarValue() == null) {
                addCheck(checks, "CAR", "CHECK", "이 공고는 자동차 미소유 요건이 있어 차량 보유 여부 확인이 필요합니다.");
            } else {
                boolean pass = request.getCarValue() == 0;
                addCheck(checks, "CAR", pass ? "PASS" : "FAIL",
                        pass ? "자동차가액 0만원으로 입력되어 미소유 조건과 일치합니다."
                                : "이 공고는 자동차 미소유 요건이 있습니다.");
            }
            return;
        }

        Long limit = number(rules, "car_limit_manwon");
        if (limit == null) {
            addCheck(checks, "CAR", "CHECK", "자동차 기준을 자동 확인하지 못했습니다.");
            return;
        }
        if (request.getCarValue() == null) {
            addCheck(checks, "CAR", "CHECK", "자동차가액을 입력하면 공고 기준과 비교할 수 있습니다.");
            return;
        }
        boolean pass = request.getCarValue() <= limit;
        addCheck(checks, "CAR", pass ? "PASS" : "FAIL",
                "입력 " + request.getCarValue() + "만원 / 공고 기준 " + limit + "만원 이하");
    }

    private void evaluateRequiredRadio(
            List<HousingNoticeMatchCheckResponse> checks,
            String type,
            String value,
            boolean required,
            String label) {
        if (!required) {
            return;
        }
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
            addCheck(checks, type, "CHECK", label + " 충족 여부를 추가 확인해야 합니다.");
        } else if ("no".equalsIgnoreCase(value)) {
            addCheck(checks, type, "FAIL", label + "을 충족하지 않는 것으로 입력했습니다.");
        } else {
            addCheck(checks, type, "PASS", label + "을 충족하는 것으로 입력했습니다.");
        }
    }

    private void addCheck(
            List<HousingNoticeMatchCheckResponse> checks,
            String type,
            String state,
            String message) {
        checks.add(HousingNoticeMatchCheckResponse.builder()
                .type(type)
                .state(state)
                .message(message)
                .build());
    }

    private JsonNode fieldNode(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode direct = node.get(field);
        if (direct != null && !direct.isNull()) {
            return direct;
        }
        JsonNode rules = node.get("rules");
        if (rules != null && !rules.isNull()) {
            JsonNode value = rules.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        JsonNode parsed = node.get("parsed");
        if (parsed != null && !parsed.isNull()) {
            JsonNode value = parsed.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = fieldNode(node, field);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private Long number(JsonNode node, String field) {
        JsonNode value = fieldNode(node, field);
        return value != null && value.isNumber() ? value.asLong() : null;
    }

    private boolean bool(JsonNode node, String field) {
        JsonNode value = fieldNode(node, field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    private String mapApplicantType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim()) {
            case "newlywed", "singleParent" -> "newlywed_single_parent";
            case "student", "youth", "senior", "benefit", "industrial", "newlywed_single_parent" -> value.trim();
            default -> null;
        };
    }

    public CrawlHistoryResponse getLatestCrawlHistory() {
        CrawlHistory history = crawlHistoryRepository
                .findFirstByOrderByStartedAtDesc()
                .orElseThrow(() -> new IllegalArgumentException("크롤링 실행 이력이 없습니다."));

        return CrawlHistoryResponse.builder()
                .crawlId(history.getCrawlId())
                .crawlerName(history.getCrawlerName())
                .startedAt(history.getStartedAt())
                .finishedAt(history.getFinishedAt())
                .status(history.getStatus())
                .noticeCount(history.getNoticeCount())
                .ruleCount(history.getRuleCount())
                .parsedRuleCount(history.getParsedRuleCount())
                .validationRuleCount(history.getValidationRuleCount())
                .errorMessage(history.getErrorMessage())
                .build();
    }

    private HousingNoticeSummaryResponse toSummary(HousingNotice notice) {
        return HousingNoticeSummaryResponse.builder()
                .noticeId(notice.getNoticeId())
                .panId(notice.getPanId())
                .title(notice.getTitle())
                .region(notice.getRegion())
                .noticeDate(notice.getNoticeDate())
                .closingDate(notice.getClosingDate())
                .status(notice.getStatus())
                .housingType(notice.getHousingType())
                .crawledAt(notice.getCrawledAt())
                .build();
    }

    private HousingNoticeRuleResponse toRuleResponse(HousingNoticeRule rule) {
        return HousingNoticeRuleResponse.builder()
                .ruleId(rule.getRuleId())
                .noticeId(rule.getNoticeId())
                .panId(rule.getPanId())
                .applicantType(rule.getApplicantType())
                .sourcePdf(rule.getSourcePdf())
                .validationStatus(rule.getValidationStatus())
                .ruleJson(rule.getRuleJson())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private HousingNoticeDetailResponse toDetail(
            HousingNotice notice,
            List<HousingNoticeRuleResponse> rules) {

        return HousingNoticeDetailResponse.builder()
                .noticeId(notice.getNoticeId())
                .panId(notice.getPanId())
                .source(notice.getSource())
                .title(notice.getTitle())
                .region(notice.getRegion())
                .noticeDate(notice.getNoticeDate())
                .postingDate(notice.getPostingDate())
                .closingDate(notice.getClosingDate())
                .status(notice.getStatus())
                .housingType(notice.getHousingType())
                .pdfFileId(notice.getPdfFileId())
                .pdfFileName(notice.getPdfFileName())
                .hwpxFileId(notice.getHwpxFileId())
                .hwpxFileName(notice.getHwpxFileName())
                .detailEndpoint(notice.getDetailEndpoint())
                .crawledAt(notice.getCrawledAt())
                .rules(rules)
                .build();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(String actual, String expectedNormalized) {
        if (expectedNormalized == null) {
            return true;
        }
        return actual != null
                && actual.trim().toLowerCase(Locale.ROOT).contains(expectedNormalized);
    }
}
