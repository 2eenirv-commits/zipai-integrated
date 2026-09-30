import argparse
import hashlib
from pathlib import Path

import pandas as pd

try:
    from dotenv import load_dotenv
except ModuleNotFoundError:
    def load_dotenv():
        return False


RAW_DIR = Path("rpa/safety/data/raw")
PROCESSED_DIR = Path("rpa/safety/data/processed")
COMMON_COLUMNS = ["source_id", "name", "facility_type", "address", "latitude", "longitude", "source", "source_updated_at"]


def clean_text(value):
    if pd.isna(value):
        return ""
    return str(value).strip().replace("\u3000", " ").replace("\r", " ").replace("\n", " ")


def stable_id(*parts):
    text = "|".join(clean_text(part) for part in parts)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:32]


def stable_full_id(*parts):
    text = "|".join(clean_text(part) for part in parts)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def to_number(series):
    return pd.to_numeric(series, errors="coerce")


def is_valid_coordinate(frame):
    lat = to_number(frame["latitude"])
    lng = to_number(frame["longitude"])
    return lat.between(-90, 90) & lng.between(-180, 180)


def is_korea_wgs84_coordinate(frame):
    lat = to_number(frame["latitude"])
    lng = to_number(frame["longitude"])
    return lat.between(33, 39) & lng.between(124, 132)


def read_csv(path):
    if path.name == "전국보안등정보표준데이터_api.csv":
        return pd.read_csv(path, encoding="utf-8-sig", dtype=str, low_memory=False)
    return pd.read_csv(path, encoding="cp949", dtype=str, low_memory=False)


def detect_files(raw_dir):
    files = list(raw_dir.glob("*.csv"))
    cctv = next((path for path in files if "CCTV" in path.name.upper()), None)
    police = next((path for path in files if "지구대" in path.name or "파출소" in path.name or "경찰" in path.name), None)
    safety_bell = next((path for path in files if "비상벨" in path.name), None)
    street_light = next((path for path in files if path.name == "전국보안등정보표준데이터_api.csv"), None)
    return cctv, police, safety_bell, street_light


def process_cctv(path):
    frame = read_csv(path)
    raw_count = len(frame)
    address = frame["소재지도로명주소"].where(frame["소재지도로명주소"].notna(), frame["소재지지번주소"])
    normalized = pd.DataFrame({
        "source_id": frame["관리번호"].astype(str).map(lambda value: stable_id("CCTV", value)),
        "name": frame["관리기관명"].map(clean_text) + " " + frame["설치목적구분"].map(clean_text) + " CCTV",
        "facility_type": "CCTV",
        "address": address.map(clean_text),
        "latitude": to_number(frame["WGS84위도"]),
        "longitude": to_number(frame["WGS84경도"]),
        "source": "공공데이터 CCTV정보",
        "source_updated_at": frame["데이터기준일자"].map(clean_text),
        "purpose": frame["설치목적구분"].map(clean_text),
        "camera_count": pd.to_numeric(frame["카메라대수"], errors="coerce").fillna(1).astype(int),
    })
    before_dedup = len(normalized)
    normalized = normalized[normalized["address"] != ""]
    address_missing = raw_count - len(normalized)
    coordinate_missing = int(normalized["latitude"].isna().sum() + normalized["longitude"].isna().sum())
    invalid_coordinates = int((~is_valid_coordinate(normalized)).sum())
    normalized = normalized[is_valid_coordinate(normalized)]
    normalized = normalized.drop_duplicates(subset=["source_id"])
    dedup_removed = before_dedup - address_missing - invalid_coordinates - len(normalized)
    output = PROCESSED_DIR / "cctv_processed.csv"
    write_processed(normalized, output)
    print_stats("CCTV", path, raw_count, len(normalized), coordinate_missing, address_missing, invalid_coordinates, max(0, dedup_removed))
    print("설치목적구분:", frame["설치목적구분"].fillna("").astype(str).value_counts().to_dict())
    return output


def process_police(path):
    frame = read_csv(path)
    raw_count = len(frame)
    facility_type = frame["구분"].map(lambda value: "POLICE_BOX" if clean_text(value) in ["지구대", "파출소"] else "POLICE_STATION")
    normalized = pd.DataFrame({
        "source_id": [
            stable_id("POLICE", row.시도청, row.경찰서, row.관서명, row.구분, row.주소)
            for row in frame.itertuples(index=False)
        ],
        "name": frame["경찰서"].map(clean_text) + " " + frame["관서명"].map(clean_text),
        "facility_type": facility_type,
        "address": frame["주소"].map(clean_text),
        "latitude": pd.NA,
        "longitude": pd.NA,
        "source": "경찰청 전국 지구대 파출소 주소 현황",
        "source_updated_at": "2025-12-31",
        "office_type": frame["구분"].map(clean_text),
    })
    before_dedup = len(normalized)
    normalized = normalized[normalized["address"] != ""]
    address_missing = raw_count - len(normalized)
    coordinate_missing = len(normalized)
    invalid_coordinates = 0
    normalized = normalized.drop_duplicates(subset=["source_id"])
    dedup_removed = before_dedup - address_missing - len(normalized)
    output = PROCESSED_DIR / "police_processed.csv"
    write_processed(normalized, output)
    print_stats("경찰시설", path, raw_count, len(normalized), coordinate_missing, address_missing, invalid_coordinates, max(0, dedup_removed))
    print("구분:", frame["구분"].fillna("").astype(str).value_counts().to_dict())
    return output


