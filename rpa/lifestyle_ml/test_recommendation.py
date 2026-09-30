"""DB 없이 실행 가능한 Lifestyle AI/ML 3차 자동 검증."""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.metrics.pairwise import cosine_similarity

from data_loader import FEATURE_COLUMNS, validate_area_frame
from explainability import FEATURE_LABELS
from feature_scaler import minmax_scale, normalized_weights, weighted_feature_space
from recommendation_service import compare_recommenders


def make_fixture() -> pd.DataFrame:
    # 31개 경기도 시군 규모를 재현한 결정적 테스트 데이터.
    rng = np.random.default_rng(20260902)
    rows = []
    for index in range(31):
        row = {
            "area_id": index + 1,
            "area_code": f"TEST-{index + 1:02d}",
            "sido": "경기도",
            "sigungu": f"테스트시군{index + 1:02d}",
        }
        for feature in FEATURE_COLUMNS:
            row[feature] = int(rng.integers(20, 101))
        rows.append(row)
    return validate_area_frame(pd.DataFrame(rows))


def run_tests() -> None:
    frame = make_fixture()
    weights = [5, 4, 3, 2, 4, 5, 3, 4, 5]

    assert len(frame) == 31
    assert len(FEATURE_COLUMNS) == 9
    assert set(FEATURE_LABELS) == set(FEATURE_COLUMNS)

    scaled, _ = minmax_scale(frame)
    assert scaled.shape == (31, 9)
    assert scaled.min() >= -1e-12
    assert scaled.max() <= 1.0 + 1e-12

    importance = normalized_weights(weights)
    assert importance.shape == (9,)
    assert np.all((importance >= 0) & (importance <= 1))

    weighted_areas, user_target = weighted_feature_space(scaled, weights)
    expected_axis_weight = np.sqrt(importance)
    assert np.allclose(user_target, expected_axis_weight)
    assert np.allclose(weighted_areas, scaled * expected_axis_weight)

    # weight=0은 '낮은 점수를 선호'가 아니라 Feature 자체를 계산에서 제외해야 한다.
    zero_test_weights = [5, 0, 3, 0, 4, 5, 0, 4, 5]
    zero_importance = normalized_weights(zero_test_weights)
    zero_areas, zero_target = weighted_feature_space(scaled, zero_test_weights)
    ignored = np.where(zero_importance == 0)[0]
    assert np.all(zero_areas[:, ignored] == 0)
    assert np.all(zero_target[ignored] == 0)

    result = compare_recommenders(frame, weights, top_k=3, explanation_top_n=4)
    for ranking in (result.weighted, result.cosine, result.knn):
        assert len(ranking) == 3
        assert ranking["area_id"].nunique() == 3
        assert ranking["score"].notna().all()
        assert ranking["score"].is_monotonic_decreasing
        assert "explanation" in ranking.columns
        assert "reason_summary" in ranking.columns
        assert ranking["reason_summary"].str.len().gt(0).all()
        for explanation in ranking["explanation"]:
            assert 1 <= len(explanation["top_features"]) <= 4
            for feature in explanation["top_features"]:
                assert feature["feature"] in FEATURE_COLUMNS
                assert feature["label"] == FEATURE_LABELS[feature["feature"]]
                assert 0 <= float(feature["weight"]) <= 5

    # Weighted 설명의 contribution 합은 Weighted Score 자체와 정확히 일치해야 한다.
    for _, row in result.weighted.iterrows():
        explanation = row["explanation"]
        assert np.isclose(explanation["contribution_sum"], row["score"], atol=1e-9)

    # Cosine 설명에서 재계산한 similarity가 추천 점수와 일치해야 한다.
    for _, row in result.cosine.iterrows():
        explanation = row["explanation"]
        assert np.isclose(explanation["cosine_similarity"] * 100.0, row["score"], atol=1e-9)

    # KNN 설명의 Feature별 squared penalty 합의 sqrt가 실제 distance와 일치해야 한다.
    positions = {int(area_id): pos for pos, area_id in enumerate(frame["area_id"].tolist())}
    for _, row in result.knn.iterrows():
        explanation = row["explanation"]
        assert np.isclose(explanation["distance"], row["distance"], atol=1e-9)
        pos = positions[int(row["area_id"])]
        expected_distance = float(np.linalg.norm(weighted_areas[pos] - user_target))
        assert np.isclose(explanation["distance"], expected_distance, atol=1e-9)

    # KNN은 cosine이 아니라 Euclidean이므로 별도 거리 값이 존재해야 한다.
    assert result.knn["algorithm"].eq("knn_euclidean").all()
    assert "distance" in result.knn.columns
    assert np.all(result.knn["distance"].to_numpy() >= 0)
    assert result.knn["distance"].is_monotonic_increasing

    # Cosine의 3차 설명 계산과 실제 cosine 값이 같은 Feature 공간을 쓰는지 추가 확인한다.
    first_cosine_area_id = int(result.cosine.iloc[0]["area_id"])
    pos = positions[first_cosine_area_id]
    direct_cosine = cosine_similarity(
        weighted_areas[pos].reshape(1, -1),
        user_target.reshape(1, -1),
    )[0, 0]
    assert np.isclose(direct_cosine * 100.0, result.cosine.iloc[0]["score"], atol=1e-9)

    # 동일 fixture에서 cosine과 euclidean KNN이 완전히 같은 계산 경로가 아님을 확인한다.
    cosine_ids = result.cosine["area_id"].tolist()
    knn_ids = result.knn["area_id"].tolist()
    cosine_scores = result.cosine["score"].to_numpy()
    knn_scores = result.knn["score"].to_numpy()
    assert cosine_ids != knn_ids or not np.allclose(cosine_scores, knn_scores, atol=1e-9)

    # 설명 Feature 개수를 CLI 설정처럼 변경할 수 있어야 한다.
    one_reason = compare_recommenders(frame, weights, top_k=2, explanation_top_n=1)
    for ranking in (one_reason.weighted, one_reason.cosine, one_reason.knn):
        assert all(len(item["top_features"]) == 1 for item in ranking["explanation"])

    try:
        compare_recommenders(frame, [0] * 9, top_k=3)
    except ValueError:
        pass
    else:
        raise AssertionError("모든 가중치가 0일 때 ValueError가 발생해야 합니다.")

    try:
        compare_recommenders(frame, weights, top_k=3, explanation_top_n=0)
    except ValueError:
        pass
    else:
        raise AssertionError("explanation_top_n=0일 때 ValueError가 발생해야 합니다.")

    print("[PASS] Lifestyle AI/ML 3차 자동검증")
    print("[PASS] 31개 지역 / 9개 Feature")
    print("[PASS] Weighted contribution = Weighted Score")
    print("[PASS] Cosine 설명값 = 실제 Cosine Score")
    print("[PASS] KNN Feature distance = 실제 Euclidean Distance")
    print("[PASS] Feature 한글 라벨 / 중요도 / 원점수 설명")
    print("[PASS] 추천 이유 TOP N 제어")
    print("[PASS] 기존 2차 Weighted/Cosine/KNN 계산 유지")


if __name__ == "__main__":
    run_tests()
