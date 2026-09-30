"""Weighted Euclidean Nearest Neighbors 기반 Lifestyle 추천."""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.neighbors import NearestNeighbors

from feature_scaler import minmax_scale, weighted_feature_space


def recommend_knn(
    frame: pd.DataFrame,
    weights: list[float] | np.ndarray,
    top_k: int = 3,
) -> pd.DataFrame:
    """사용자 중요도가 적용된 Feature 공간에서 Euclidean 최근접 지역을 찾는다.

    score는 거리 자체를 그대로 노출하지 않고 0~100의 해석 가능한 유사 점수로
    변환한다. score = 100 / (1 + distance)
    """
    if top_k < 1:
        raise ValueError("top_k는 1 이상이어야 합니다.")
    top_k = min(top_k, len(frame))

    scaled, _ = minmax_scale(frame)
    weighted_areas, user_target = weighted_feature_space(scaled, weights)

    model = NearestNeighbors(n_neighbors=top_k, metric="euclidean", algorithm="brute")
    model.fit(weighted_areas)
    distances, indices = model.kneighbors(user_target.reshape(1, -1))

    selected = frame.iloc[indices[0]][["area_id", "area_code", "sido", "sigungu"]].copy()
    selected["score"] = 100.0 / (1.0 + distances[0])
    selected["distance"] = distances[0]
    selected["algorithm"] = "knn_euclidean"
    return selected.reset_index(drop=True)
