"""Cosine Similarity 기반 Lifestyle 추천."""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.metrics.pairwise import cosine_similarity

from feature_scaler import minmax_scale, weighted_feature_space


def recommend_cosine(
    frame: pd.DataFrame,
    weights: list[float] | np.ndarray,
    top_k: int = 3,
) -> pd.DataFrame:
    scaled, _ = minmax_scale(frame)
    weighted_areas, user_target = weighted_feature_space(scaled, weights)

    similarities = cosine_similarity(weighted_areas, user_target.reshape(1, -1)).ravel()

    result = frame[["area_id", "area_code", "sido", "sigungu"]].copy()
    result["score"] = similarities * 100.0
    result["algorithm"] = "cosine"
    return result.sort_values("score", ascending=False).head(top_k).reset_index(drop=True)
