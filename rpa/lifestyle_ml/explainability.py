"""ZipAI Lifestyle AI/ML 3차 - 추천 이유(Explainability) 생성.

각 추천 알고리즘이 실제로 사용한 계산을 Feature 단위로 풀어서,
추천 지역마다 '왜 이 지역이 추천됐는가'를 구조화된 데이터로 제공한다.
"""
from __future__ import annotations

from typing import Final

import numpy as np
import pandas as pd
from sklearn.metrics.pairwise import cosine_similarity

from data_loader import FEATURE_COLUMNS
from feature_scaler import minmax_scale, normalized_weights, validate_weights, weighted_feature_space


FEATURE_LABELS: Final[dict[str, str]] = {
    "transport_score": "교통",
    "convenience_score": "편의시설",
    "medical_score": "의료",
    "education_score": "교육",
    "park_score": "공원",
    "safety_score": "안전",
    "commercial_score": "상권",
    "quiet_score": "조용함(추정)",
    "cost_score": "주거비",
}


def _feature_detail(
    feature: str,
    raw_score: float,
    weight: float,
    metric_value: float,
    metric_name: str,
) -> dict[str, float | str]:
    return {
        "feature": feature,
        "label": FEATURE_LABELS[feature],
        "raw_score": float(raw_score),
        "weight": float(weight),
        metric_name: float(metric_value),
    }


def _summary(details: list[dict[str, float | str]]) -> str:
    return ", ".join(
        f"{item['label']} {float(item['raw_score']):.1f}점(중요도 {float(item['weight']):g})"
        for item in details
    )


def explain_weighted_area(
    area_row: pd.Series,
    weights: list[float] | np.ndarray,
    top_n: int = 4,
) -> dict[str, object]:
    """Weighted Score의 Feature별 가중 점수 기여도를 설명한다.

    contribution_i = raw_score_i * weight_i / sum(weight)
    모든 contribution의 합은 해당 지역의 Weighted Score와 같다.
    """
    weight_vector = validate_weights(weights)
    raw_scores = area_row[FEATURE_COLUMNS].to_numpy(dtype=float)
    contributions = raw_scores * weight_vector / weight_vector.sum()

    details = [
        _feature_detail(feature, raw_scores[i], weight_vector[i], contributions[i], "contribution")
        for i, feature in enumerate(FEATURE_COLUMNS)
        if weight_vector[i] > 0
    ]
    details.sort(key=lambda item: float(item["contribution"]), reverse=True)
    top_details = details[:top_n]

    return {
        "algorithm": "weighted",
        "summary": _summary(top_details),
        "top_features": top_details,
        "contribution_sum": float(contributions.sum()),
    }


def explain_cosine_area(
    frame: pd.DataFrame,
    area_position: int,
    weights: list[float] | np.ndarray,
    top_n: int = 4,
) -> dict[str, object]:
    """Weighted Cosine Similarity의 Feature별 정렬(alignment) 기여를 설명한다.

    2차의 sqrt(weight) Feature 공간에서는 cosine 내적의 Feature별 항이
    scaled_score_i * normalized_weight_i 이다.

    cosine 자체는 벡터 norm으로도 나뉘므로 개별 항의 단순 합이 최종 score는
    아니지만, 어떤 Feature가 방향 유사도에 크게 기여했는지를 보여준다.
    """
    weight_vector = validate_weights(weights)
    importance = normalized_weights(weight_vector)
    scaled, _ = minmax_scale(frame)
    weighted_areas, user_target = weighted_feature_space(scaled, weight_vector)

    scaled_scores = scaled[area_position]
    raw_scores = frame.iloc[area_position][FEATURE_COLUMNS].to_numpy(dtype=float)
    alignment = scaled_scores * importance

    similarity = float(
        cosine_similarity(
            weighted_areas[area_position].reshape(1, -1),
            user_target.reshape(1, -1),
        )[0, 0]
    )

    details = [
        _feature_detail(feature, raw_scores[i], weight_vector[i], alignment[i], "alignment")
        for i, feature in enumerate(FEATURE_COLUMNS)
        if weight_vector[i] > 0
    ]
    details.sort(key=lambda item: float(item["alignment"]), reverse=True)
    top_details = details[:top_n]

    return {
        "algorithm": "cosine",
        "summary": _summary(top_details),
        "top_features": top_details,
        "cosine_similarity": similarity,
    }


