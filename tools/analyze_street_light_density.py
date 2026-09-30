from pathlib import Path

import pandas as pd
from sklearn.neighbors import BallTree


RAW_CSV = Path("rpa/safety/data/raw/전국보안등정보표준데이터_api.csv")
OUTPUT_CSV = Path("rpa/safety/data/processed/street_light_density_sample.csv")

LATITUDE_COL = "latitude"
LONGITUDE_COL = "longitude"
INSTALLATION_COUNT_COL = "installationCo"
ROAD_ADDRESS_COL = "rdnmadr"
LOT_ADDRESS_COL = "lnmadr"

EARTH_RADIUS_M = 6_371_008.8
RADIUS_M = 500
MAX_SAMPLE_SIZE = 10_000
RANDOM_STATE = 42

INSTALLATION_PERCENTILES = [0.50, 0.75, 0.90, 0.95, 0.99, 0.999]
DENSITY_PERCENTILES = [0.25, 0.50, 0.75, 0.90, 0.95, 0.99]


def main():
    print(f"Loading raw CSV: {RAW_CSV}")
    data = pd.read_csv(RAW_CSV, dtype=str, low_memory=False)
    print(f"Raw rows: {len(data):,}")
    print(f"Columns: {list(data.columns)}")

    require_columns(data)

    data["latitude_num"] = pd.to_numeric(data[LATITUDE_COL], errors="coerce")
    data["longitude_num"] = pd.to_numeric(data[LONGITUDE_COL], errors="coerce")
    data["installation_co_num"] = pd.to_numeric(data[INSTALLATION_COUNT_COL], errors="coerce")

    print_coordinate_quality(data)
    print_installation_distribution(data)

    valid = data[
        data["latitude_num"].between(33, 39, inclusive="both")
        & data["longitude_num"].between(124, 132, inclusive="both")
    ].copy()
    valid["installation_co_for_sum"] = valid["installation_co_num"].fillna(0)
    valid["address_for_group"] = (
        valid[ROAD_ADDRESS_COL]
        .where(valid[ROAD_ADDRESS_COL].notna() & (valid[ROAD_ADDRESS_COL].str.strip() != ""), valid[LOT_ADDRESS_COL])
        .fillna("")
    )
    valid["region_group"] = valid["address_for_group"].map(classify_region)

    print(f"Valid Korea WGS84 coordinate rows: {len(valid):,}")
    if valid.empty:
        raise SystemExit("No valid coordinate rows found. Density analysis cannot continue.")

    sample_size = min(MAX_SAMPLE_SIZE, len(valid))
    sample = valid.sample(n=sample_size, random_state=RANDOM_STATE).copy()
    print(f"Sample rows: {sample_size:,} (random_state={RANDOM_STATE})")

    result = calculate_density(valid, sample)
    print_density_distribution("National sample row_count within 500m", result["row_count"])
    print_density_distribution("National sample installation_count within 500m", result["installation_count"])
    print_group_distribution(result)

    OUTPUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    output_columns = [
        "lmpLcNm",
        "rdnmadr",
        "lnmadr",
        "latitude",
        "longitude",
        "installationCo",
        "institutionNm",
        "referenceDate",
        "region_group",
        "row_count",
        "installation_count",
    ]
    existing_output_columns = [col for col in output_columns if col in result.columns]
    result[existing_output_columns].to_csv(OUTPUT_CSV, index=False, encoding="utf-8-sig")
    print(f"Sample density CSV written: {OUTPUT_CSV}")
    print("Done. Raw CSV was not modified.")


def require_columns(data):
    required = [
        LATITUDE_COL,
        LONGITUDE_COL,
        INSTALLATION_COUNT_COL,
        ROAD_ADDRESS_COL,
        LOT_ADDRESS_COL,
    ]
    missing = [column for column in required if column not in data.columns]
    if missing:
        raise SystemExit(f"Missing required columns: {missing}")


def print_coordinate_quality(data):
    latitude = data["latitude_num"]
    longitude = data["longitude_num"]
    valid_korea = latitude.between(33, 39, inclusive="both") & longitude.between(124, 132, inclusive="both")
    zero_zero = (latitude == 0) & (longitude == 0)
    swapped = latitude.between(124, 132, inclusive="both") & longitude.between(33, 39, inclusive="both")
    null_coord = latitude.isna() | longitude.isna()
    out_of_korea = latitude.notna() & longitude.notna() & ~valid_korea & ~zero_zero & ~swapped

    print("Coordinate quality:")
    print(f"- valid Korea WGS84 rows: {valid_korea.sum():,}")
    print(f"- null latitude/longitude rows: {null_coord.sum():,}")
    print(f"- 0,0 coordinate rows: {zero_zero.sum():,}")
    print(f"- likely swapped lat/lng rows: {swapped.sum():,}")
    print(f"- out-of-Korea coordinate rows: {out_of_korea.sum():,}")


def print_installation_distribution(data):
    installation = data["installation_co_num"].dropna()
    missing = data["installation_co_num"].isna().sum()
    print("installationCo quality:")
    print(f"- numeric rows: {len(installation):,}")
    print(f"- null/non-numeric rows: {missing:,}")
    if installation.empty:
        print("- distribution: no numeric installationCo values")
        return

    print("- distribution:")
    for percentile in INSTALLATION_PERCENTILES:
        label = format_percentile(percentile)
        value = installation.quantile(percentile)
        print(f"  {label}: {value:,.2f}")
    print(f"  max: {installation.max():,.2f}")


def calculate_density(valid, sample):
    print("Building BallTree with haversine metric...")
    valid_coords_radians = valid[["latitude_num", "longitude_num"]] * (3.141592653589793 / 180)
    sample_coords_radians = sample[["latitude_num", "longitude_num"]] * (3.141592653589793 / 180)
    tree = BallTree(valid_coords_radians.to_numpy(), metric="haversine")
    radius_radians = RADIUS_M / EARTH_RADIUS_M

    print(f"Querying {RADIUS_M}m radius for {len(sample):,} sample points...")
    neighbor_indices = tree.query_radius(sample_coords_radians.to_numpy(), r=radius_radians)
    installation_values = valid["installation_co_for_sum"].to_numpy()

    sample["row_count"] = [len(indices) for indices in neighbor_indices]
    sample["installation_count"] = [installation_values[indices].sum() for indices in neighbor_indices]
    return sample


def print_density_distribution(title, values):
    print(f"{title}:")
    print(f"- mean: {values.mean():,.2f}")
    print(f"- median: {values.median():,.2f}")
    for percentile in DENSITY_PERCENTILES:
        label = format_percentile(percentile)
        value = values.quantile(percentile)
        print(f"- {label}: {value:,.2f}")
    print(f"- max: {values.max():,.2f}")


def print_group_distribution(result):
    print("Regional sample density distribution:")
    for group_name, group in result.groupby("region_group", dropna=False):
        print(f"[{group_name}] sample rows: {len(group):,}")
        print_density_distribution("row_count", group["row_count"])
        print_density_distribution("installation_count", group["installation_count"])


def classify_region(address):
    text = str(address).strip()
    if text.startswith("서울"):
        return "서울"
    if text.startswith("경기"):
        return "경기"
    if text.startswith("인천"):
        return "인천"
    if text.startswith(("부산", "대구", "광주", "대전", "울산")):
        return "광역시(부산/대구/광주/대전/울산)"
    return "기타 지역"


def format_percentile(percentile):
    percent = percentile * 100
    if percent.is_integer():
        return f"{int(percent)}%"
    return f"{percent:g}%"


if __name__ == "__main__":
    main()
