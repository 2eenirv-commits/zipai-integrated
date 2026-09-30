"""ZipAI Lifestyle AI/ML 2차 - Feature Scaling과 사용자 중요도 처리."""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler

from data_loader import FEATURE_COLUMNS


def validate_weights(weights: list[float] | np.ndarray) -> np.ndarray:
    values = np.asarray(weights, dtype=float)
    if values.shape != (len(FEATURE_COLUMNS),):
        raise ValueError(f"가중치는 {len(FEATURE_COLUMNS)}개가 필요합니다.")
    if np.any(values < 0) or np.any(values > 5):
        raise ValueError("가중치는 0~5 범위여야 합니다.")
    if np.all(values == 0):
        raise ValueError("최소 한 개 이상의 가중치는 1 이상이어야 합니다.")
    return values


def minmax_scale(frame: pd.DataFrame) -> tuple[np.ndarray, MinMaxScaler]:
    """지역 9개 점수를 Feature별 0~1 범위로 Scaling한다."""
    scaler = MinMaxScaler()
    matrix = scaler.fit_transform(frame[FEATURE_COLUMNS].to_numpy(dtype=float))
    return matrix, scaler


def normalized_weights(weights: list[float] | np.ndarray) -> np.ndarray:
    """0~5 중요도를 0~1 중요도 벡터로 변환한다."""
    values = validate_weights(weights)
    return values / 5.0


def weighted_feature_space(
    scaled_area_matrix: np.ndarray,
    weights: list[float] | np.ndarray,
) -> tuple[np.ndarray, np.ndarray]:
    """사용자 '중요도'를 선형 가중치로 반영한 ML Feature 공간을 만든다.

    핵심은 sqrt(weight)를 좌표축에 곱하는 것이다.

    weighted_area_i = scaled_score_i * sqrt(weight_i)
    user_target_i   = 1.0 * sqrt(weight_i)

    이렇게 하면 cosine의 내적과 Euclidean 거리 제곱에 weight_i가 한 번만
    반영된다. 즉 1차의 importance 자체를 양쪽 벡터에 곱해 weight가 제곱되는
    현상을 피한다.

    weight=0인 Feature는 두 벡터에서 0이 되어 추천 계산에서 제외된다.
    user_target의 1.0은 '중요한 항목일수록 높은 지역 점수를 선호'한다는
    현재 ZipAI의 추천 정책을 의미한다.
    """
    importance = normalized_weights(weights)
    axis_weight = np.sqrt(importance)
    weighted_areas = scaled_area_matrix * axis_weight
    user_target = axis_weight.copy()
    return weighted_areas, user_target
