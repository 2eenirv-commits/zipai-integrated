package com.onrender.zipai.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onrender.zipai.domain.LifestyleArea;
import com.onrender.zipai.domain.LifestyleScore;
import com.onrender.zipai.dto.lifestyle.LifestyleMlAlgorithmResult;
import com.onrender.zipai.dto.lifestyle.LifestyleMlRecommendResponse;
import com.onrender.zipai.dto.lifestyle.LifestyleMlRecommendationItem;
import com.onrender.zipai.dto.lifestyle.LifestyleMlSelectedAreaResponse;
import com.onrender.zipai.dto.lifestyle.LifestyleRecommendRequest;
import com.onrender.zipai.repository.LifestyleAreaRepository;
import com.onrender.zipai.repository.LifestyleScoreRepository;

@Service
public class LifestyleMlBridgeService {

    private static final int FEATURE_COUNT = 9;
    private static final int TOP_K = 3;
    private static final long PROCESS_TIMEOUT_SECONDS = 30L;
    private static final String NOTICE =
            "Spring Boot가 DB의 최신 Lifestyle 점수를 임시 CSV로 전달하고, "
            + "Python AI/ML 모듈이 Weighted·Cosine·KNN 추천과 설명을 계산합니다.";

    private final LifestyleAreaRepository lifestyleAreaRepository;
    private final LifestyleScoreRepository lifestyleScoreRepository;
    private final String configuredPythonCommand;
    private final String scriptPath;

    public LifestyleMlBridgeService(
            LifestyleAreaRepository lifestyleAreaRepository,
            LifestyleScoreRepository lifestyleScoreRepository,
            @Value("${zipai.lifestyle.ml.python-command:}") String configuredPythonCommand,
            @Value("${zipai.lifestyle.ml.script-path:rpa/lifestyle_ml/recommendation_service.py}") String scriptPath) {
        this.lifestyleAreaRepository = lifestyleAreaRepository;
        this.lifestyleScoreRepository = lifestyleScoreRepository;
        this.configuredPythonCommand = configuredPythonCommand == null ? "" : configuredPythonCommand.trim();
        this.scriptPath = scriptPath;
    }

    @Transactional(readOnly = true)
    public LifestyleMlRecommendResponse recommend(LifestyleRecommendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Lifestyle 조건이 없습니다.");
        }

        List<Integer> weights = extractAndValidateWeights(request);
        String sido = normalize(request.getSido());
        String sigungu = normalize(request.getSigungu());
        if (sido == null) {
            sido = "경기도";
        }

