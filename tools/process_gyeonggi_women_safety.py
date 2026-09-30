import argparse
import csv
import hashlib
import json
import os
import re
import time
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path


OUTPUT_COLUMNS = [
    "source_id", "sido_name", "sigungu_name", "facility_type", "facility_name",
    "brand_name", "address", "latitude", "longitude", "source_name",
    "source_updated_at", "data_year", "coverage_note", "coordinate_validation",
]
GYEONGGI_BOUNDS = (36.8, 38.3, 126.3, 127.9)


def _date(value):
    text = re.sub(r"\D", "", value or "")
    return datetime.strptime(text, "%Y%m%d").date().isoformat() if len(text) == 8 else ""


def _source_id(prefix, region, name, address):
    digest = hashlib.sha256(f"{region}|{name}|{address}".encode("utf-8")).hexdigest()[:24]
    return f"{prefix}-{digest}"


def _official_coordinate(row, sigungu, address):
    latitude = (row.get("위도") or "").strip()
    longitude = (row.get("경도") or "").strip()
    if not latitude or not longitude:
        return "", "", "NEEDS_GEOCODING"
    try:
        lat, lng = float(latitude), float(longitude)
    except ValueError:
        return "", "", "INVALID_COORDINATE"
    min_lat, max_lat, min_lng, max_lng = GYEONGGI_BOUNDS
    if not (min_lat <= lat <= max_lat and min_lng <= lng <= max_lng):
        return "", "", "INVALID_COORDINATE"
    if "경기도" not in address or sigungu not in address:
        return "", "", "ADMIN_MISMATCH"
    return latitude, longitude, "VERIFIED"


def _geocode(address, sigungu, api_key):
    for category in ("ROAD", "PARCEL"):
        query = urllib.parse.urlencode({
            "service": "search", "request": "search", "version": "2.0", "crs": "EPSG:4326",
            "size": 1, "page": 1, "query": address, "type": "ADDRESS", "category": category,
            "format": "json", "errorformat": "json", "key": api_key,
        })
        with urllib.request.urlopen("https://api.vworld.kr/req/search?" + query, timeout=15) as response:
            body = json.load(response)
        if body.get("response", {}).get("status") != "OK":
            continue
        item = body["response"]["result"]["items"][0]
        point = item["point"]
        reverse_query = urllib.parse.urlencode({
            "service": "address", "request": "getAddress", "version": "2.0", "crs": "EPSG:4326",
            "point": f'{point["x"]},{point["y"]}', "format": "json", "type": "both",
            "zipcode": "false", "simple": "false", "key": api_key,
        })
        with urllib.request.urlopen("https://api.vworld.kr/req/address?" + reverse_query, timeout=15) as response:
            reverse = json.load(response)
        result = (reverse.get("response", {}).get("result") or [{}])[0]
        structure = result.get("structure") or {}
        if structure.get("level1") == "경기도" and structure.get("level2") == sigungu:
            return str(point["y"]), str(point["x"])
    return None


def process_guard_houses(path):
    rows = []
    with path.open(encoding="cp949", newline="") as handle:
        for raw in csv.DictReader(handle):
            sigungu = raw["시군구명"].strip()
            name = raw["점포명"].strip()
            address = (raw["소재지도로명주소"].strip() or raw["소재지지번주소"].strip())
            latitude, longitude, validation = _official_coordinate(raw, sigungu, address)
            if raw["운영여부"].strip().upper() != "Y":
                latitude, longitude, validation = "", "", "INACTIVE"
            updated_at = _date(raw["데이터기준일자"])
            rows.append({
                "source_id": _source_id("gg-safe-house", sigungu, name, address),
                "sido_name": "경기도", "sigungu_name": sigungu, "facility_type": "SAFE_HOUSE",
                "facility_name": name, "brand_name": "", "address": address,
                "latitude": latitude, "longitude": longitude,
                "source_name": "경기데이터드림 여성안심지킴이집(제공표준)",
                "source_updated_at": updated_at, "data_year": raw["지정연도"].strip(),
                "coverage_note": "경기도 공식 여성안심지킴이집 중 운영 여부가 Y인 시설입니다.",
                "coordinate_validation": validation,
            })
    return rows


def process_parcel_lockers(path):
    rows = []
    with path.open(encoding="cp949", newline="") as handle:
        for raw in csv.DictReader(handle):
            sigungu = raw["시군명"].strip()
            name = raw["택배함 명칭"].strip()
            address = (raw["소재지도로명주소"].strip() or raw["소재지지번주소"].strip())
            latitude, longitude, validation = _official_coordinate(raw, sigungu, address)
            updated_at = _date(raw["데이터수집일자"])
            installation = re.sub(r"\D", "", raw["설치일자"] or "")
            rows.append({
                "source_id": _source_id("gg-parcel", sigungu, name, address),
                "sido_name": "경기도", "sigungu_name": sigungu, "facility_type": "SAFE_PARCEL_LOCKER",
                "facility_name": name, "brand_name": "", "address": address,
                "latitude": latitude, "longitude": longitude,
                "source_name": "경기데이터드림 안심택배함 이용 현황",
                "source_updated_at": updated_at, "data_year": installation[:4] if len(installation) >= 4 else "",
                "coverage_note": "경기도 공식 여성 안심 무인택배함 설치 위치입니다.",
                "coordinate_validation": validation,
            })
    return rows


def geocode_missing(rows, delay_seconds=0.2):
    api_key = os.getenv("VWORLD_API_KEY", "").strip()
    if not api_key:
        raise ValueError("--geocode 실행에는 VWORLD_API_KEY가 필요합니다.")
    success = mismatch = 0
    for row in rows:
        if row["coordinate_validation"] != "NEEDS_GEOCODING":
            continue
        try:
            result = _geocode(row["address"], row["sigungu_name"], api_key)
        except Exception:
            result = None
        if result:
            row["latitude"], row["longitude"] = result
            row["coordinate_validation"] = "VERIFIED"
            success += 1
        else:
            row["coordinate_validation"] = "GEOCODING_FAILED"
            mismatch += 1
        time.sleep(max(0, delay_seconds))
    return success, mismatch


def validate(rows):
    keys = [(row["source_name"], row["source_id"]) for row in rows]
    required = [column for column in OUTPUT_COLUMNS if column not in {"brand_name", "latitude", "longitude", "data_year"}]
    return {
        "processedRows": len(rows),
        "verifiedCoordinates": sum(row["coordinate_validation"] == "VERIFIED" for row in rows),
        "needsGeocoding": sum(row["coordinate_validation"] == "NEEDS_GEOCODING" for row in rows),
        "inactive": sum(row["coordinate_validation"] == "INACTIVE" for row in rows),
        "supportedRegions": len({row["sigungu_name"] for row in rows if row["coordinate_validation"] == "VERIFIED"}),
        "duplicates": len(keys) - len(set(keys)),
        "requiredNullRows": sum(any(not row[column] for column in required) for row in rows),
    }


def write_csv(rows, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--guard", type=Path, required=True)
    parser.add_argument("--parcel", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--geocode", action="store_true")
    parser.add_argument("--delay-seconds", type=float, default=0.2)
    args = parser.parse_args()
    rows = process_guard_houses(args.guard) + process_parcel_lockers(args.parcel)
    if args.geocode:
        geocode_missing(rows, args.delay_seconds)
    summary = validate(rows)
    if summary["duplicates"] or summary["requiredNullRows"]:
        raise ValueError(f"processed data validation failed: {summary}")
    write_csv(rows, args.output)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
