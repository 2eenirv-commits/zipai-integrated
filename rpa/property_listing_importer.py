"""Import authorized or study listing data into ZipAI.

CSV/JSON rows are normalized, optionally geocoded through the running ZipAI
backend, saved as a processed JSON checkpoint, and uploaded in batches.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import sys
import time
import uuid
from pathlib import Path
from typing import Any

import requests


def configure_utf8_output() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            reconfigure(encoding="utf-8", errors="replace", line_buffering=True)


configure_utf8_output()
BASE_DIR = Path(__file__).resolve().parent
OUT = BASE_DIR / "data" / "processed" / "property_listing_import_processed.json"


def load_project_env() -> None:
    env_path = BASE_DIR.parent / ".env"
    if not env_path.exists():
        return
    try:
        from dotenv import load_dotenv
        load_dotenv(env_path, override=False)
        return
    except Exception:
        pass
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        name, value = name.strip(), value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        if name and name not in os.environ:
            os.environ[name] = value


load_project_env()
BACKEND = os.getenv("ZIPAI_BASE_URL", "http://localhost:8080").strip().rstrip("/")
TOKEN = os.getenv("PROPERTY_IMPORT_TOKEN", "").strip()
TIMEOUT = max(5, min(int(os.getenv("PROPERTY_CRAWL_TIMEOUT_SECONDS", "20")), 120))
GEOCODE_LIMIT = max(0, min(int(os.getenv("PROPERTY_LISTING_GEOCODE_LIMIT", "500")), 2000))
GEOCODE_DELAY = max(0.0, float(os.getenv("PROPERTY_LISTING_GEOCODE_DELAY_SECONDS", "0.25")))
SESSION = requests.Session()
SESSION.headers.update({"User-Agent": "ZipAI-authorized-listing-importer/1.0"})


def clean(value: Any) -> str:
    return "" if value is None else str(value).strip()


def number(value: Any) -> float | None:
    text = clean(value).replace(",", "")
    return None if not text else float(text)


def integer(value: Any, default: int = 0) -> int:
    parsed = number(value)
    return default if parsed is None else int(parsed)


def boolean(value: Any) -> bool:
    return clean(value).lower() in {"1", "true", "yes", "y", "on", "예", "가능"}


def deal_type(value: Any) -> str:
    normalized = clean(value).upper().replace(" ", "_")
    aliases = {
        "매매": "SALE", "SALE": "SALE",
        "전세": "JEONSE", "JEONSE": "JEONSE",
        "월세": "MONTHLY", "MONTHLY": "MONTHLY", "MONTHLY_RENT": "MONTHLY", "RENT": "MONTHLY",
    }
    if normalized not in aliases:
        raise ValueError(f"지원하지 않는 거래 유형: {value}")
    return aliases[normalized]


def load_rows(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"입력 파일이 없습니다: {path}")
    if path.suffix.lower() == ".csv":
        with path.open("r", encoding="utf-8-sig", newline="") as stream:
            return [dict(row) for row in csv.DictReader(stream)]
    if path.suffix.lower() == ".json":
        payload = json.loads(path.read_text(encoding="utf-8"))
        rows = payload if isinstance(payload, list) else payload.get("items")
        if not isinstance(rows, list):
            raise ValueError("JSON은 배열 또는 items 배열이어야 합니다.")
        return [dict(row) for row in rows if isinstance(row, dict)]
    raise ValueError("입력 파일은 CSV 또는 JSON만 지원합니다.")


def scoped_source_id(source_name: str, row: dict[str, Any]) -> str:
    raw_id = clean(row.get("sourceId") or row.get("id"))
    if not raw_id:
        raw_id = "|".join(clean(row.get(key)) for key in ("title", "address", "dealType", "salePrice", "deposit", "monthly"))
    return hashlib.sha1(f"{source_name}|{raw_id}".encode("utf-8")).hexdigest()


def normalize_row(source_name: str, row: dict[str, Any]) -> dict[str, Any]:
    deal = deal_type(row.get("dealType") or row.get("deal"))
    title, address = clean(row.get("title")), clean(row.get("address"))
    if not title or not address:
        raise ValueError("title과 address는 필수입니다.")
    sale_price = integer(row.get("salePrice")) if deal == "SALE" else None
    deposit = integer(row.get("deposit")) if deal != "SALE" else 0
    monthly = integer(row.get("monthly")) if deal == "MONTHLY" else 0
    maintenance = integer(row.get("maintenance"))
    if deal == "SALE" and (sale_price is None or sale_price <= 0):
        raise ValueError("매매가는 0보다 커야 합니다.")
    if min(deposit, monthly, maintenance) < 0:
        raise ValueError("가격 정보는 0 이상이어야 합니다.")
    if maintenance > 1000:
        raise ValueError("관리비는 만원 단위로 1000 이하이어야 합니다.")
    return {
        "sourceId": scoped_source_id(source_name, row),
        "sourceUrl": clean(row.get("sourceUrl")),
        "dealType": deal,
        "buildingType": clean(row.get("buildingType")) or "기타",
        "title": title,
        "address": address,
        "sido": clean(row.get("sido")),
        "sigungu": clean(row.get("sigungu")),
        "neighborhood": clean(row.get("neighborhood")),
        "lat": number(row.get("lat")),
        "lng": number(row.get("lng")),
        "salePrice": sale_price,
        "deposit": deposit,
        "monthly": monthly,
        "maintenance": maintenance,
        "area": number(row.get("area")),
        "floor": clean(row.get("floor")),
        "parking": boolean(row.get("parking")),
        "elevator": boolean(row.get("elevator")),
        "pet": boolean(row.get("pet")),
        "description": clean(row.get("description")),
        "contact": clean(row.get("contact")),
        "imageUrl": clean(row.get("imageUrl")),
    }


def geocode(row: dict[str, Any]) -> bool:
    if row.get("lat") is not None and row.get("lng") is not None:
        return True
    response = SESSION.get(BACKEND + "/api/safety/geocode", params={"query": row["address"]}, timeout=TIMEOUT)
    if not response.ok:
        return False
    payload = response.json()
    candidates = payload.get("candidates") if isinstance(payload.get("candidates"), list) else []
    location = payload.get("location") or (candidates[0] if candidates else {})
    lat = location.get("latitude", location.get("lat"))
    lng = location.get("longitude", location.get("lng"))
    if lat is None or lng is None:
        return False
    row["lat"], row["lng"] = float(lat), float(lng)
    row["address"] = clean(location.get("address") or location.get("roadAddress")) or row["address"]
    return True


def validate_backend() -> None:
    if not TOKEN:
        raise RuntimeError("필수 환경변수 누락: PROPERTY_IMPORT_TOKEN")
    response = SESSION.post(
        BACKEND + "/api/properties/import-check",
        headers={"X-Property-Import-Token": TOKEN}, timeout=TIMEOUT,
    )
    print(f"backend={BACKEND}")
    print(f"import_token_status={response.status_code}")
    response.raise_for_status()


def save(source_name: str, rows: list[dict[str, Any]], errors: list[str]) -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUT.with_suffix(OUT.suffix + ".tmp")
    temporary.write_text(json.dumps({
        "sourceName": source_name, "processedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "count": len(rows), "errors": errors, "items": rows,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(OUT)


def upload(source_name: str, rows: list[dict[str, Any]], batch_size: int, study_data: bool, complete_snapshot: bool) -> dict[str, int]:
    totals = {"collected": 0, "inserted": 0, "updated": 0, "errors": 0}
    import_run_id = uuid.uuid4().hex
    batches = (len(rows) + batch_size - 1) // batch_size
    for index in range(batches):
        batch = rows[index * batch_size:(index + 1) * batch_size]
        response = SESSION.post(
            BACKEND + "/api/properties/crawl-import",
            headers={"X-Property-Import-Token": TOKEN, "Content-Type": "application/json"},
            json={"sourceName": source_name, "importRunId": import_run_id, "studyData": study_data, "items": batch},
            timeout=max(TIMEOUT, 120),
        )
        print(f"upload_batch={index + 1}/{batches} rows={len(batch)} status={response.status_code}")
        if response.text:
            print(response.text)
        response.raise_for_status()
        result = response.json()
        for key in totals:
            totals[key] += int(result.get(key, 0) or 0)
    totals["batches"] = batches
    if complete_snapshot:
        response = SESSION.post(
            BACKEND + "/api/properties/crawl-import/finalize",
            headers={"X-Property-Import-Token": TOKEN, "Content-Type": "application/json"},
            json={"sourceName": source_name, "importRunId": import_run_id}, timeout=TIMEOUT,
        )
        print(f"finalize_status={response.status_code}")
        if response.text:
            print(response.text)
        response.raise_for_status()
        totals["closed"] = int(response.json().get("closed", 0) or 0)
    return totals


def delete_study_listings() -> int:
    response = SESSION.delete(
        BACKEND + "/api/properties/study-listings",
        headers={"X-Property-Import-Token": TOKEN}, timeout=TIMEOUT,
    )
    print(f"delete_study_status={response.status_code}")
    if response.text:
        print(response.text)
    response.raise_for_status()
    return int(response.json().get("deleted", 0) or 0)


def main() -> int:
    parser = argparse.ArgumentParser(description="ZipAI 허가·학습용 현재 매물 Import")
    parser.add_argument("--file", type=Path, help="CSV 또는 JSON 입력 파일")
    parser.add_argument("--source-name", default="ZIPAI_STUDY_LISTING", help="공급처 식별 이름")
    parser.add_argument("--upload", action="store_true", help="정규화 후 ZipAI DB에 업로드")
    parser.add_argument("--check", action="store_true", help="백엔드와 Import Token 확인")
    parser.add_argument("--study-data", action="store_true", help="업로드 데이터를 학습용으로 표시")
    parser.add_argument("--complete-snapshot", action="store_true", help="이번 파일에 없는 동일 공급처의 기존 매물을 거래 종료 처리")
    parser.add_argument("--delete-study", action="store_true", help="DB의 학습용 매물을 모두 삭제")
    parser.add_argument("--no-geocode", action="store_true", help="좌표 없는 주소의 좌표 변환 생략")
    parser.add_argument("--batch-size", type=int, default=500, help="업로드 배치 크기(1~2000)")
    args = parser.parse_args()
    if args.check:
        validate_backend()
        return 0
    if args.delete_study:
        validate_backend()
        deleted = delete_study_listings()
        print(f"study_deleted={deleted}")
        return 0
    if args.file is None:
        raise RuntimeError("--file 경로를 입력해 주세요.")
    source_name = clean(args.source_name)
    if not source_name:
        raise RuntimeError("--source-name을 입력해 주세요.")
    batch_size = max(1, min(args.batch_size, 2000))
    raw_rows = load_rows(args.file)
    rows: list[dict[str, Any]] = []
    errors: list[str] = []
    for index, raw in enumerate(raw_rows, 2):
        try:
            rows.append(normalize_row(source_name, raw))
        except Exception as exc:
            errors.append(f"row={index}: {exc}")
    geocode_requested = geocode_success = 0
    if not args.no_geocode:
        for row in rows:
            if row.get("lat") is not None and row.get("lng") is not None:
                continue
            if geocode_requested >= GEOCODE_LIMIT:
                break
            geocode_requested += 1
            try:
                if geocode(row):
                    geocode_success += 1
            except requests.RequestException as exc:
                errors.append(f"geocode={row['address']}: {exc}")
            if GEOCODE_DELAY:
                time.sleep(GEOCODE_DELAY)
    save(source_name, rows, errors)
    coordinates = sum(1 for row in rows if row.get("lat") is not None and row.get("lng") is not None)
    print(f"input={len(raw_rows)} accepted={len(rows)} rejected={len(raw_rows) - len(rows)}")
    print(f"geocode_requested={geocode_requested} success={geocode_success} coordinates={coordinates} missing={len(rows) - coordinates}")
    print(f"saved={OUT}")
    if errors:
        for error in errors[:20]:
            print(error, file=sys.stderr)
    if args.upload:
        validate_backend()
        result = upload(source_name, rows, batch_size, args.study_data, args.complete_snapshot)
        print("import_result=" + json.dumps(result, ensure_ascii=False))
        if result["errors"]:
            return 2
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("cancelled", file=sys.stderr)
        raise SystemExit(130)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
