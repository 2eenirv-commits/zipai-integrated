#!/usr/bin/env python3
"""Validate and normalize the two official Seoul safety datasets used by ZipAI."""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


CRIME_HEADER = ["구분", "죄종", "발생검거", "건수"]
CRIME_TYPES = ("살인", "강도", "강간·추행", "절도", "폭력")
# District grouping derived from the official police-station jurisdiction table
# (경찰청과 그 소속기관 직제 시행규칙 별표 2). A district may deliberately map
# to multiple stations; no station is selected merely by name prefix.
STATION_DISTRICTS = {
    "중부": "중구", "종로": "종로구", "남대문": "중구", "서대문": "서대문구",
    "혜화": "종로구", "용산": "용산구", "성북": "성북구", "동대문": "동대문구",
    "마포": "마포구", "영등포": "영등포구", "성동": "성동구", "동작": "동작구",
    "광진": "광진구", "서부": "은평구", "강북": "강북구", "금천": "금천구",
    "중랑": "중랑구", "강남": "강남구", "관악": "관악구", "강서": "강서구",
    "강동": "강동구", "종암": "성북구", "구로": "구로구", "서초": "서초구",
    "양천": "양천구", "송파": "송파구", "노원": "노원구", "방배": "서초구",
    "은평": "은평구", "도봉": "도봉구", "수서": "강남구",
}
CRIME_SOURCE = "경찰청 서울특별시경찰청_경찰서별 5대범죄 발생 검거 현황"
WOMEN_SOURCE = "서울특별시_여성안심지킴이집 정보"
WOMEN_SOURCE_DATE = "2019-11-04"


def _open_csv(path: Path):
    for encoding in ("utf-8-sig", "cp949"):
        try:
            handle = path.open(encoding=encoding, newline="")
            handle.read(1024)
            handle.seek(0)
            return handle
        except UnicodeDecodeError:
            handle.close()
    raise ValueError(f"지원하지 않는 CSV 인코딩입니다: {path}")


def process_crime(source: Path, output: Path) -> dict[str, int]:
    with _open_csv(source) as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != CRIME_HEADER:
            raise ValueError(f"5대범죄 원본 헤더 불일치: {reader.fieldnames}")
        raw_rows = list(reader)

    values: dict[tuple[str, str], dict[str, int]] = defaultdict(dict)
    raw_duplicates = 0
    for row in raw_rows:
        if any(not (row.get(name) or "").strip() for name in CRIME_HEADER):
            raise ValueError(f"5대범죄 필수값 누락: {row}")
        station = row["구분"].strip()
        if station not in STATION_DISTRICTS:
            raise ValueError(f"미등록 경찰서: {station}")
        crime_type = row["죄종"].strip().replace("강간,추행", "강간·추행")
        if crime_type == "강간":
            crime_type = "강간·추행"
        if crime_type not in CRIME_TYPES:
            raise ValueError(f"지원하지 않는 죄종: {crime_type}")
        mode = row["발생검거"].strip()
        if mode not in ("발생", "검거"):
            raise ValueError(f"지원하지 않는 발생검거 값: {mode}")
        key = (station, crime_type)
        if mode in values[key]:
            raw_duplicates += 1
        count = int(row["건수"].replace(",", ""))
        if count < 0:
            raise ValueError(f"음수 건수: {row}")
        values[key][mode] = count

    expected = {(station, crime_type) for station in STATION_DISTRICTS for crime_type in CRIME_TYPES}
    if set(values) != expected or any(set(modes) != {"발생", "검거"} for modes in values.values()):
        raise ValueError("31개 경찰서 × 5개 죄종 × 발생/검거 구조가 완전하지 않습니다.")

    output.parent.mkdir(parents=True, exist_ok=True)
    fields = ["year", "sido_name", "sigungu_name", "police_station_name", "crime_type",
              "occurrence_count", "arrest_count", "source_name", "source_updated_at"]
    with output.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for station in STATION_DISTRICTS:
            for crime_type in CRIME_TYPES:
                writer.writerow({
                    "year": 2024,
                    "sido_name": "서울특별시",
                    "sigungu_name": STATION_DISTRICTS[station],
                    "police_station_name": f"서울{station}경찰서",
                    "crime_type": crime_type,
                    "occurrence_count": values[(station, crime_type)]["발생"],
                    "arrest_count": values[(station, crime_type)]["검거"],
                    "source_name": CRIME_SOURCE,
                    "source_updated_at": "2024-12-31",
                })
    return {
        "rawRows": len(raw_rows), "processedRows": len(values),
        "stations": len(STATION_DISTRICTS), "crimeTypes": len(CRIME_TYPES),
        "duplicates": raw_duplicates, "nullRows": 0,
    }