def process_safety_bell(path):
    frame = read_csv(path)
    raw_count = len(frame)
    address = frame["소재지도로명주소"].where(frame["소재지도로명주소"].notna(), frame["소재지지번주소"])
    name = frame["안전비상벨관리번호"].map(clean_text)
    fallback_name = frame["설치위치"].map(clean_text)
    purpose = (
        frame["설치목적"].map(clean_text)
        + " / "
        + frame["설치장소유형"].map(clean_text)
    ).str.strip(" /")
    normalized = pd.DataFrame({
        "source_id": "SAFETY_BELL:" + frame["관리번호"].astype(str).map(clean_text),
        "name": name.where(name != "", fallback_name).replace("", "안전비상벨"),
        "facility_type": "SAFETY_BELL",
        "address": address.map(clean_text),
        "latitude": to_number(frame["WGS84위도"]),
        "longitude": to_number(frame["WGS84경도"]),
        "source": "공공데이터 안전비상벨위치정보",
        "source_updated_at": frame["데이터기준일자"].map(clean_text),
        "purpose": purpose.str.slice(0, 50),
        "camera_count": 1,
    })
    before_dedup = len(normalized)
    normalized = normalized[normalized["address"] != ""]
    address_missing = raw_count - len(normalized)
    coordinate_missing = int(normalized["latitude"].isna().sum() + normalized["longitude"].isna().sum())
    invalid_coordinates = int((~is_korea_wgs84_coordinate(normalized)).sum())
    normalized = normalized[is_korea_wgs84_coordinate(normalized)]
    normalized = normalized.drop_duplicates(subset=["source_id"])
    dedup_removed = before_dedup - address_missing - invalid_coordinates - len(normalized)
    output = PROCESSED_DIR / "safety_bell_processed.csv"
    write_processed(normalized, output)
    print_stats("안전비상벨", path, raw_count, len(normalized), coordinate_missing, address_missing, invalid_coordinates, max(0, dedup_removed))
    print("좌표계 판별: WGS84위도/WGS84경도 컬럼 사용")
    print("좌표 변환: 하지 않음")
    print("설치목적:", frame["설치목적"].fillna("").astype(str).value_counts().to_dict())
    print("설치장소유형:", frame["설치장소유형"].fillna("").astype(str).value_counts().to_dict())
    return output


def process_street_light(path):
    frame = read_csv(path)
    raw_count = len(frame)
    latitude = to_number(frame["latitude"])
    longitude = to_number(frame["longitude"])
    valid_coordinate = latitude.between(33, 39) & longitude.between(124, 132)
    normal_coordinate_count = int(valid_coordinate.sum())
    excluded_coordinate_count = raw_count - normal_coordinate_count

    address = frame["rdnmadr"].where(frame["rdnmadr"].notna() & (frame["rdnmadr"].map(clean_text) != ""), frame["lnmadr"])
    name = frame["lmpLcNm"].map(clean_text).replace("", "보안등")
    address_clean = address.map(clean_text)
    source_updated_at = frame["referenceDate"].map(clean_text)

    normalized = pd.DataFrame({
        "source_id": [
            "STREET_LIGHT:" + stable_full_id(
                "STREET_LIGHT",
                row.insttCode,
                row.lmpLcNm,
                row.address,
                row.latitude,
                row.longitude,
            )
            for row in pd.DataFrame({
                "insttCode": frame["insttCode"].map(clean_text),
                "lmpLcNm": frame["lmpLcNm"].map(clean_text),
                "address": address_clean,
                "latitude": latitude.map(lambda value: "" if pd.isna(value) else f"{value:.8f}"),
                "longitude": longitude.map(lambda value: "" if pd.isna(value) else f"{value:.8f}"),
            }).itertuples(index=False)
        ],
        "name": name,
        "facility_type": "STREET_LIGHT",
        "address": address_clean,
        "latitude": latitude,
        "longitude": longitude,
        "source": "전국보안등정보표준데이터",
        "source_updated_at": source_updated_at,
        "camera_count": pd.NA,
    })

    normalized = normalized[valid_coordinate]
    before_address_filter = len(normalized)
    normalized = normalized[normalized["address"] != ""]
    address_missing = before_address_filter - len(normalized)

    before_dedup = len(normalized)
    source_id_duplicate_count = int(normalized["source_id"].duplicated().sum())
    normalized = normalized.drop_duplicates(subset=["source_id"])
    dedup_removed = before_dedup - len(normalized)

    output = PROCESSED_DIR / "street_light_processed.csv"
    write_processed(normalized, output)

    print("\n[보안등]")
    print(f"파일명: {path.name}")
    print(f"원본 행 수: {raw_count}")
    print(f"정상 좌표 행 수: {normal_coordinate_count}")
    print(f"제외된 좌표 행 수: {excluded_coordinate_count}")
    print(f"주소 누락 제외 수: {address_missing}")
    print(f"중복 제거 수: {dedup_removed}")
    print(f"최종 processed 행 수: {len(normalized)}")
    print(f"source_id 중복 수: {source_id_duplicate_count}")
    print("installationCo 처리: 원본 1행을 DB 1행으로 처리, installationCo만큼 복제하지 않음")
    return output


