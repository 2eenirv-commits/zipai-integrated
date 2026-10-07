import csv
import os
from datetime import datetime
from pathlib import Path

import mysql.connector

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_CSV = BASE_DIR / "data" / "lifestyle" / "lifestyle_official_scores.csv"
SCORE_FIELDS = [
    "transport_score", "convenience_score", "medical_score", "education_score",
    "park_score", "safety_score", "commercial_score", "quiet_score", "cost_score"
]


def env(name, default=None):
    value = os.getenv(name)
    return value if value not in (None, "") else default


def as_score(value):
    if value is None or str(value).strip() == "":
        return None
    number = int(float(str(value).strip()))
    if not 0 <= number <= 100:
        raise ValueError(f"점수는 0~100이어야 합니다: {value}")
    return number


def connect_db():
    return mysql.connector.connect(
        host=env("ZIPAI_DB_HOST", "localhost"),
        port=int(env("ZIPAI_DB_PORT", "3306")),
        database=env("ZIPAI_DB_NAME", "zipai"),
        user=env("ZIPAI_DB_USER", "zipai"),
        password=env("ZIPAI_DB_PASSWORD", ""),
        charset="utf8mb4",
        autocommit=False,
    )


def import_csv(path):
    if not path.exists():
        raise FileNotFoundError(f"CSV 파일이 없습니다: {path}")

    with path.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))

    required = {"area_code", "sido", "sigungu", "source_name", "source_date"}
    if not rows:
        raise ValueError("CSV에 데이터 행이 없습니다.")
    missing = required - set(rows[0].keys())
    if missing:
        raise ValueError("필수 컬럼 누락: " + ", ".join(sorted(missing)))

    conn = connect_db()
    cur = conn.cursor()
    imported = 0
    try:
        for row in rows:
            area_code = row["area_code"].strip()
            if not area_code:
                raise ValueError("area_code는 필수입니다.")
            source_name = row["source_name"].strip()
            source_date = row["source_date"].strip()
            if not source_name or not source_date:
                raise ValueError(f"source_name/source_date는 필수입니다: {area_code}")
            datetime.strptime(source_date, "%Y-%m-%d")

            cur.execute(
                """
                INSERT INTO lifestyle_area
                    (area_code, sido, sigungu, dong, latitude, longitude, active)
                VALUES (%s, %s, %s, %s, %s, %s, TRUE)
                ON DUPLICATE KEY UPDATE
                    sido=VALUES(sido), sigungu=VALUES(sigungu), dong=VALUES(dong),
                    latitude=COALESCE(VALUES(latitude), latitude),
                    longitude=COALESCE(VALUES(longitude), longitude), active=TRUE
                """,
                (
                    area_code, row["sido"].strip(), row["sigungu"].strip(),
                    (row.get("dong") or "").strip() or None,
                    (row.get("latitude") or "").strip() or None,
                    (row.get("longitude") or "").strip() or None,
                ),
            )
            cur.execute("SELECT area_id FROM lifestyle_area WHERE area_code=%s", (area_code,))
            area_id = cur.fetchone()[0]

            scores = [as_score(row.get(name)) for name in SCORE_FIELDS]
            if all(value is None for value in scores):
                print(f"[SKIP] 점수 없음: {area_code} {row['sigungu']}")
                continue

            cur.execute(
                """
                INSERT INTO lifestyle_score (
                    area_id, transport_score, convenience_score, medical_score, education_score,
                    park_score, safety_score, commercial_score, quiet_score, cost_score,
                    source_name, source_date
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                ON DUPLICATE KEY UPDATE
                    transport_score=VALUES(transport_score),
                    convenience_score=VALUES(convenience_score),
                    medical_score=VALUES(medical_score),
                    education_score=VALUES(education_score),
                    park_score=VALUES(park_score),
                    safety_score=VALUES(safety_score),
                    commercial_score=VALUES(commercial_score),
                    quiet_score=VALUES(quiet_score),
                    cost_score=VALUES(cost_score),
                    source_name=VALUES(source_name),
                    source_date=VALUES(source_date)
                """,
                (area_id, *scores, source_name, source_date),
            )
            imported += 1

        conn.commit()
        print(f"[OK] Lifestyle 공식/정규화 점수 {imported}건 반영")
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    csv_path = Path(os.getenv("LIFESTYLE_SCORE_CSV", DEFAULT_CSV))
    import_csv(csv_path)