def _column_number(reference: str) -> int:
    letters = "".join(char for char in reference if char.isalpha())
    value = 0
    for char in letters:
        value = value * 26 + ord(char.upper()) - 64
    return value


def _xlsx_rows(path: Path):
    main_ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    rel_ns = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    pkg_ns = "http://schemas.openxmlformats.org/package/2006/relationships"
    with zipfile.ZipFile(path) as book:
        shared_root = ET.fromstring(book.read("xl/sharedStrings.xml"))
        shared = ["".join(node.itertext()) for node in shared_root.findall(f"{{{main_ns}}}si")]
        workbook = ET.fromstring(book.read("xl/workbook.xml"))
        relationships = ET.fromstring(book.read("xl/_rels/workbook.xml.rels"))
        targets = {node.attrib["Id"]: node.attrib["Target"] for node in relationships.findall(f"{{{pkg_ns}}}Relationship")}
        for sheet_number, sheet in enumerate(workbook.findall(f".//{{{main_ns}}}sheet"), start=1):
            target = targets[sheet.attrib[f"{{{rel_ns}}}id"]].lstrip("/")
            if not target.startswith("xl/"):
                target = "xl/" + target
            root = ET.fromstring(book.read(target))
            rows = []
            for row in root.findall(f".//{{{main_ns}}}sheetData/{{{main_ns}}}row"):
                values = {}
                for cell in row.findall(f"{{{main_ns}}}c"):
                    value_node = cell.find(f"{{{main_ns}}}v")
                    value = "" if value_node is None else value_node.text or ""
                    if cell.attrib.get("t") == "s" and value:
                        value = shared[int(value)]
                    values[_column_number(cell.attrib["r"])] = value.strip()
                rows.append((int(row.attrib["r"]), values))
            yield sheet_number, sheet.attrib["name"], rows


@dataclass
class GuardHouse:
    source_id: str
    brand_name: str
    store_name: str
    sigungu_name: str
    address: str
    latitude: str = ""
    longitude: str = ""
    geocode_status: str = "NOT_ATTEMPTED"
    geocoded_address: str = ""
    geocoded_sido: str = ""
    geocoded_sigungu: str = ""


def _extract_guard_houses(path: Path) -> list[GuardHouse]:
    result = []
    for sheet_number, sheet_name, rows in _xlsx_rows(path):
        sigungu = sheet_name.split(".", 1)[-1].strip()
        if not sigungu.endswith("구"):
            raise ValueError(f"자치구 시트명 오류: {sheet_name}")
        row_map = {number: values for number, values in rows}
        header = row_map.get(2, {})
        if sheet_number == 15:  # 양천구 원본은 연번/브랜드 열이 한 차례 중복됨
            brand_col, store_col, address_col = 6, 5, 7
        else:
            brand_col = next((column for column, name in header.items() if name == "브랜드"), 0)
            store_col = next((column for column, name in header.items() if name == "지점명"), 0)
            address_col = next((column for column, name in header.items() if name == "소재지"), 0)
        if not brand_col or not address_col:
            raise ValueError(f"필수 열을 찾을 수 없음: {sheet_name} {header}")
        for row_number, values in rows:
            if row_number <= 2:
                continue
            brand = values.get(brand_col, "").strip()
            store = values.get(store_col, "").strip() if store_col else ""
            address = values.get(address_col, "").strip()
            if not address:
                continue
            result.append(GuardHouse(
                source_id=f"2019-{sheet_number:02d}-{row_number:04d}",
                brand_name=brand,
                store_name=store,
                sigungu_name=sigungu,
                address=address,
            ))
    return result