def write_processed(frame, output):
    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    base_columns = [column for column in frame.columns if column in COMMON_COLUMNS]
    extra_columns = [column for column in frame.columns if column not in COMMON_COLUMNS]
    frame[base_columns + extra_columns].to_csv(output, index=False, encoding="utf-8-sig")


def print_stats(name, path, raw_count, final_count, coordinate_missing, address_missing, invalid_coordinates, dedup_removed):
    print(f"\n[{name}]")
    print(f"파일명: {path.name}")
    print(f"원본 row: {raw_count}")
    print(f"정상 변환: {final_count}")
    print(f"좌표 누락: {coordinate_missing}")
    print(f"주소 누락: {address_missing}")
    print(f"잘못된 좌표: {invalid_coordinates}")
    print(f"중복 제거: {dedup_removed}")
    print(f"최종 row: {final_count}")


def analyze_raw(raw_dir):
    for path in sorted(raw_dir.glob("*.csv")):
        frame = read_csv(path)
        print(f"\n[분석] {path.name}")
        print(f"파일 크기: {path.stat().st_size} bytes")
        print("encoding: cp949")
        print("delimiter: ,")
        print(f"전체 row: {len(frame)}")
        print("columns:", list(frame.columns))
        lat_cols = [column for column in frame.columns if "위도" in column or column.lower() in ["lat", "latitude"]]
        lng_cols = [column for column in frame.columns if "경도" in column or column.lower() in ["lng", "lon", "longitude"]]
        print("위도 column:", lat_cols[0] if lat_cols else None)
        print("경도 column:", lng_cols[0] if lng_cols else None)
        for column in frame.columns:
            if any(token in column for token in ["주소", "위도", "경도", "목적", "구분", "기관", "관서", "관리번호"]):
                print(f"{column}: missing={int(frame[column].isna().sum())}, unique={int(frame[column].nunique(dropna=True))}")
        print("전체 row 중복:", int(frame.duplicated().sum()))


def process_raw(raw_dir, only="all"):
    cctv, police, safety_bell, street_light = detect_files(raw_dir)
    outputs = []
    if only in ["all", "cctv"] and cctv:
        outputs.append(process_cctv(cctv))
    elif only in ["all", "cctv"]:
        print("CCTV CSV를 찾지 못했습니다.")
    if only in ["all", "police"] and police:
        outputs.append(process_police(police))
    elif only in ["all", "police"]:
        print("경찰 CSV를 찾지 못했습니다.")
    if only in ["all", "safety-bell"] and safety_bell:
        outputs.append(process_safety_bell(safety_bell))
    elif only in ["all", "safety-bell"]:
        print("안전비상벨 CSV를 찾지 못했습니다.")
    if only in ["all", "street-light"] and street_light:
        outputs.append(process_street_light(street_light))
    elif only in ["all", "street-light"]:
        print("전국보안등정보표준데이터_api.csv를 찾지 못했습니다.")
    print("\n생성 파일:")
    for output in outputs:
        print(output)


def main():
    load_dotenv()
    parser = argparse.ArgumentParser(description="Analyze and normalize public safety raw CSV files.")
    parser.add_argument("--raw-dir", default=str(RAW_DIR))
    parser.add_argument("--analyze-only", action="store_true")
    parser.add_argument("--only", choices=["all", "cctv", "police", "safety-bell", "street-light"], default="all")
    args = parser.parse_args()
    raw_dir = Path(args.raw_dir)
    if not raw_dir.exists():
        raise FileNotFoundError(f"raw directory not found: {raw_dir}")
    analyze_raw(raw_dir)
    if not args.analyze_only:
        process_raw(raw_dir, args.only)


if __name__ == "__main__":
    main()
