import argparse
import csv
from pathlib import Path


OUTPUT_COLUMNS = [
    "year", "sido_name", "sigungu_name", "police_station_name", "police_agency",
    "crime_type", "occurrence_count", "arrest_count", "provisional", "coverage_type",
    "coverage_note", "source_name", "source_updated_at",
]

SOUTH_STATION_REGIONS = {
    "수원중부": "수원시", "수원남부": "수원시", "수원서부": "수원시",
    "안양동안": "안양시", "안양만안": "안양시", "군포": "군포시",
    "성남수정": "성남시", "성남중원": "성남시", "분당": "성남시",
    "부천소사": "부천시", "부천원미": "부천시", "부천오정": "부천시",
    "광명": "광명시", "안산단원": "안산시", "안산상록": "안산시",
    "시흥": "시흥시", "평택": "평택시", "오산": "오산시",
    "화성서부": "화성시", "화성동탄": "화성시",
    "용인동부": "용인시", "용인서부": "용인시", "광주": "광주시",
    "김포": "김포시", "하남": "하남시", "과천": "과천시",
    "의왕": "의왕시", "이천": "이천시", "안성": "안성시",
    "여주": "여주시", "양평": "양평군",
}

SOUTH_CRIME_COLUMNS = {
    "살인": ("살인 발생", "살인 검거"),
    "강도": ("강도 발생", "강도 검거"),
    "강간·추행": ("강간 강제추행 발생", "강간 강제추행 검거"),
    "절도": ("절도 발생", "절도 검거"),
    "폭력": ("폭력 발생", "폭력 검거"),
}


def number(value):
    return int(str(value).replace(",", "").strip())


def process_north(path):
    rows = []
    with path.open(encoding="cp949", newline="") as handle:
        for raw in csv.DictReader(handle):
            year = number(raw["연도"])
            crime_type = "강간·추행" if raw["범죄유형"].strip() == "성범죄" else raw["범죄유형"].strip()
            rows.append({
                "year": year,
                "sido_name": "경기도",
                "sigungu_name": "",
                "police_station_name": "경기북부경찰청",
                "police_agency": "경기북부경찰청",
                "crime_type": crime_type,
                "occurrence_count": number(raw["발생건수"]),
                "arrest_count": number(raw["검거건수"]),
                "provisional": "true" if year == 2025 else "false",
                "coverage_type": "POLICE_AGENCY_STATISTICS",
                "coverage_note": (
                    "경기북부경찰청 관내 전체 합계이며 개별 시군 통계가 아닙니다. "
                    "2025년 수치는 잠정치이며 2026년 10월 확정 통계 등록 예정입니다."
                    if year == 2025
                    else "경기북부경찰청 관내 전체 합계이며 개별 시군 통계가 아닙니다."
                ),
                "source_name": "경찰청 경기도북부경찰청_5대범죄 발생 및 검거현황",
                "source_updated_at": "2025-12-31",
            })
    return rows


def process_south(path):
    rows = []
    with path.open(encoding="cp949", newline="") as handle:
        for raw in csv.DictReader(handle):
            station = raw["관서명"].strip()
            if station == "도경찰청":
                continue
            sigungu = SOUTH_STATION_REGIONS.get(station)
            if not sigungu:
                raise ValueError(f"공식 관서-시군 매핑이 없습니다: {station}")
            for crime_type, (occurrence_column, arrest_column) in SOUTH_CRIME_COLUMNS.items():
                rows.append({
                    "year": 2022,
                    "sido_name": "경기도",
                    "sigungu_name": sigungu,
                    "police_station_name": station + "경찰서",
                    "police_agency": "경기남부경찰청",
                    "crime_type": crime_type,
                    "occurrence_count": number(raw[occurrence_column]),
                    "arrest_count": number(raw[arrest_column]),
                    "provisional": "false",
                    "coverage_type": "POLICE_STATION_STATISTICS",
                    "coverage_note": "경기남부경찰청 2022년 경찰서별 확정 통계입니다.",
                    "source_name": "경찰청 경기도남부경찰청_5대범죄 발생검거 건수",
                    "source_updated_at": "2022-12-31",
                })
    return rows


def validate(rows):
    keys = [(row["year"], row["police_agency"], row["police_station_name"], row["crime_type"]) for row in rows]
    if len(keys) != len(set(keys)):
        raise ValueError("중복 범죄 통계가 있습니다.")
    if any(row[column] in (None, "") for row in rows for column in OUTPUT_COLUMNS if column != "sigungu_name"):
        raise ValueError("필수 컬럼에 빈 값이 있습니다.")
    if {row["crime_type"] for row in rows} != set(SOUTH_CRIME_COLUMNS):
        raise ValueError("5대범죄 유형이 일치하지 않습니다.")
    return {
        "processedRows": len(rows),
        "stations": len({row["police_station_name"] for row in rows}),
        "southStations": len({row["police_station_name"] for row in rows if row["police_agency"] == "경기남부경찰청"}),
        "southRegions": len({row["sigungu_name"] for row in rows if row["police_agency"] == "경기남부경찰청"}),
        "northRows": sum(row["police_agency"] == "경기북부경찰청" for row in rows),
        "duplicates": len(keys) - len(set(keys)),
    }


def write_csv(rows, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--north", type=Path, required=True)
    parser.add_argument("--south", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = process_north(args.north) + process_south(args.south)
    summary = validate(rows)
    write_csv(rows, args.output)
    for key, value in summary.items():
        print(f"{key}={value}")


if __name__ == "__main__":
    main()
