#!/usr/bin/env python3
"""Extract the official 2025 regional safety grades directly from HWPX XML."""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass, asdict
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET


YEAR = 2025
SOURCE_NAME = "행정안전부 2025년 지역안전지수 산출결과"
SOURCE_UPDATED_AT = "2026-01-12"
SECTION_PATH = "Contents/section0.xml"
GRADE_HEADERS = ("교통사고", "화재", "범죄", "생활안전", "자살", "감염병")
CSV_COLUMNS = (
    "year", "region_level", "sido_name", "sigungu_name",
    "traffic_grade", "fire_grade", "crime_grade", "life_safety_grade",
    "suicide_grade", "infectious_disease_grade", "source_name", "source_updated_at",
)

FULL_SIDO_NAME = {
    "서울": "서울특별시", "부산": "부산광역시", "대구": "대구광역시",
    "인천": "인천광역시", "광주": "광주광역시", "대전": "대전광역시",
    "울산": "울산광역시", "세종": "세종특별자치시", "경기": "경기도",
    "강원": "강원특별자치도", "충북": "충청북도", "충남": "충청남도",
    "전북": "전북특별자치도", "전남": "전라남도", "경북": "경상북도",
    "경남": "경상남도", "제주": "제주특별자치도",
}


@dataclass(frozen=True)
class RegionalSafetyRow:
    year: int
    region_level: str
    sido_name: str
    sigungu_name: str
    traffic_grade: int
    fire_grade: int
    crime_grade: int
    life_safety_grade: int
    suicide_grade: int
    infectious_disease_grade: int
    source_name: str
    source_updated_at: str


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _cell_text(cell: ET.Element) -> str:
    return "".join(
        node.text or "" for node in cell.iter() if _local_name(node.tag) == "t"
    ).strip()


def _table_matrix(table: ET.Element) -> list[list[str]]:
    matrix: list[list[str]] = []
    for row in (node for node in table if _local_name(node.tag) == "tr"):
        positioned: list[tuple[int, str]] = []
        for cell in (node for node in row if _local_name(node.tag) == "tc"):
            address = next(
                (node for node in cell if _local_name(node.tag) == "cellAddr"), None
            )
            if address is None:
                raise ValueError("HWPX table cell is missing cellAddr")
            positioned.append((int(address.attrib["colAddr"]), _cell_text(cell)))
        if positioned:
            matrix.append([value for _, value in sorted(positioned)])
    return matrix


def _grade(value: str, region: str, field: str) -> int:
    try:
        grade = int(value)
    except ValueError as error:
        raise ValueError(f"{region} {field} 등급이 정수가 아닙니다: {value!r}") from error
    if grade not in range(1, 6):
        raise ValueError(f"{region} {field} 등급 범위 오류: {grade}")
    return grade


def parse_hwpx(path: Path) -> list[RegionalSafetyRow]:
    with ZipFile(path) as archive:
        root = ET.fromstring(archive.read(SECTION_PATH))

    result: list[RegionalSafetyRow] = []
    for table in (node for node in root.iter() if _local_name(node.tag) == "tbl"):
        matrix = _table_matrix(table)
        if not matrix:
            continue
        header = tuple(matrix[0])
        if header == ("구분", "시도", *GRADE_HEADERS):
            for values in matrix[1:]:
                if len(values) != 8:
                    raise ValueError(f"시도 표 열 개수 오류: {values}")
                sido = values[1]
                grades = [_grade(value, sido, field) for value, field in zip(values[2:], GRADE_HEADERS)]
                result.append(_row("sido", sido, "", grades))
        elif header == ("구분", "시도", "시군구", *GRADE_HEADERS):
            for values in matrix[1:]:
                if len(values) != 9:
                    raise ValueError(f"시군구 표 열 개수 오류: {values}")
                short_sido, sigungu = values[1], values[2]
                if short_sido not in FULL_SIDO_NAME:
                    raise ValueError(f"알 수 없는 시도 약칭: {short_sido}")
                grades = [_grade(value, sigungu, field) for value, field in zip(values[3:], GRADE_HEADERS)]
                result.append(_row("sigungu", FULL_SIDO_NAME[short_sido], sigungu, grades))
    return result


def _row(level: str, sido: str, sigungu: str, grades: list[int]) -> RegionalSafetyRow:
    return RegionalSafetyRow(
        YEAR, level, sido, sigungu, *grades, SOURCE_NAME, SOURCE_UPDATED_AT
    )


def validate(rows: list[RegionalSafetyRow]) -> dict[str, object]:
    sido_rows = [row for row in rows if row.region_level == "sido"]
    sigungu_rows = [row for row in rows if row.region_level == "sigungu"]
    keys = [(row.year, row.region_level, row.sido_name, row.sigungu_name) for row in rows]
    duplicates = sorted({key for key in keys if keys.count(key) > 1})
    invalid_required = [
        row for row in rows
        if not row.sido_name or (row.region_level == "sigungu" and not row.sigungu_name)
    ]
    grades = [
        grade for row in rows for grade in (
            row.traffic_grade, row.fire_grade, row.crime_grade,
            row.life_safety_grade, row.suicide_grade, row.infectious_disease_grade,
        )
    ]
    if len(sido_rows) != 17:
        raise ValueError(f"시도 행은 17개여야 합니다: {len(sido_rows)}")
    if len(sigungu_rows) != 226:
        raise ValueError(f"시군구 행은 226개여야 합니다: {len(sigungu_rows)}")
    if invalid_required:
        raise ValueError(f"필수 지역명이 비어 있습니다: {len(invalid_required)}행")
    if duplicates:
        raise ValueError(f"중복 지역이 있습니다: {duplicates}")
    if not grades or min(grades) != 1 or max(grades) != 5:
        raise ValueError("등급 전체 범위가 1~5인지 확인할 수 없습니다")
    return {
        "total_rows": len(rows),
        "sido_rows": len(sido_rows),
        "sigungu_rows": len(sigungu_rows),
        "required_null_rows": len(invalid_required),
        "grade_min": min(grades),
        "grade_max": max(grades),
        "duplicate_regions": len(duplicates),
    }


def write_csv(rows: list[RegionalSafetyRow], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        writer.writerows(asdict(row) for row in rows)


def main() -> None:
    base = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input", type=Path,
        default=base / "data/raw/regional_safety/2025_regional_safety_index_result.hwpx",
    )
    parser.add_argument(
        "--output", type=Path,
        default=base / "data/processed/regional_safety_index_2025.csv",
    )
    args = parser.parse_args()
    rows = parse_hwpx(args.input)
    summary = validate(rows)
    write_csv(rows, args.output)
    print(f"CSV: {args.output}")
    for key, value in summary.items():
        print(f"{key}: {value}")


if __name__ == "__main__":
    main()
