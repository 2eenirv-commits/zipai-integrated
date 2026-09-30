import json
import math
import os
import sys
import time
from pathlib import Path
from urllib.parse import unquote

import pandas as pd
import requests


ENDPOINT = "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api"
RAW_DIR = Path("rpa/safety/data/raw")
OUTPUT_CSV = RAW_DIR / "전국보안등정보표준데이터_api.csv"
CHECKPOINT = RAW_DIR / "street_light_download_checkpoint.json"
NUM_OF_ROWS = 1000
REQUEST_TIMEOUT_SECONDS = 20
MAX_RETRIES = 3
RETRY_SLEEP_SECONDS = 2
PAGE_DELAY_SECONDS = 0.2
PROGRESS_EVERY_PAGES = 10


def main():
    raw_api_key = os.getenv("DATA_GO_KR_API_KEY")
    if not raw_api_key:
        print("DATA_GO_KR_API_KEY environment variable is required.", file=sys.stderr)
        raise SystemExit(1)
    api_key = unquote(raw_api_key)

    RAW_DIR.mkdir(parents=True, exist_ok=True)
    checkpoint = load_checkpoint()
    start_page = 1
    rows_written = 0
    total_count = None

    if checkpoint:
        validate_resume_state(checkpoint)
        start_page = int(checkpoint["last_completed_page"]) + 1
        rows_written = int(checkpoint["rows_written"])
        total_count = checkpoint.get("total_count")
        print(f"Resuming from page {start_page}. Rows already written: {rows_written}")

    if start_page == 1 and OUTPUT_CSV.exists():
        print(f"{OUTPUT_CSV} already exists without a checkpoint. Move or delete it before starting a new download.", file=sys.stderr)
        raise SystemExit(1)

    first_payload = fetch_page(api_key, start_page)
    body = first_payload.get("body", {})
    total_count = int(body.get("totalCount", total_count or 0))
    total_pages = math.ceil(total_count / NUM_OF_ROWS)

    if start_page > total_pages:
        print("Checkpoint indicates the download is already complete.")
        verify_completed_file(total_count, total_pages)
        return

    written = append_page_items(first_payload, include_header=start_page == 1)
    rows_written += written
    save_checkpoint(start_page, rows_written, total_count, total_pages)
    print_progress(start_page, total_pages, rows_written, total_count)

    for page_no in range(start_page + 1, total_pages + 1):
        time.sleep(PAGE_DELAY_SECONDS)
        payload = fetch_page(api_key, page_no)
        written = append_page_items(payload, include_header=False)
        rows_written += written
        save_checkpoint(page_no, rows_written, total_count, total_pages)
        if page_no % PROGRESS_EVERY_PAGES == 0 or page_no == total_pages:
            print_progress(page_no, total_pages, rows_written, total_count)

    verify_completed_file(total_count, total_pages)


def fetch_page(api_key, page_no):
    params = {
        "serviceKey": api_key,
        "pageNo": page_no,
        "numOfRows": NUM_OF_ROWS,
        "type": "json",
    }
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            response = requests.get(ENDPOINT, params=params, timeout=REQUEST_TIMEOUT_SECONDS)
            response.raise_for_status()
            payload = response.json()
            header = payload.get("header", {})
            result_code = str(header.get("resultCode", ""))
            result_msg = header.get("resultMsg", "")
            if result_code != "00":
                raise RuntimeError(f"API resultCode={result_code}, resultMsg={result_msg}")
            return payload
        except Exception as error:
            last_error = error
            if attempt < MAX_RETRIES:
                print(f"page {page_no} failed attempt {attempt}/{MAX_RETRIES}: {error}")
                time.sleep(RETRY_SLEEP_SECONDS)
            else:
                print(f"page {page_no} failed after {MAX_RETRIES} attempts: {last_error}", file=sys.stderr)
                raise SystemExit(1)


def append_page_items(payload, include_header):
    body = payload.get("body", {})
    items = body.get("items", [])
    if isinstance(items, dict):
        items = items.get("item", [])
    if isinstance(items, dict):
        items = [items]
    if items is None:
        items = []
    frame = pd.DataFrame(items)
    frame.to_csv(
        OUTPUT_CSV,
        mode="w" if include_header else "a",
        header=include_header,
        index=False,
        encoding="utf-8-sig",
    )
    return len(frame)


def load_checkpoint():
    if not CHECKPOINT.exists():
        return None
    with CHECKPOINT.open("r", encoding="utf-8") as file:
        return json.load(file)


def save_checkpoint(last_completed_page, rows_written, total_count, total_pages):
    data = {
        "last_completed_page": last_completed_page,
        "rows_written": rows_written,
        "total_count": total_count,
        "total_pages": total_pages,
        "output_csv": str(OUTPUT_CSV),
    }
    with CHECKPOINT.open("w", encoding="utf-8") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)


def validate_resume_state(checkpoint):
    if not OUTPUT_CSV.exists():
        print(f"Checkpoint exists but {OUTPUT_CSV} is missing. Refusing to resume.", file=sys.stderr)
        raise SystemExit(1)
    csv_rows = count_csv_rows()
    expected_rows = int(checkpoint["rows_written"])
    if csv_rows != expected_rows:
        print(
            f"Checkpoint/CSV mismatch. checkpoint rows={expected_rows}, csv rows={csv_rows}. "
            "Resolve this manually before resuming.",
            file=sys.stderr,
        )
        raise SystemExit(1)


def count_csv_rows():
    if not OUTPUT_CSV.exists():
        return 0
    rows = 0
    for chunk in pd.read_csv(OUTPUT_CSV, encoding="utf-8-sig", chunksize=100_000):
        rows += len(chunk)
    return rows


def print_progress(page_no, total_pages, rows_written, total_count):
    print(f"page {page_no} / {total_pages}")
    print(f"rows written: {rows_written} / {total_count}")


def verify_completed_file(total_count, total_pages):
    csv_rows = count_csv_rows()
    size = OUTPUT_CSV.stat().st_size if OUTPUT_CSV.exists() else 0
    print("Download completed.")
    print(f"API totalCount: {total_count}")
    print(f"CSV rows: {csv_rows}")
    print(f"Pages: {total_pages}")
    print(f"File size: {size} bytes")
    if csv_rows != total_count:
        print(f"WARNING: CSV rows ({csv_rows}) != API totalCount ({total_count})")
    if csv_rows > 0:
        head = pd.read_csv(OUTPUT_CSV, encoding="utf-8-sig", nrows=3)
        print("Columns:")
        print(list(head.columns))
        print("First 3 rows:")
        print(head.to_string(index=False))
        tail = last_rows(3)
        print("Last 3 rows:")
        print(tail.to_string(index=False))


def last_rows(count):
    tail = None
    for chunk in pd.read_csv(OUTPUT_CSV, encoding="utf-8-sig", chunksize=100_000):
        tail = chunk if tail is None else pd.concat([tail, chunk], ignore_index=True).tail(count)
    return tail if tail is not None else pd.DataFrame()


if __name__ == "__main__":
    main()