def _geocode(address: str, api_key: str) -> dict[str, str] | None:
    for category in ("ROAD", "PARCEL"):
        query = urllib.parse.urlencode({
            "service": "search", "request": "search", "version": "2.0", "crs": "EPSG:4326",
            "size": 1, "page": 1, "query": address, "type": "ADDRESS", "category": category,
            "format": "json", "errorformat": "json", "key": api_key,
        })
        with urllib.request.urlopen("https://api.vworld.kr/req/search?" + query, timeout=10) as response:
            body = json.load(response)
        if body.get("response", {}).get("status") == "OK":
            item = body["response"]["result"]["items"][0]
            point = item["point"]
            reverse_query = urllib.parse.urlencode({
                "service": "address", "request": "getAddress", "version": "2.0", "crs": "EPSG:4326",
                "point": f'{point["x"]},{point["y"]}', "format": "json", "type": "both",
                "zipcode": "false", "simple": "false", "key": api_key,
            })
            with urllib.request.urlopen("https://api.vworld.kr/req/address?" + reverse_query, timeout=10) as response:
                reverse = json.load(response)
            result = (reverse.get("response", {}).get("result") or [{}])[0]
            structure = result.get("structure") or {}
            return {
                "latitude": str(point["y"]), "longitude": str(point["x"]),
                "address": item.get("address", {}).get("road") or item.get("address", {}).get("parcel") or item.get("title", ""),
                "sido": structure.get("level1", ""), "sigungu": structure.get("level2", ""),
            }
    return None


def _application_geocode(address: str, api_base: str) -> dict[str, str] | None:
    url = api_base.rstrip("/") + "/api/safety/search?" + urllib.parse.urlencode({"q": address})
    try:
        with urllib.request.urlopen(url, timeout=15) as response:
            location = json.load(response).get("location")
    except Exception:
        return None
    if not location:
        return None
    return {
        "latitude": str(location.get("latitude", "")), "longitude": str(location.get("longitude", "")),
        "address": location.get("address", ""), "sido": location.get("sidoName", ""),
        "sigungu": location.get("sigunguName", ""),
    }


def _address_candidates(row: GuardHouse) -> list[str]:
    original = re.sub(r"[\r\n\t]+", " ", row.address).strip()
    original = re.sub(r"\s+", " ", original)
    without_prefix = re.sub(r"^(?:서울특별시|서울시|서울)\s*", "", original)
    without_district = re.sub(rf"^{re.escape(row.sigungu_name)}\s*", "", without_prefix)
    canonical = f"서울특별시 {row.sigungu_name} {without_district}".strip()
    no_parentheses = re.sub(r"\([^)]*\)", " ", canonical)
    no_parentheses = re.sub(r"\s+", " ", no_parentheses).strip()
    before_comma = canonical.split(",", 1)[0].strip()
    spaced = re.sub(r"(?<=[가-힣])(?=\d)", " ", no_parentheses)
    spaced = re.sub(r"(\d)(?=번지|층|호)", r"\1 ", spaced)
    road_base = re.match(r"^(.+?(?:대로|로|길)\s*\d+(?:-\d+)?)", spaced)
    parcel_base = re.match(r"^(.+?[동가]\s*\d+(?:-\d+)?)", spaced)
    parenthetical = []
    for inner in re.findall(r"\(([^)]*)\)", canonical):
        cleaned = re.sub(r"^(?:서울특별시|서울시|서울)?\s*", "", inner).strip()
        cleaned = re.sub(rf"^{re.escape(row.sigungu_name)}\s*", "", cleaned)
        if re.search(r"(?:대로|로|길|[동가])\s*\d", cleaned):
            parenthetical.append(f"서울특별시 {row.sigungu_name} {cleaned}")
    candidates = [
        canonical, original, no_parentheses, before_comma, spaced,
        road_base.group(1) if road_base else "",
        parcel_base.group(1) if parcel_base else "",
        *parenthetical,
    ]
    return list(dict.fromkeys(candidate for candidate in candidates if candidate))


