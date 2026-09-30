#!/usr/bin/env python3
import argparse
import csv
from pathlib import Path

FIELDS = [
    "source_id", "name", "facility_type", "address",
    "latitude", "longitude", "purpose", "camera_count",
    "source", "source_updated_at"
]

def coordinate_reason(row):
    lat_text = (row.get("latitude") or "").strip()
    lng_text = (row.get("longitude") or "").strip()

    if not lat_text and not lng_text:
        return "missing latitude and longitude"
    if not lat_text:
        return "missing latitude"
    if not lng_text:
        return "missing longitude"

    try:
        lat = float(lat_text)
        lng = float(lng_text)
    except ValueError:
        return "latitude/longitude is not numeric"

    if not (-90 <= lat <= 90):
        return "latitude is outside WGS84 range"
    if not (-180 <= lng <= 180):
        return "longitude is outside WGS84 range"
    return None

def main():
    parser = argparse.ArgumentParser(
        description="Split unusable Safety rows into a rejected-data pool without touching the DB."
    )
    parser.add_argument(
        "input",
        nargs="?",
        default="data/processed/safe_infrastructure_all_available_import.csv"
    )
    parser.add_argument(
        "--output",
        default="data/rejected/rejected_safety_infrastructure.csv"
    )
    args = parser.parse_args()

    source = Path(args.input)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    read = 0
    rejected = 0

    with source.open("r", encoding="utf-8-sig", newline="") as src, \
         output.open("w", encoding="utf-8", newline="") as dst:
        reader = csv.DictReader(src)
        missing = [field for field in FIELDS if field not in (reader.fieldnames or [])]
        if missing:
            raise SystemExit("Missing CSV columns: " + ", ".join(missing))

        writer = csv.DictWriter(
            dst,
            fieldnames=["record_number", *FIELDS, "reject_reason"],
            quoting=csv.QUOTE_MINIMAL
        )
        writer.writeheader()

        for record_number, row in enumerate(reader, start=1):
            read += 1
            reason = coordinate_reason(row)
            if reason is None:
                continue

            rejected += 1
            writer.writerow({
                "record_number": record_number,
                **{field: row.get(field, "") for field in FIELDS},
                "reject_reason": reason
            })

    print(f"CSV logical rows read: {read}")
    print(f"Rejected/pool rows: {rejected}")
    print(f"Service-usable coordinate rows: {read - rejected}")
    print(f"Rejected pool: {output.resolve()}")

if __name__ == "__main__":
    main()