        // 추천 후보는 선택 시·도의 전체 지역을 유지하고,
        // sigungu는 사용자가 실제 이사 관심 지역으로 선택한 비교 대상에만 사용한다.
        List<AreaScoreRow> rows = loadRows(sido, null);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("선택한 범위에 AI/ML 추천 가능한 Lifestyle 데이터가 없습니다.");
        }

        Path tempCsv = null;
        try {
            tempCsv = Files.createTempFile("zipai-lifestyle-ml-", ".csv");
            writeCsv(tempCsv, rows);
            ProcessResult processResult = runPython(tempCsv, weights, sigungu);
            return parseTsv(processResult.stdout(), rows.size());
        } catch (IOException ex) {
            throw new IllegalStateException("AI/ML 추천용 임시 데이터 처리에 실패했습니다.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI/ML Python 실행이 중단되었습니다.", ex);
        } finally {
            if (tempCsv != null) {
                try {
                    Files.deleteIfExists(tempCsv);
                } catch (IOException ignored) {
                    // 임시파일 삭제 실패는 추천 응답 자체를 실패시키지 않는다.
                }
            }
        }
    }

    private List<AreaScoreRow> loadRows(String sido, String sigungu) {
        List<AreaScoreRow> rows = new ArrayList<>();
        for (LifestyleArea area : lifestyleAreaRepository.findByActiveTrueOrderBySidoAscSigunguAscDongAsc()) {
            if (!sido.equals(area.getSido())) {
                continue;
            }
            if (sigungu != null && !sigungu.equals(area.getSigungu())) {
                continue;
            }

            LifestyleScore score = lifestyleScoreRepository
                    .findTopByAreaIdOrderBySourceDateDescScoreIdDesc(area.getAreaId())
                    .orElse(null);
            if (score == null || hasMissingFeature(score)) {
                continue;
            }

            rows.add(new AreaScoreRow(area, score));
        }
        return rows;
    }

    private boolean hasMissingFeature(LifestyleScore score) {
        return score.getTransportScore() == null
                || score.getConvenienceScore() == null
                || score.getMedicalScore() == null
                || score.getEducationScore() == null
                || score.getParkScore() == null
                || score.getSafetyScore() == null
                || score.getCommercialScore() == null
                || score.getQuietScore() == null
                || score.getCostScore() == null;
    }

    private void writeCsv(Path csvPath, List<AreaScoreRow> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("area_id,area_code,sido,sigungu,transport_score,convenience_score,medical_score,education_score,park_score,safety_score,commercial_score,quiet_score,cost_score");
        for (AreaScoreRow row : rows) {
            LifestyleArea area = row.area();
            LifestyleScore score = row.score();
            lines.add(String.join(",",
                    String.valueOf(area.getAreaId()),
                    csv(area.getAreaCode()),
                    csv(area.getSido()),
                    csv(area.getSigungu()),
                    String.valueOf(score.getTransportScore()),
                    String.valueOf(score.getConvenienceScore()),
                    String.valueOf(score.getMedicalScore()),
                    String.valueOf(score.getEducationScore()),
                    String.valueOf(score.getParkScore()),
                    String.valueOf(score.getSafetyScore()),
                    String.valueOf(score.getCommercialScore()),
                    String.valueOf(score.getQuietScore()),
                    String.valueOf(score.getCostScore())));
        }
        Files.write(csvPath, lines, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private ProcessResult runPython(Path csvPath, List<Integer> weights, String selectedSigungu)
            throws IOException, InterruptedException {
        Path script = Path.of(scriptPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("Lifestyle AI/ML Python 파일을 찾을 수 없습니다: " + script);
        }

        List<String> command = new ArrayList<>(List.of(
                resolvePythonCommand(),
                script.toString(),
                "--source", "csv",
                "--csv", csvPath.toAbsolutePath().toString(),
                "--weights", joinWeights(weights),
                "--top-k", String.valueOf(TOP_K),
                "--format", "tsv"));
        if (selectedSigungu != null) {
            command.add("--selected-sigungu");
            command.add(selectedSigungu);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        // Windows에서는 Python stdout이 cp949로 선택될 수 있다.
        // Java는 UTF-8로 읽으므로 Python 출력 인코딩도 UTF-8로 강제해 한글 깨짐을 원천 차단한다.
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("PYTHONUTF8", "1");
        Process process = processBuilder
                .redirectErrorStream(true)
                .start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Lifestyle AI/ML Python 실행 시간이 30초를 초과했습니다.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Lifestyle AI/ML Python 실행 실패: " + output.toString().trim());
        }
        return new ProcessResult(process.exitValue(), output.toString());
    }

    private LifestyleMlRecommendResponse parseTsv(String output, int fallbackAreaCount) {
        int areaCount = fallbackAreaCount;
        int featureCount = FEATURE_COUNT;
        Map<String, List<LifestyleMlRecommendationItem>> itemsByAlgorithm = new LinkedHashMap<>();
        itemsByAlgorithm.put("weighted", new ArrayList<>());
        itemsByAlgorithm.put("cosine", new ArrayList<>());
        itemsByAlgorithm.put("knn_euclidean", new ArrayList<>());
        Map<String, Integer> overlap = new LinkedHashMap<>();
        Map<String, LifestyleMlRecommendationItem> selectedResults = new LinkedHashMap<>();
        Map<String, Double> selectedFeatureScores = new LinkedHashMap<>();
        String selectedSido = null;
        String selectedSigungu = null;

        for (String rawLine : output.split("\\R")) {
            if (rawLine.isBlank()) {
                continue;
            }
            String[] parts = rawLine.split("\\t", -1);
            switch (parts[0]) {
                case "META" -> {
                    requireParts(parts, 3, rawLine);
                    areaCount = Integer.parseInt(parts[1]);
                    featureCount = Integer.parseInt(parts[2]);
                }
                case "RESULT" -> {
                    requireParts(parts, 11, rawLine);
                    String algorithm = parts[1];
                    List<LifestyleMlRecommendationItem> list = itemsByAlgorithm.get(algorithm);
                    if (list == null) {
                        throw new IllegalStateException("알 수 없는 AI/ML 알고리즘 응답: " + algorithm);
                    }
                    list.add(new LifestyleMlRecommendationItem(
                            Integer.parseInt(parts[2]),
                            Long.valueOf(parts[3]),
                            parts[4],
                            parts[5],
                            parts[6],
                            Double.parseDouble(parts[7]),
                            parts[8].isBlank() ? null : Double.valueOf(parts[8]),
                            parts[10]));
                }
                case "SELECTED" -> {
                    requireParts(parts, 9, rawLine);
                    String algorithm = parts[1];
                    LifestyleMlRecommendationItem selectedItem =
                            new LifestyleMlRecommendationItem(
                                    Integer.parseInt(parts[2]),
                                    Long.valueOf(parts[3]),
                                    parts[4],
                                    parts[5],
                                    parts[6],
                                    Double.parseDouble(parts[7]),
                                    parts[8].isBlank() ? null : Double.valueOf(parts[8]),
                                    "");
                    selectedResults.put(algorithm, selectedItem);
                    selectedSido = parts[5];
                    selectedSigungu = parts[6];
                }
                case "SELECTED_FEATURES" -> {
                    requireParts(parts, 10, rawLine);
                    selectedFeatureScores.put("transport", Double.parseDouble(parts[1]));
                    selectedFeatureScores.put("convenience", Double.parseDouble(parts[2]));
                    selectedFeatureScores.put("medical", Double.parseDouble(parts[3]));
                    selectedFeatureScores.put("education", Double.parseDouble(parts[4]));
                    selectedFeatureScores.put("park", Double.parseDouble(parts[5]));
                    selectedFeatureScores.put("safety", Double.parseDouble(parts[6]));
                    selectedFeatureScores.put("commercial", Double.parseDouble(parts[7]));
                    selectedFeatureScores.put("quiet", Double.parseDouble(parts[8]));
                    selectedFeatureScores.put("cost", Double.parseDouble(parts[9]));
                }
                case "OVERLAP" -> {
                    requireParts(parts, 3, rawLine);
                    overlap.put(parts[1], Integer.parseInt(parts[2]));
                }
                default -> throw new IllegalStateException("해석할 수 없는 AI/ML Python 응답: " + rawLine);
            }
        }

        List<LifestyleMlAlgorithmResult> algorithms = List.of(
                new LifestyleMlAlgorithmResult("weighted", "Weighted Score (Baseline)", itemsByAlgorithm.get("weighted")),
                new LifestyleMlAlgorithmResult("cosine", "Weighted Cosine Similarity", itemsByAlgorithm.get("cosine")),
                new LifestyleMlAlgorithmResult("knn_euclidean", "KNN Euclidean", itemsByAlgorithm.get("knn_euclidean")));

        LifestyleMlSelectedAreaResponse selectedArea = selectedResults.isEmpty()
                ? null
                : new LifestyleMlSelectedAreaResponse(
                        selectedSido,
                        selectedSigungu,
                        selectedResults,
                        selectedFeatureScores);

        return new LifestyleMlRecommendResponse(
                areaCount,
                featureCount,
                algorithms,
                selectedArea,
                overlap,
                NOTICE);
    }

    private List<Integer> extractAndValidateWeights(LifestyleRecommendRequest request) {
        List<Integer> weights = List.of(
                value(request.getTransportWeight()),
                value(request.getConvenienceWeight()),
                value(request.getMedicalWeight()),
                value(request.getEducationWeight()),
                value(request.getParkWeight()),
                value(request.getSafetyWeight()),
                value(request.getCommercialWeight()),
                value(request.getQuietWeight()),
                value(request.getCostWeight()));

        int sum = 0;
        for (int weight : weights) {
            if (weight < 0 || weight > 5) {
                throw new IllegalArgumentException("Lifestyle 중요도는 0~5 범위로 입력해 주세요.");
            }
            sum += weight;
        }
        if (sum == 0) {
            throw new IllegalArgumentException("최소 한 가지 Lifestyle 항목의 중요도를 1 이상으로 선택해 주세요.");
        }
        return weights;
    }

    private String resolvePythonCommand() {
        if (!configuredPythonCommand.isBlank()) {
            return configuredPythonCommand;
        }
        Path windowsVenv = Path.of(".venv", "Scripts", "python.exe");
        if (Files.isRegularFile(windowsVenv)) {
            return windowsVenv.toAbsolutePath().toString();
        }
        Path unixVenv = Path.of(".venv", "bin", "python");
        if (Files.isRegularFile(unixVenv)) {
            return unixVenv.toAbsolutePath().toString();
        }
        return "python";
    }

    private String joinWeights(List<Integer> weights) {
        return weights.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private void requireParts(String[] parts, int minimum, String line) {
        if (parts.length < minimum) {
            throw new IllegalStateException("AI/ML Python 응답 형식이 올바르지 않습니다: " + line);
        }
    }

    private record AreaScoreRow(LifestyleArea area, LifestyleScore score) {
    }

    private record ProcessResult(int exitCode, String stdout) {
    }
}