def process_guard_houses(
    source: Path,
    output: Path,
    geocode: bool = False,
    delay_seconds: float = 0.2,
    geocode_api_base: str | None = None,
) -> dict[str, int | str]:
    rows = _extract_guard_houses(source)
    api_key = os.getenv("VWORLD_API_KEY", "").strip()
    if geocode and not api_key and not geocode_api_base:
        raise ValueError("--geocode 실행에는 VWORLD_API_KEY 또는 --geocode-api-base가 필요합니다.")
    success = failure = mismatch = retry_success = 0
    if geocode:
        for row in rows:
            saw_mismatch = False
            for attempt, candidate in enumerate(_address_candidates(row)):
                try:
                    result = _application_geocode(candidate, geocode_api_base) if geocode_api_base else _geocode(candidate, api_key)
                except Exception:
                    result = None
                if not result:
                    continue
                if result["sido"].strip() == "서울특별시" and result["sigungu"].strip() == row.sigungu_name:
                    row.latitude = result["latitude"]
                    row.longitude = result["longitude"]
                    row.geocoded_address = result["address"].strip()
                    row.geocoded_sido = result["sido"].strip()
                    row.geocoded_sigungu = result["sigungu"].strip()
                    row.geocode_status = "VERIFIED"
                    success += 1
                    if attempt > 0:
                        retry_success += 1
                    break
                saw_mismatch = True
                row.geocoded_address = result["address"].strip()
                row.geocoded_sido = result["sido"].strip()
                row.geocoded_sigungu = result["sigungu"].strip()
            if row.geocode_status != "VERIFIED":
                row.geocode_status = "ADMIN_MISMATCH" if saw_mismatch else "FAILED"
                if saw_mismatch:
                    mismatch += 1
                else:
                    failure += 1
            time.sleep(max(0, delay_seconds))

    output.parent.mkdir(parents=True, exist_ok=True)
    fields = ["source_id", "brand_name", "store_name", "sido_name", "sigungu_name", "address",
              "latitude", "longitude", "geocode_status", "geocoded_address", "geocoded_sido",
              "geocoded_sigungu", "source_name", "source_updated_at"]
    with output.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({
                "source_id": row.source_id, "brand_name": row.brand_name, "store_name": row.store_name,
                "sido_name": "서울특별시", "sigungu_name": row.sigungu_name, "address": row.address,
                "latitude": row.latitude, "longitude": row.longitude, "source_name": WOMEN_SOURCE,
                "geocode_status": row.geocode_status, "geocoded_address": row.geocoded_address,
                "geocoded_sido": row.geocoded_sido, "geocoded_sigungu": row.geocoded_sigungu,
                "source_updated_at": WOMEN_SOURCE_DATE,
            })
    duplicates = len(rows) - len({(row.sigungu_name, row.brand_name.casefold(), row.store_name.casefold(), row.address) for row in rows})
    return {
        "rawRows": len(rows), "validAddresses": sum(bool(row.address) for row in rows),
        "geocodingSuccess": success, "geocodingFailure": failure,
        "administrativeMismatch": mismatch, "retrySuccess": retry_success,
        "dbReadyRows": sum(bool(row.latitude and row.longitude) for row in rows),
        "duplicates": duplicates, "sourceUpdatedAt": WOMEN_SOURCE_DATE,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--crime-source", type=Path, default=Path("rpa/safety/data/raw/seoul_police_crime_statistics_2024.csv"))
    parser.add_argument("--crime-output", type=Path, default=Path("rpa/safety/data/processed/seoul_police_crime_statistics_2024.csv"))
    parser.add_argument("--women-source", type=Path, default=Path("rpa/safety/data/raw/seoul_women_safe_houses_2019.xlsx"))
    parser.add_argument("--women-output", type=Path, default=Path("rpa/safety/data/processed/seoul_women_safe_houses_2019.csv"))
    parser.add_argument("--geocode", action="store_true")
    parser.add_argument("--delay-seconds", type=float, default=0.2)
    parser.add_argument("--geocode-api-base", help="기존 ZipAI VWorld 검색 API base URL (예: http://127.0.0.1:8080)")
    args = parser.parse_args()
    print(json.dumps({
        "crime": process_crime(args.crime_source, args.crime_output),
        "womenSafeHouses": process_guard_houses(
            args.women_source, args.women_output, args.geocode, args.delay_seconds, args.geocode_api_base
        ),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
