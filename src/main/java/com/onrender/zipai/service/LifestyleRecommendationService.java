package com.onrender.zipai.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onrender.zipai.domain.LifestyleArea;
import com.onrender.zipai.domain.LifestyleScore;
import com.onrender.zipai.dto.lifestyle.LifestyleAreaResponse;
import com.onrender.zipai.dto.lifestyle.LifestyleRecommendRequest;
import com.onrender.zipai.dto.lifestyle.LifestyleRecommendResponse;
import com.onrender.zipai.dto.lifestyle.LifestyleRecommendationItem;
import com.onrender.zipai.dto.lifestyle.LifestyleScoreDetail;
import com.onrender.zipai.dto.lifestyle.LifestyleWeightResponse;
import com.onrender.zipai.repository.LifestyleAreaRepository;
import com.onrender.zipai.repository.LifestyleScoreRepository;

@Service
public class LifestyleRecommendationService {

    private static final String DATA_NOTICE =
            "경기도 공개데이터를 지역별 0~100 상대점수로 정규화해 추천에 사용합니다. "
            + "조용함 점수는 인구 대비 자동차 등록대수를 이용한 추정지표이며 실제 소음 dB 측정값이 아닙니다. "
            + "비용 점수는 일반주택·아파트·빌라 전월세 실거래가격의 상대적 부담도를 반영합니다.";

    private final LifestyleAreaRepository lifestyleAreaRepository;
    private final LifestyleScoreRepository lifestyleScoreRepository;

    public LifestyleRecommendationService(
            LifestyleAreaRepository lifestyleAreaRepository,
            LifestyleScoreRepository lifestyleScoreRepository) {
        this.lifestyleAreaRepository = lifestyleAreaRepository;
        this.lifestyleScoreRepository = lifestyleScoreRepository;
    }

    @Transactional(readOnly = true)
    public List<LifestyleAreaResponse> getAreas() {
        return lifestyleAreaRepository.findByActiveTrueOrderBySidoAscSigunguAscDongAsc()
                .stream()
                .map(LifestyleAreaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LifestyleAreaResponse getArea(Long areaId) {
        if (areaId == null) {
            throw new IllegalArgumentException("지역 번호가 필요합니다.");
        }
        LifestyleArea area = lifestyleAreaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("지역 정보를 찾을 수 없습니다."));
        return LifestyleAreaResponse.from(area);
    }

    @Transactional(readOnly = true)
    public LifestyleRecommendResponse recommend(LifestyleRecommendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Lifestyle 조건이 없습니다.");
        }

        Weights weights = Weights.from(request);
        weights.validate();

        String sido = normalize(request.getSido());
        String sigungu = normalize(request.getSigungu());

        List<ScoredArea> scored = new ArrayList<>();
        for (LifestyleArea area : lifestyleAreaRepository.findByActiveTrueOrderBySidoAscSigunguAscDongAsc()) {
            if (!matches(area, sido, sigungu)) {
                continue;
            }
            LifestyleScore score = lifestyleScoreRepository
                    .findTopByAreaIdOrderBySourceDateDescScoreIdDesc(area.getAreaId())
                    .orElse(null);
            if (score == null) {
                continue;
            }
            double total = calculate(score, weights);
            scored.add(new ScoredArea(area, score, total, buildReasons(score, weights)));
        }

        if (scored.isEmpty()) {
            throw new IllegalArgumentException("선택한 범위에 추천 가능한 Lifestyle 데이터가 없습니다.");
        }

        scored.sort(Comparator
                .comparingDouble(ScoredArea::totalScore).reversed()
                .thenComparing(item -> item.area().getAreaId()));

        List<LifestyleRecommendationItem> items = new ArrayList<>();
        int limit = Math.min(3, scored.size());
        for (int index = 0; index < limit; index++) {
            ScoredArea item = scored.get(index);
            LifestyleArea area = item.area();
            LifestyleScore score = item.score();
            items.add(new LifestyleRecommendationItem(
                    index + 1,
                    area.getAreaId(),
                    area.getAreaCode(),
                    area.getSido(),
                    area.getSigungu(),
                    area.getDong(),
                    areaName(area),
                    round1(item.totalScore()),
                    matchLevel(item.totalScore()),
                    detail(score),
                    item.reasons(),
                    score.getSourceName(),
                    score.getSourceDate()));
        }

        return new LifestyleRecommendResponse(
                items,
                weights.toResponse(),
                scopeLabel(sido, sigungu),
                DATA_NOTICE);
    }

    private boolean matches(LifestyleArea area, String sido, String sigungu) {
        if (sido != null && !sido.equals(area.getSido())) {
            return false;
        }
        return sigungu == null || sigungu.equals(area.getSigungu());
    }

    private double calculate(LifestyleScore score, Weights weights) {
        int numerator = 0;
        int denominator = 0;

        numerator += contribution(score.getTransportScore(), weights.transport());
        denominator += includedWeight(score.getTransportScore(), weights.transport());
        numerator += contribution(score.getConvenienceScore(), weights.convenience());
        denominator += includedWeight(score.getConvenienceScore(), weights.convenience());
        numerator += contribution(score.getMedicalScore(), weights.medical());
        denominator += includedWeight(score.getMedicalScore(), weights.medical());
        numerator += contribution(score.getEducationScore(), weights.education());
        denominator += includedWeight(score.getEducationScore(), weights.education());
        numerator += contribution(score.getParkScore(), weights.park());
        denominator += includedWeight(score.getParkScore(), weights.park());
        numerator += contribution(score.getSafetyScore(), weights.safety());
        denominator += includedWeight(score.getSafetyScore(), weights.safety());
        numerator += contribution(score.getCommercialScore(), weights.commercial());
        denominator += includedWeight(score.getCommercialScore(), weights.commercial());
        numerator += contribution(score.getQuietScore(), weights.quiet());
        denominator += includedWeight(score.getQuietScore(), weights.quiet());
        numerator += contribution(score.getCostScore(), weights.cost());
        denominator += includedWeight(score.getCostScore(), weights.cost());

        if (denominator == 0) {
            throw new IllegalArgumentException("계산할 수 있는 Lifestyle 점수 데이터가 없습니다.");
        }
        return (double) numerator / denominator;
    }

