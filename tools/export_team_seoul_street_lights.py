#!/usr/bin/env python3
"""Export and validate Seoul street lights exposed by the team safety API.

The default mode is a read-only dry-run.  A CSV is written only when --write is
explicitly supplied and every API request and validation has succeeded.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import re
import sys
import tempfile
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit, urlunsplit
from urllib.request import Request, urlopen


API_PATH = "/api/safety/infrastructure"
RADIUS_METERS = 2_000
MAX_WORKERS = 4
MAX_ATTEMPTS = 3
REQUEST_DELAY_SECONDS = 0.08
REQUEST_TIMEOUT_SECONDS = 60

# Rectangle previously used to audit the team's Seoul data. API results are
# filtered back to this rectangle after the overlapping grid has been queried.
SEOUL_MIN_LAT = 37.40
SEOUL_MAX_LAT = 37.72
SEOUL_MIN_LON = 126.74
SEOUL_MAX_LON = 127.21

SOURCE_ID_RE = re.compile(r"^SECURITY_LIGHT_SEOUL:[0-9a-f]{24}$")
SOURCE_ID_PREFIX = "SECURITY_LIGHT_SEOUL:"
REQUIRED_API_FIELDS = (
    "sourceId",
    "name",
    "address",
    "latitude",
    "longitude",
    "sourceName",
    "sourceUpdatedAt",
)
CSV_COLUMNS = (
    "source_id",
    "name",
    "facility_type",
    "address",
    "latitude",
    "longitude",
    "source",
    "source_updated_at",
    "purpose",
    "camera_count",
)
EXPECTED_DISTRICT_COUNTS = {
    "서초구": 10_692,
    "관악구": 13_989,
    "동작구": 8_469,
    "강동구": 7_262,
    "서대문구": 9_518,
    "은평구": 8_673,
}
YEOKSAM_CHECKS = (
    ("team_coordinate", 37.495484, 127.033357, 81),
    ("personal_coordinate", 37.4953666908087, 127.03306536185, 83),
)


@dataclass(frozen=True)
class GridPoint:
    latitude: float
    longitude: float


@dataclass
class ExistingDataComparison:
    path: Path | None = None
    row_count: int = 0
    source_id_collisions: int = 0
    seoul_address_rows: int = 0
    seoul_coordinate_rows: int = 0
    suspected_physical_duplicates: int = 0


class ExportFailure(RuntimeError):
    """Raised when an incomplete or invalid export must not be written."""


class Progress:
    def __init__(self, total: int) -> None:
        self.total = total
        self.completed = 0
        self.lock = threading.Lock()

    def tick(self) -> None:
        with self.lock:
            self.completed += 1
            if self.completed % 20 == 0 or self.completed == self.total:
                print(f"API progress: {self.completed}/{self.total}", flush=True)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Extract and validate team Seoul STREET_LIGHT rows. "
            "Defaults to a read-only dry-run."
        )
    )
    parser.add_argument(
        "--base-url",
        default="http://3.34.125.63",
        help="Team site base URL or complete infrastructure endpoint",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("rpa/safety/data/processed/team_seoul_street_lights.csv"),
        help="CSV path used only with --write",
    )
    parser.add_argument(
        "--expected-count",
        type=int,
        default=58_603,
        help="Required unique Seoul STREET_LIGHT count",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate in memory without creating a CSV (the default)",
    )
    mode.add_argument(
        "--write",
        action="store_true",
        help="Write the CSV only after all requests and validations succeed",
    )
    return parser.parse_args(argv)


def infrastructure_endpoint(base_url: str) -> str:
    raw = base_url.strip().rstrip("/")
    parsed = urlsplit(raw)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ExportFailure(f"Invalid --base-url: {base_url!r}")
    if parsed.path.rstrip("/").endswith(API_PATH):
        path = parsed.path.rstrip("/")
    else:
        path = parsed.path.rstrip("/") + API_PATH
    return urlunsplit((parsed.scheme, parsed.netloc, path, "", ""))


def generate_hex_grid() -> list[GridPoint]:
    """Cover the Seoul rectangle with overlapping radius-2km circles.

    A triangular/hexagonal covering uses horizontal spacing sqrt(3)*radius and
    vertical spacing 1.5*radius.  Boundary padding ensures that points on every
    edge are covered before API results are filtered back to the rectangle.
    """
    earth_meters_per_degree = 111_320.0
    vertical_step = (1.5 * RADIUS_METERS) / earth_meters_per_degree
    lat_padding = RADIUS_METERS / earth_meters_per_degree

    rows: list[GridPoint] = []
    latitude = SEOUL_MIN_LAT - lat_padding
    row_index = 0
    while latitude <= SEOUL_MAX_LAT + lat_padding + 1e-12:
        lon_meters_per_degree = earth_meters_per_degree * math.cos(math.radians(latitude))
        horizontal_step = (math.sqrt(3.0) * RADIUS_METERS) / lon_meters_per_degree
        lon_padding = RADIUS_METERS / lon_meters_per_degree
        longitude = SEOUL_MIN_LON - lon_padding
        if row_index % 2:
            longitude -= horizontal_step / 2.0
        while longitude <= SEOUL_MAX_LON + lon_padding + 1e-12:
            rows.append(GridPoint(round(latitude, 9), round(longitude, 9)))
            longitude += horizontal_step
        latitude += vertical_step
        row_index += 1
    return rows


def request_grid_point(endpoint: str, point: GridPoint) -> list[dict[str, Any]]:
    query = urlencode(
        {
            "lat": point.latitude,
            "lng": point.longitude,
            "radius": RADIUS_METERS,
            "features": "safety",
        }
    )
    url = f"{endpoint}?{query}"
    last_error: BaseException | None = None

    for attempt in range(1, MAX_ATTEMPTS + 1):
        time.sleep(REQUEST_DELAY_SECONDS)
        request = Request(
            url,
            headers={"Accept": "application/json", "User-Agent": "zipai-seoul-export/1.0"},
        )
        try:
            with urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                if response.status != 200:
                    raise ExportFailure(f"HTTP {response.status} from {url}")
                payload = json.load(response)
            if not isinstance(payload, dict) or payload.get("success") is not True:
                raise ExportFailure(f"Expected a successful JSON object from {url}")
            facilities = payload.get("facilities")
            if not isinstance(facilities, list):
                raise ExportFailure(f"Expected a facilities list from {url}")
            return [item for item in facilities if isinstance(item, dict)]
        except (HTTPError, URLError, TimeoutError, OSError, json.JSONDecodeError, ExportFailure) as exc:
            last_error = exc
            if attempt < MAX_ATTEMPTS:
                time.sleep(float(2 ** (attempt - 1)))

    raise ExportFailure(
        f"Request failed after {MAX_ATTEMPTS} attempts at "
        f"({point.latitude}, {point.longitude}): {last_error}"
    )


def is_in_seoul_rectangle(item: dict[str, Any]) -> bool:
    try:
        latitude = float(item["latitude"])
        longitude = float(item["longitude"])
    except (KeyError, TypeError, ValueError):
        return False
    return (
        math.isfinite(latitude)
        and math.isfinite(longitude)
        and SEOUL_MIN_LAT <= latitude <= SEOUL_MAX_LAT
        and SEOUL_MIN_LON <= longitude <= SEOUL_MAX_LON
    )


def collect_records(endpoint: str) -> tuple[list[dict[str, Any]], int]:
    grid = generate_hex_grid()
    print(
        f"Querying {len(grid)} Seoul grid points "
        f"(radius={RADIUS_METERS}, workers={MAX_WORKERS})...",
        flush=True,
    )
    progress = Progress(len(grid))
    failures: list[str] = []
    by_source_id: dict[str, dict[str, Any]] = {}
    conflicting_duplicates = 0

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {executor.submit(request_grid_point, endpoint, point): point for point in grid}
        for future in as_completed(futures):
            point = futures[future]
            try:
                items = future.result()
                for item in items:
                    if item.get("facilityType") != "STREET_LIGHT":
                        continue
                    source_id = item.get("sourceId")
                    if not isinstance(source_id, str) or not source_id.startswith(SOURCE_ID_PREFIX):
                        continue
                    if not is_in_seoul_rectangle(item):
                        continue
                    previous = by_source_id.get(source_id)
                    if previous is None:
                        by_source_id[source_id] = item
                    elif record_identity(previous) != record_identity(item):
                        conflicting_duplicates += 1
            except BaseException as exc:  # preserve all failures until the pool drains
                failures.append(f"{point.latitude},{point.longitude}: {exc}")
            finally:
                progress.tick()

    if failures:
        preview = "\n".join(f"  - {failure}" for failure in failures[:10])
        raise ExportFailure(
            f"{len(failures)} grid request(s) failed; CSV generation is forbidden.\n{preview}"
        )
    return sorted(by_source_id.values(), key=lambda item: str(item["sourceId"])), conflicting_duplicates


def record_identity(item: dict[str, Any]) -> tuple[str, ...]:
    return tuple(
        str(item.get(field, ""))
        for field in (
            "sourceId",
            "name",
            "facilityType",
            "address",
            "latitude",
            "longitude",
            "sourceName",
            "sourceUpdatedAt",
            "purpose",
        )
    )


def district_for(item: dict[str, Any]) -> str | None:
    source_name = str(item.get("sourceName", ""))
    matches = [district for district in EXPECTED_DISTRICT_COUNTS if district in source_name]
    return matches[0] if len(matches) == 1 else None


def is_missing(item: dict[str, Any], field: str) -> bool:
    value = item.get(field)
    if value is None:
        return True
    if isinstance(value, str) and not value.strip():
        return True
    if field in {"latitude", "longitude"}:
        try:
            return not math.isfinite(float(value))
        except (TypeError, ValueError):
            return True
    return False


def validate_records(
    records: list[dict[str, Any]], expected_count: int, conflicting_duplicates: int
) -> tuple[dict[str, int], int, int, int]:
    invalid_source_ids = sum(
        1 for item in records if not SOURCE_ID_RE.fullmatch(str(item.get("sourceId", "")))
    )
    rows_with_missing_fields = sum(
        1 for item in records if any(is_missing(item, field) for field in REQUIRED_API_FIELDS)
    )
    unknown_districts = 0
    district_counts = {district: 0 for district in EXPECTED_DISTRICT_COUNTS}
    for item in records:
        district = district_for(item)
        if district is None:
            unknown_districts += 1
        else:
            district_counts[district] += 1

    errors: list[str] = []
    if len(records) != expected_count:
        errors.append(f"unique count {len(records)} != expected {expected_count}")
    if invalid_source_ids:
        errors.append(f"invalid sourceId rows: {invalid_source_ids}")
    if rows_with_missing_fields:
        errors.append(f"rows with required-field omissions: {rows_with_missing_fields}")
    if unknown_districts:
        errors.append(f"rows not mapped to exactly one expected district: {unknown_districts}")
    if conflicting_duplicates:
        errors.append(f"conflicting duplicate sourceId payloads: {conflicting_duplicates}")
    for district, expected in EXPECTED_DISTRICT_COUNTS.items():
        actual = district_counts[district]
        if actual != expected:
            errors.append(f"{district} count {actual} != expected {expected}")

    if errors:
        raise ExportFailure("Validation failed: " + "; ".join(errors))
    return district_counts, invalid_source_ids, rows_with_missing_fields, unknown_districts


def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6_371_000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    )
    return radius * 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


def validate_yeoksam(records: list[dict[str, Any]]) -> dict[str, int]:
    results: dict[str, int] = {}
    errors: list[str] = []
    coordinates = [(float(item["latitude"]), float(item["longitude"])) for item in records]
    for label, center_lat, center_lon, expected in YEOKSAM_CHECKS:
        count = sum(
            1
            for latitude, longitude in coordinates
            if haversine_meters(center_lat, center_lon, latitude, longitude) <= 500.0
        )
        results[label] = count
        if count != expected:
            errors.append(f"{label} 500m count {count} != expected {expected}")
    if errors:
        raise ExportFailure("Yeoksam validation failed: " + "; ".join(errors))
    return results


def normalize_text(value: Any) -> str:
    return " ".join(str(value or "").strip().split()).casefold()


def normalized_coordinate(value: Any) -> str:
    try:
        return f"{float(value):.8f}"
    except (TypeError, ValueError):
        return ""


def physical_key_from_api(item: dict[str, Any]) -> tuple[str, str, str, str, str]:
    return (
        normalize_text(item.get("facilityType")),
        normalize_text(item.get("name")),
        normalize_text(item.get("address")),
        normalized_coordinate(item.get("latitude")),
        normalized_coordinate(item.get("longitude")),
    )


def physical_key_from_csv(row: dict[str, str]) -> tuple[str, str, str, str, str]:
    return (
        normalize_text(row.get("facility_type")),
        normalize_text(row.get("name")),
        normalize_text(row.get("address")),
        normalized_coordinate(row.get("latitude")),
        normalized_coordinate(row.get("longitude")),
    )


def address_is_seoul(value: Any) -> bool:
    address = normalize_text(value).replace(" ", "")
    return address.startswith("서울특별시") or address.startswith("서울시") or address.startswith("서울")


def coordinate_is_seoul(latitude: Any, longitude: Any) -> bool:
    try:
        lat = float(latitude)
        lon = float(longitude)
    except (TypeError, ValueError):
        return False
    return SEOUL_MIN_LAT <= lat <= SEOUL_MAX_LAT and SEOUL_MIN_LON <= lon <= SEOUL_MAX_LON


def locate_existing_csv(repo_root: Path) -> Path | None:
    candidates = (
        repo_root / "rpa" / "safety" / "data" / "processed" / "street_light_processed.csv",
        repo_root / "rpa" / "safety" / "data" / "processed" / "street_light_tidb_import.csv",
    )
    return next((candidate for candidate in candidates if candidate.is_file()), None)


def compare_existing(records: list[dict[str, Any]], repo_root: Path) -> ExistingDataComparison:
    path = locate_existing_csv(repo_root)
    result = ExistingDataComparison(path=path)
    if path is None:
        return result

    new_source_ids = {str(item["sourceId"]) for item in records}
    new_physical_keys = {physical_key_from_api(item) for item in records}
    collided_ids: set[str] = set()
    collided_physical_keys: set[tuple[str, str, str, str, str]] = set()

    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        required_columns = {
            "source_id",
            "name",
            "facility_type",
            "address",
            "latitude",
            "longitude",
        }
        if reader.fieldnames is None or not required_columns.issubset(reader.fieldnames):
            raise ExportFailure(f"Existing CSV has unexpected columns: {path}")
        for row in reader:
            if normalize_text(row.get("facility_type")) != "street_light":
                continue
            result.row_count += 1
            source_id = str(row.get("source_id", "")).strip()
            if source_id in new_source_ids:
                collided_ids.add(source_id)
            if address_is_seoul(row.get("address")):
                result.seoul_address_rows += 1
            if coordinate_is_seoul(row.get("latitude"), row.get("longitude")):
                result.seoul_coordinate_rows += 1
            physical_key = physical_key_from_csv(row)
            if physical_key in new_physical_keys:
                collided_physical_keys.add(physical_key)

    result.source_id_collisions = len(collided_ids)
    result.suspected_physical_duplicates = len(collided_physical_keys)
    return result


def csv_row(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "source_id": item["sourceId"],
        "name": item["name"],
        "facility_type": "STREET_LIGHT",
        "address": item["address"],
        "latitude": item["latitude"],
        "longitude": item["longitude"],
        "source": item["sourceName"],
        "source_updated_at": item["sourceUpdatedAt"],
        "purpose": item.get("purpose") or "",
        "camera_count": 1,
    }


def write_csv_atomically(records: Iterable[dict[str, Any]], output: Path) -> None:
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8-sig",
            newline="",
            prefix=f".{output.name}.",
            suffix=".tmp",
            dir=output.parent,
            delete=False,
        ) as handle:
            temporary_path = Path(handle.name)
            writer = csv.DictWriter(handle, fieldnames=CSV_COLUMNS)
            writer.writeheader()
            for item in records:
                writer.writerow(csv_row(item))
        os.replace(temporary_path, output)
    except BaseException:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
        raise


def print_summary(
    *,
    mode: str,
    records: list[dict[str, Any]],
    district_counts: dict[str, int],
    invalid_source_ids: int,
    missing_fields: int,
    conflicting_duplicates: int,
    existing: ExistingDataComparison,
    yeoksam: dict[str, int],
    output: Path,
) -> None:
    print("\n=== Seoul STREET_LIGHT validation summary ===")
    print(f"mode: {mode}")
    print(f"unique_street_lights: {len(records)}")
    for district in EXPECTED_DISTRICT_COUNTS:
        print(f"district_{district}: {district_counts[district]}")
    print(f"invalid_source_ids: {invalid_source_ids}")
    print(f"rows_with_missing_required_fields: {missing_fields}")
    print(f"conflicting_duplicate_source_ids: {conflicting_duplicates}")
    if existing.path is None:
        print("existing_csv: not found")
    else:
        print(f"existing_csv: {existing.path}")
        print(f"existing_street_light_rows: {existing.row_count}")
        print(f"source_id_collisions: {existing.source_id_collisions}")
        print(f"existing_seoul_address_rows: {existing.seoul_address_rows}")
        print(f"existing_seoul_coordinate_rows: {existing.seoul_coordinate_rows}")
        print(f"suspected_physical_duplicates: {existing.suspected_physical_duplicates}")
    print(f"yeoksam_team_coordinate_500m: {yeoksam['team_coordinate']}")
    print(f"yeoksam_personal_coordinate_500m: {yeoksam['personal_coordinate']}")
    if mode == "write":
        print(f"csv_written: {output.resolve()}")
    else:
        print("csv_written: no (dry-run)")


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    mode = "write" if args.write else "dry-run"
    repo_root = Path(__file__).resolve().parents[1]

    try:
        endpoint = infrastructure_endpoint(args.base_url)
        records, conflicting_duplicates = collect_records(endpoint)
        district_counts, invalid_source_ids, missing_fields, _ = validate_records(
            records, args.expected_count, conflicting_duplicates
        )
        yeoksam = validate_yeoksam(records)
        existing = compare_existing(records, repo_root)

        # This branch is unreachable unless every network request and validation
        # above completed successfully.
        if args.write:
            write_csv_atomically(records, args.output)

        print_summary(
            mode=mode,
            records=records,
            district_counts=district_counts,
            invalid_source_ids=invalid_source_ids,
            missing_fields=missing_fields,
            conflicting_duplicates=conflicting_duplicates,
            existing=existing,
            yeoksam=yeoksam,
            output=args.output,
        )
        return 0
    except (ExportFailure, OSError, csv.Error) as exc:
        print(f"ERROR: {exc}", file=sys.stderr, flush=True)
        print("No CSV was created by this run.", file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
