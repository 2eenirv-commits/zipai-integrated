"""기준 모델: 기존 설명 가능한 Weighted Score 추천."""
from __future__ import annotations

import numpy as np
import pandas as pd

from data_loader import FEATURE_COLUMNS
from feature_scaler import validate_weights


def recommend_weighted(
    frame: pd.DataFrame,
    weights: list[float] | np.ndarray,
    top_k: int = 3,
) -> pd.DataFrame:
    weight_vector = validate_weights(weights)
    scores = frame[FEATURE_COLUMNS].to_numpy(dtype=float)
    weighted_score = (scores @ weight_vector) / weight_vector.sum()

    result = frame[["area_id", "area_code", "sido", "sigungu"]].copy()
    result["score"] = weighted_score
    result["algorithm"] = "weighted"
    return result.sort_values("score", ascending=False).head(top_k).reset_index(drop=True)