    private int contribution(Integer score, int weight) {
        return score == null || weight == 0 ? 0 : score * weight;
    }

    private int includedWeight(Integer score, int weight) {
        return score == null ? 0 : weight;
    }

    private List<String> buildReasons(LifestyleScore score, Weights weights) {
        Map<String, ReasonCandidate> candidates = new LinkedHashMap<>();
        candidates.put("대중교통", new ReasonCandidate(score.getTransportScore(), weights.transport(), "대중교통 접근성이 생활패턴과 잘 맞습니다."));
        candidates.put("생활편의", new ReasonCandidate(score.getConvenienceScore(), weights.convenience(), "마트·생활편의시설 이용 여건이 좋습니다."));
        candidates.put("의료", new ReasonCandidate(score.getMedicalScore(), weights.medical(), "의료시설 접근성을 중요하게 보는 조건에 강점이 있습니다."));
        candidates.put("교육", new ReasonCandidate(score.getEducationScore(), weights.education(), "교육환경을 중요하게 보는 조건과 잘 맞습니다."));
        candidates.put("공원·녹지", new ReasonCandidate(score.getParkScore(), weights.park(), "공원·녹지 환경을 중요하게 보는 조건에 적합합니다."));
        candidates.put("안전", new ReasonCandidate(score.getSafetyScore(), weights.safety(), "안전을 중요하게 보는 조건에서 높은 점수를 받았습니다."));
        candidates.put("상권", new ReasonCandidate(score.getCommercialScore(), weights.commercial(), "상권과 생활서비스 접근성이 좋은 편입니다."));
        candidates.put("조용함", new ReasonCandidate(score.getQuietScore(), weights.quiet(), "자동차 등록 압력 기반 조용함 추정점수가 선호 조건과 잘 맞습니다."));
        candidates.put("비용", new ReasonCandidate(score.getCostScore(), weights.cost(), "비용 부담을 중요하게 보는 조건에서 상대적으로 유리합니다."));

        return candidates.values().stream()
                .filter(candidate -> candidate.score() != null && candidate.weight() > 0)
                .sorted(Comparator
                        .comparingInt(ReasonCandidate::weightedScore).reversed()
                        .thenComparing(Comparator.comparingInt(ReasonCandidate::scoreValue).reversed()))
                .limit(3)
                .map(ReasonCandidate::message)
                .toList();
    }

    private LifestyleScoreDetail detail(LifestyleScore score) {
        return new LifestyleScoreDetail(
                score.getTransportScore(),
                score.getConvenienceScore(),
                score.getMedicalScore(),
                score.getEducationScore(),
                score.getParkScore(),
                score.getSafetyScore(),
                score.getCommercialScore(),
                score.getQuietScore(),
                score.getCostScore());
    }

    private String matchLevel(double score) {
        if (score >= 90) return "매우 적합";
        if (score >= 80) return "잘 맞음";
        if (score >= 70) return "적합";
        return "비교 필요";
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String areaName(LifestyleArea area) {
        return String.join(" ", safe(area.getSido()), safe(area.getSigungu()), safe(area.getDong()))
                .trim().replaceAll("\\s+", " ");
    }

    private String scopeLabel(String sido, String sigungu) {
        if (sigungu != null) return (sido == null ? "" : sido + " ") + sigungu;
        if (sido != null) return sido;
        return "전체 지역";
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ScoredArea(
            LifestyleArea area,
            LifestyleScore score,
            double totalScore,
            List<String> reasons) {
    }

    private record ReasonCandidate(Integer score, int weight, String message) {
        int weightedScore() { return scoreValue() * weight; }
        int scoreValue() { return score == null ? 0 : score; }
    }

    private record Weights(
            int transport,
            int convenience,
            int medical,
            int education,
            int park,
            int safety,
            int commercial,
            int quiet,
            int cost) {

        static Weights from(LifestyleRecommendRequest request) {
            return new Weights(
                    value(request.getTransportWeight()),
                    value(request.getConvenienceWeight()),
                    value(request.getMedicalWeight()),
                    value(request.getEducationWeight()),
                    value(request.getParkWeight()),
                    value(request.getSafetyWeight()),
                    value(request.getCommercialWeight()),
                    value(request.getQuietWeight()),
                    value(request.getCostWeight()));
        }

        private static int value(Integer value) {
            return value == null ? 0 : value;
        }

        void validate() {
            int[] values = { transport, convenience, medical, education, park, safety, commercial, quiet, cost };
            int sum = 0;
            for (int value : values) {
                if (value < 0 || value > 5) {
                    throw new IllegalArgumentException("Lifestyle 중요도는 0~5 범위로 입력해 주세요.");
                }
                sum += value;
            }
            if (sum == 0) {
                throw new IllegalArgumentException("최소 한 가지 Lifestyle 항목의 중요도를 1 이상으로 선택해 주세요.");
            }
        }

        LifestyleWeightResponse toResponse() {
            return new LifestyleWeightResponse(
                    transport, convenience, medical, education,
                    park, safety, commercial, quiet, cost);
        }
    }
}
