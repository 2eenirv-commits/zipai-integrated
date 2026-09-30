"""ZipAI Lifestyle ML 1차 - 데이터 로더.

MySQL의 lifestyle_area + lifestyle_score 최신 데이터를 읽거나,
검증/학습을 위해 같은 컬럼 구조의 CSV를 읽는다.
"""
from __future__ import annotations

import os
from pathlib import Path
from typing import Final

import pandas as pd

FEATURE_COLUMNS: Final[list[str]] = [
    "transport_score",
    "convenience_score",
    "medical_score",
    "education_score",
    "park_score",
    "safety_score",
    "commercial_score",
    "quiet_score",
    "cost_score",
]

META_COLUMNS: Final[list[str]] = ["area_id", "area_code", "sido", "sigungu"]


def validate_area_frame(frame: pd.DataFrame) -> pd.DataFrame:
    """추천에 필요한 컬럼/값을 검증하고 정렬된 복사본을 반환한다."""
    required = META_COLUMNS + FEATURE_COLUMNS
    missing = [column for column in required if column not in frame.columns]
    if missing:
        raise ValueError(f"필수 컬럼이 없습니다: {', '.join(missing)}")

    result = frame[required].copy()
    for column in FEATURE_COLUMNS:
        result[column] = pd.to_numeric(result[column], errors="coerce")

    null_rows = result[FEATURE_COLUMNS].isna().any(axis=1)
    if null_rows.any():
        names = result.loc[null_rows, "sigungu"].astype(str).tolist()
        raise ValueError(f"9개 Lifestyle 점수가 모두 필요합니다. 누락 지역: {', '.join(names)}")

    if ((result[FEATURE_COLUMNS] < 0) | (result[FEATURE_COLUMNS] > 100)).any().any():
        raise ValueError("Lifestyle 점수는 0~100 범위여야 합니다.")

    duplicated = result["area_id"].duplicated(keep=False)
    if duplicated.any():
        ids = result.loc[duplicated, "area_id"].astype(str).tolist()
        raise ValueError(f"area_id 중복이 있습니다: {', '.join(ids)}")

    return result.sort_values(["sido", "sigungu"]).reset_index(drop=True)


def load_from_csv(csv_path: str | Path) -> pd.DataFrame:
    """UTF-8 CSV에서 Lifestyle 지역 점수를 읽는다."""
    frame = pd.read_csv(csv_path, encoding="utf-8-sig")
    return validate_area_frame(frame)


def load_from_mysql(sido: str = "경기도") -> pd.DataFrame:
    """환경변수로 MySQL에 접속해 각 지역의 최신 lifestyle_score를 읽는다."""
    try:
        import mysql.connector
    except ImportError as exc:
        raise RuntimeError(
            "mysql-connector-python이 필요합니다. "
            "가상환경에서 pip install mysql-connector-python 을 실행하세요."
        ) from exc

    config = {
        "host": os.getenv("ZIPAI_DB_HOST", "localhost"),
        "port": int(os.getenv("ZIPAI_DB_PORT", "3306")),
        "database": os.getenv("ZIPAI_DB_NAME", "zipai"),
        "user": os.getenv("ZIPAI_DB_USER", "zipai"),
        "password": os.getenv("ZIPAI_DB_PASSWORD", ""),
        "charset": "utf8mb4",
        "use_unicode": True,
    }

    if not config["password"]:
        raise RuntimeError("ZIPAI_DB_PASSWORD 환경변수가 비어 있습니다.")

    sql = """
        SELECT
            a.area_id,
            a.area_code,
            a.sido,
            a.sigungu,
            s.transport_score,
            s.convenience_score,
            s.medical_score,
            s.education_score,
            s.park_score,
            s.safety_score,
            s.commercial_score,
            s.quiet_score,
            s.cost_score
        FROM lifestyle_area a
        JOIN lifestyle_score s
          ON s.score_id = (
              SELECT s2.score_id
              FROM lifestyle_score s2
              WHERE s2.area_id = a.area_id
              ORDER BY s2.source_date DESC, s2.score_id DESC
              LIMIT 1
          )
        WHERE a.active = TRUE
          AND a.sido = %s
        ORDER BY a.sigungu
    """

    connection = mysql.connector.connect(**config)
    try:
        cursor = connection.cursor(dictionary=True)
        try:
            cursor.execute(sql, (sido,))
            rows = cursor.fetchall()
        finally:
            cursor.close()
    finally:
        connection.close()

    if not rows:
        raise ValueError(f"{sido} Lifestyle 점수를 찾지 못했습니다.")

    return validate_area_frame(pd.DataFrame(rows))