def explain_knn_area(
    frame: pd.DataFrame,
    area_position: int,
    weights: list[float] | np.ndarray,
    top_n: int = 4,
) -> dict[str, object]:
    """KNN Euclidean의 Feature별 거리와 '잘 맞는 항목'을 설명한다.

    2차 Feature 공간에서 Feature별 squared distance는:
      normalized_weight_i * (1 - scaled_score_i)^2

    전체 Euclidean distance는 이 squared penalty들의 합에 sqrt를 취한 값이다.
    match_strength는 중요도가 높은 Feature가 목표(1.0)에 얼마나 가까운지 보기 위해
      importance_i * (1 - (1 - scaled_score_i)^2)
    로 정의한다. 순위 계산 자체는 squared penalty 기반 Euclidean distance를 사용한다.
    """
    weight_vector = validate_weights(weights)
    importance = normalized_weights(weight_vector)
    scaled, _ = minmax_scale(frame)
    scaled_scores = scaled[area_position]
    raw_scores = frame.iloc[area_position][FEATURE_COLUMNS].to_numpy(dtype=float)

    squared_penalty = importance * np.square(1.0 - scaled_scores)
    match_strength = importance * (1.0 - np.square(1.0 - scaled_scores))
    distance = float(np.sqrt(squared_penalty.sum()))

    details: list[dict[str, float | str]] = []
    for i, feature in enumerate(FEATURE_COLUMNS):
        if weight_vector[i] <= 0:
            continue
        item = _feature_detail(
            feature,
            raw_scores[i],
            weight_vector[i],
            match_strength[i],
            "match_strength",
        )
        item["squared_penalty"] = float(squared_penalty[i])
        details.append(item)

    details.sort(key=lambda item: float(item["match_strength"]), reverse=True)
    top_details = details[:top_n]

    penalties = sorted(details, key=lambda item: float(item["squared_penalty"]), reverse=True)

    return {
        "algorithm": "knn_euclidean",
        "summary": _summary(top_details),
        "top_features": top_details,
        "largest_distance_penalties": penalties[:top_n],
        "distance": distance,
    }


def attach_explanations(
    frame: pd.DataFrame,
    ranking: pd.DataFrame,
    weights: list[float] | np.ndarray,
    algorithm: str,
    top_n: int = 4,
) -> pd.DataFrame:
    """추천 결과 DataFrame에 구조화된 설명을 붙인다."""
    if top_n < 1:
        raise ValueError("top_n은 1 이상이어야 합니다.")

    positions = {int(area_id): pos for pos, area_id in enumerate(frame["area_id"].tolist())}
    explained = ranking.copy()
    explanations: list[dict[str, object]] = []

    for _, ranked_row in explained.iterrows():
        area_id = int(ranked_row["area_id"])
        if area_id not in positions:
            raise ValueError(f"추천 결과의 area_id={area_id}가 원본 데이터에 없습니다.")
        position = positions[area_id]
        area_row = frame.iloc[position]

        if algorithm == "weighted":
            explanation = explain_weighted_area(area_row, weights, top_n)
        elif algorithm == "cosine":
            explanation = explain_cosine_area(frame, position, weights, top_n)
        elif algorithm == "knn_euclidean":
            explanation = explain_knn_area(frame, position, weights, top_n)
        else:
            raise ValueError(f"지원하지 않는 알고리즘입니다: {algorithm}")

        explanations.append(explanation)

    explained["explanation"] = explanations
    explained["reason_summary"] = [item["summary"] for item in explanations]
    return explained
