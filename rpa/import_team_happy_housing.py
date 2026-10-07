#!/usr/bin/env python3
"""Import the final team site's public Happy Housing snapshot into MySQL/TiDB.

The importer is deliberately read-only unless ``--apply`` is supplied. Team-side
auto-increment IDs are never inserted; notices are matched by ``pan_id`` and all
child rows are linked to the personal database's ``notice_id``.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Any, Iterable

try:
    import mysql.connector
except ImportError:  # Reported cleanly after argument/input validation.
    mysql = None
else:
    mysql = mysql.connector


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_INPUT_DIR = SCRIPT_DIR / "data" / "incoming" / "team-happy-housing"

NOTICE_FILE = "team_housing_notices.json"
NOTICE_RULE_FILE = "team_housing_notice_rules.json"
ELIGIBILITY_FILE = "team_eligibility_rules.json"
CRAWL_HISTORY_FILE = "team_crawl_history.json"


@dataclass
class ImportData:
    notices: list[dict[str, Any]]
    notice_rules: list[dict[str, Any]]
    eligibility_rules: list[dict[str, Any]]
    crawl_history: list[dict[str, Any]]


@dataclass
class ImportPlan:
    notice_insert: int = 0
    notice_update: int = 0
    notice_rule_insert: int = 0
    notice_rule_update: int = 0
    notice_rule_parent_missing: int = 0
    eligibility_insert: int = 0
    eligibility_update: int = 0
    crawl_history_insert: int = 0
    crawl_history_skip: int = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Import the extracted team Happy Housing public data safely."
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--dry-run",
        action="store_true",
        help="Run SELECT-only planning and roll back; never write to the database.",
    )
    mode.add_argument(
        "--apply",
        action="store_true",
        help="Explicitly perform all inserts/updates in one transaction.",
    )
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=DEFAULT_INPUT_DIR,
        help=f"Directory containing the four snapshot JSON files (default: {DEFAULT_INPUT_DIR}).",
    )
    args = parser.parse_args()
    if not args.dry_run and not args.apply:
        parser.error("no write mode selected: choose --dry-run or explicitly choose --apply")
    return args


def load_json_array(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as file:
        value = json.load(file)
    if not isinstance(value, list) or not all(isinstance(item, dict) for item in value):
        raise ValueError(f"{path.name} must contain a JSON array of objects")
    return value


def load_import_data(input_dir: Path) -> ImportData:
    return ImportData(
        notices=load_json_array(input_dir / NOTICE_FILE),
        notice_rules=load_json_array(input_dir / NOTICE_RULE_FILE),
        eligibility_rules=load_json_array(input_dir / ELIGIBILITY_FILE),
        crawl_history=load_json_array(input_dir / CRAWL_HISTORY_FILE),
    )


def require(item: dict[str, Any], field: str, source: str) -> Any:
    value = item.get(field)
    if value is None or (isinstance(value, str) and not value.strip()):
        raise ValueError(f"{source}: required field {field!r} is missing")
    return value


def source_pdf_hash(source_pdf: str) -> str:
    """Match happy_housing_crawler.py's SHA-256 key generation exactly."""
    return hashlib.sha256(source_pdf.encode("utf-8")).hexdigest()


def normalized_rule_json(value: Any) -> str:
    if isinstance(value, str):
        json.loads(value)
        return value
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def validate_import_data(data: ImportData) -> None:
    if len(data.crawl_history) > 1:
        raise ValueError("team_crawl_history.json may contain only the publicly exposed latest row")

    pan_ids: set[str] = set()
    for index, notice in enumerate(data.notices):
        pan_id = str(require(notice, "panId", f"notices[{index}]"))
        require(notice, "title", f"notices[{index}]")
        if pan_id in pan_ids:
            raise ValueError(f"duplicate notice panId: {pan_id}")
        pan_ids.add(pan_id)

    rule_keys: set[tuple[str, str, str]] = set()
    for index, rule in enumerate(data.notice_rules):
        source = f"notice_rules[{index}]"
        pan_id = str(require(rule, "panId", source))
        applicant_type = str(require(rule, "applicantType", source))
        source_pdf = str(require(rule, "sourcePdf", source))
        if pan_id not in pan_ids:
            raise ValueError(f"{source}: parent panId is not present in notice input")
        normalized_rule_json(require(rule, "ruleJson", source))
        key = (pan_id, applicant_type, source_pdf_hash(source_pdf))
        if key in rule_keys:
            raise ValueError(f"duplicate notice rule key at {source}")
        rule_keys.add(key)

    eligibility_keys: set[tuple[str, str, str | None]] = set()
    for index, rule in enumerate(data.eligibility_rules):
        source = f"eligibility_rules[{index}]"
        applicant_type = str(require(rule, "applicantType", source))
        require(rule, "ruleYear", source)
        effective_from = str(require(rule, "effectiveFrom", source))
        effective_to = rule.get("effectiveTo")
        key = (applicant_type, effective_from, None if effective_to is None else str(effective_to))
        if key in eligibility_keys:
            raise ValueError(f"duplicate eligibility rule key at {source}")
        eligibility_keys.add(key)

    for index, history in enumerate(data.crawl_history):
        source = f"crawl_history[{index}]"
        require(history, "crawlerName", source)
        require(history, "startedAt", source)
        require(history, "status", source)


def get_db_config() -> dict[str, Any]:
    names = [
        "ZIPAI_DB_HOST",
        "ZIPAI_DB_NAME",
        "ZIPAI_DB_USER",
        "ZIPAI_DB_PASSWORD",
    ]
    missing = [name for name in names if not os.getenv(name, "").strip()]
    if missing:
        raise ValueError("missing required database environment variables: " + ", ".join(missing))
    try:
        port = int(os.getenv("ZIPAI_DB_PORT", "3306"))
    except ValueError as error:
        raise ValueError("ZIPAI_DB_PORT must be an integer") from error
    return {
        "host": os.environ["ZIPAI_DB_HOST"].strip(),
        "port": port,
        "database": os.environ["ZIPAI_DB_NAME"].strip(),
        "user": os.environ["ZIPAI_DB_USER"].strip(),
        "password": os.environ["ZIPAI_DB_PASSWORD"],
    }


def connect(config: dict[str, Any]):
    if mysql is None:
        raise RuntimeError("mysql-connector-python is required")
    return mysql.connect(
        host=config["host"],
        port=config["port"],
        database=config["database"],
        user=config["user"],
        password=config["password"],
        charset="utf8mb4",
        autocommit=False,
    )


def to_date(value: Any) -> date | None:
    if value is None or str(value).strip() == "":
        return None
    return date.fromisoformat(str(value).strip())


def to_datetime(value: Any) -> datetime | None:
    if value is None or str(value).strip() == "":
        return None
    parsed = datetime.fromisoformat(str(value).strip().replace("Z", "+00:00"))
    return parsed.replace(tzinfo=None)


def chunks(values: list[str], size: int = 500) -> Iterable[list[str]]:
    for start in range(0, len(values), size):
        yield values[start : start + size]


def select_notice_ids(cursor, pan_ids: Iterable[str]) -> dict[str, int]:
    values = list(dict.fromkeys(pan_ids))
    result: dict[str, int] = {}
    for batch in chunks(values):
        if not batch:
            continue
        placeholders = ",".join(["%s"] * len(batch))
        cursor.execute(
            f"SELECT notice_id, pan_id FROM housing_notice WHERE pan_id IN ({placeholders})",
            tuple(batch),
        )
        result.update({str(pan_id): int(notice_id) for notice_id, pan_id in cursor.fetchall()})
    return result


def eligibility_key(rule: dict[str, Any]) -> tuple[str, date, date | None]:
    return (
        str(rule["applicantType"]),
        to_date(rule["effectiveFrom"]),
        to_date(rule.get("effectiveTo")),
    )


def crawl_key(history: dict[str, Any]) -> tuple[str, datetime, datetime | None, str]:
    return (
        str(history["crawlerName"]),
        to_datetime(history["startedAt"]),
        to_datetime(history.get("finishedAt")),
        str(history["status"]),
    )


def build_plan(cursor, data: ImportData) -> ImportPlan:
    plan = ImportPlan()
    input_pan_ids = [str(notice["panId"]) for notice in data.notices]
    existing_notice_ids = select_notice_ids(cursor, input_pan_ids)
    plan.notice_update = len(existing_notice_ids)
    plan.notice_insert = len(data.notices) - plan.notice_update

    for rule in data.notice_rules:
        pan_id = str(rule["panId"])
        notice_id = existing_notice_ids.get(pan_id)
        if notice_id is None:
            if pan_id in input_pan_ids:
                plan.notice_rule_insert += 1
            else:
                plan.notice_rule_parent_missing += 1
            continue
        rule_hash = source_pdf_hash(str(rule["sourcePdf"]))
        cursor.execute(
            """
            SELECT rule_id FROM housing_notice_rule
             WHERE notice_id=%s AND applicant_type=%s AND source_pdf_hash=%s
            """,
            (notice_id, str(rule["applicantType"]), rule_hash),
        )
        if cursor.fetchone():
            plan.notice_rule_update += 1
        else:
            plan.notice_rule_insert += 1

    for rule in data.eligibility_rules:
        applicant_type, effective_from, effective_to = eligibility_key(rule)
        cursor.execute(
            """
            SELECT rule_id FROM eligibility_rule
             WHERE applicant_type=%s AND effective_from=%s AND effective_to <=> %s
             ORDER BY rule_id DESC LIMIT 1
            """,
            (applicant_type, effective_from, effective_to),
        )
        if cursor.fetchone():
            plan.eligibility_update += 1
        else:
            plan.eligibility_insert += 1

    for history in data.crawl_history:
        crawler_name, started_at, finished_at, status = crawl_key(history)
        cursor.execute(
            """
            SELECT crawl_id FROM crawl_history
             WHERE crawler_name=%s AND started_at=%s
               AND finished_at <=> %s AND status=%s
             ORDER BY crawl_id DESC LIMIT 1
            """,
            (crawler_name, started_at, finished_at, status),
        )
        if cursor.fetchone():
            plan.crawl_history_skip += 1
        else:
            plan.crawl_history_insert += 1
    return plan


def upsert_notices(cursor, notices: list[dict[str, Any]]) -> dict[str, int]:
    sql = """
        INSERT INTO housing_notice (
            pan_id, source, title, region, notice_date, posting_date, closing_date,
            status, housing_type, pdf_file_id, pdf_file_name, hwpx_file_id,
            hwpx_file_name, detail_endpoint, crawled_at
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        ON DUPLICATE KEY UPDATE
            source=VALUES(source), title=VALUES(title), region=VALUES(region),
            notice_date=VALUES(notice_date), posting_date=VALUES(posting_date),
            closing_date=VALUES(closing_date), status=VALUES(status),
            housing_type=VALUES(housing_type), pdf_file_id=VALUES(pdf_file_id),
            pdf_file_name=VALUES(pdf_file_name), hwpx_file_id=VALUES(hwpx_file_id),
            hwpx_file_name=VALUES(hwpx_file_name), detail_endpoint=VALUES(detail_endpoint),
            crawled_at=VALUES(crawled_at)
    """
    for notice in notices:
        cursor.execute(
            sql,
            (
                str(notice["panId"]),
                notice.get("source"),
                notice["title"],
                notice.get("region"),
                to_date(notice.get("noticeDate")),
                to_date(notice.get("postingDate")),
                to_date(notice.get("closingDate")),
                notice.get("status"),
                notice.get("housingType"),
                notice.get("pdfFileId"),
                notice.get("pdfFileName"),
                notice.get("hwpxFileId"),
                notice.get("hwpxFileName"),
                notice.get("detailEndpoint"),
                to_datetime(notice.get("crawledAt")),
            ),
        )
    return select_notice_ids(cursor, (str(notice["panId"]) for notice in notices))


def upsert_notice_rules(
    cursor,
    rules: list[dict[str, Any]],
    notice_ids_by_pan: dict[str, int],
) -> None:
    for rule in rules:
        pan_id = str(rule["panId"])
        notice_id = notice_ids_by_pan.get(pan_id)
        if notice_id is None:
            raise ValueError(f"local parent notice was not found for panId {pan_id}")
        source_pdf = str(rule["sourcePdf"])
        rule_hash = source_pdf_hash(source_pdf)
        rule_json = normalized_rule_json(rule["ruleJson"])
        updated_at = to_datetime(rule.get("updatedAt"))
        cursor.execute(
            """
            SELECT rule_id FROM housing_notice_rule
             WHERE notice_id=%s AND applicant_type=%s AND source_pdf_hash=%s
             ORDER BY rule_id DESC LIMIT 1
            """,
            (notice_id, str(rule["applicantType"]), rule_hash),
        )
        existing = cursor.fetchone()
        if existing:
            cursor.execute(
                """
                UPDATE housing_notice_rule
                   SET pan_id=%s, source_pdf=%s, validation_status=%s,
                       rule_json=%s, updated_at=COALESCE(%s, updated_at)
                 WHERE rule_id=%s
                """,
                (
                    pan_id,
                    source_pdf,
                    rule.get("validationStatus"),
                    rule_json,
                    updated_at,
                    existing[0],
                ),
            )
        else:
            cursor.execute(
                """
                INSERT INTO housing_notice_rule (
                    notice_id, pan_id, applicant_type, source_pdf, source_pdf_hash,
                    validation_status, rule_json, updated_at
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,COALESCE(%s,CURRENT_TIMESTAMP))
                """,
                (
                    notice_id,
                    pan_id,
                    str(rule["applicantType"]),
                    source_pdf,
                    rule_hash,
                    rule.get("validationStatus"),
                    rule_json,
                    updated_at,
                ),
            )


def upsert_eligibility_rules(cursor, rules: list[dict[str, Any]]) -> None:
    for rule in rules:
        applicant_type, effective_from, effective_to = eligibility_key(rule)
        cursor.execute(
            """
            SELECT rule_id FROM eligibility_rule
             WHERE applicant_type=%s AND effective_from=%s AND effective_to <=> %s
             ORDER BY rule_id DESC LIMIT 1
            """,
            (applicant_type, effective_from, effective_to),
        )
        existing = cursor.fetchone()
        public_values = (
            int(rule["ruleYear"]),
            rule.get("minAge"),
            rule.get("maxAge"),
            rule.get("incomeLimit"),
            rule.get("assetLimit"),
            rule.get("carLimit"),
        )
        if existing:
            cursor.execute(
                """
                UPDATE eligibility_rule
                   SET rule_year=%s, min_age=%s, max_age=%s, income_limit=%s,
                       asset_limit=%s, car_limit=%s
                 WHERE rule_id=%s
                """,
                public_values + (existing[0],),
            )
        else:
            # Internal boolean/status columns are intentionally omitted so the
            # migration-defined database defaults apply; no values are invented.
            cursor.execute(
                """
                INSERT INTO eligibility_rule (
                    applicant_type, rule_year, min_age, max_age, income_limit,
                    asset_limit, car_limit, effective_from, effective_to
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
                """,
                (applicant_type,) + public_values + (effective_from, effective_to),
            )


def insert_crawl_history_if_missing(cursor, rows: list[dict[str, Any]]) -> None:
    for history in rows:
        crawler_name, started_at, finished_at, status = crawl_key(history)
        cursor.execute(
            """
            SELECT crawl_id FROM crawl_history
             WHERE crawler_name=%s AND started_at=%s
               AND finished_at <=> %s AND status=%s
             ORDER BY crawl_id DESC LIMIT 1
            """,
            (crawler_name, started_at, finished_at, status),
        )
        if cursor.fetchone():
            continue
        cursor.execute(
            """
            INSERT INTO crawl_history (
                crawler_name, started_at, finished_at, status, notice_count,
                rule_count, parsed_rule_count, validation_rule_count, error_message
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
            """,
            (
                crawler_name,
                started_at,
                finished_at,
                status,
                history.get("noticeCount", 0),
                history.get("ruleCount", 0),
                history.get("parsedRuleCount", 0),
                history.get("validationRuleCount", 0),
                history.get("errorMessage"),
            ),
        )


def print_plan(plan: ImportPlan, mode: str) -> None:
    print(f"mode: {mode}")
    print("housing_notice:")
    print(f"  insert planned: {plan.notice_insert}")
    print(f"  update planned: {plan.notice_update}")
    print("housing_notice_rule:")
    print(f"  insert planned: {plan.notice_rule_insert}")
    print(f"  update planned: {plan.notice_rule_update}")
    print(f"  parent missing: {plan.notice_rule_parent_missing}")
    print("eligibility_rule:")
    print(f"  insert planned: {plan.eligibility_insert}")
    print(f"  update planned: {plan.eligibility_update}")
    print("crawl_history:")
    print(f"  insert planned: {plan.crawl_history_insert}")
    print(f"  skip planned: {plan.crawl_history_skip}")


def main() -> int:
    args = parse_args()
    data = load_import_data(args.input_dir.resolve())
    validate_import_data(data)
    config = get_db_config()

    connection = None
    cursor = None
    try:
        connection = connect(config)
        cursor = connection.cursor()
        plan = build_plan(cursor, data)
        print_plan(plan, "DRY-RUN" if args.dry_run else "APPLY")

        if args.dry_run:
            connection.rollback()
            print("dry-run complete: SELECT only; transaction rolled back")
            return 0

        notice_ids_by_pan = upsert_notices(cursor, data.notices)
        upsert_notice_rules(cursor, data.notice_rules, notice_ids_by_pan)
        upsert_eligibility_rules(cursor, data.eligibility_rules)
        insert_crawl_history_if_missing(cursor, data.crawl_history)
        connection.commit()
        print("apply complete: transaction committed")
        return 0
    except Exception as error:
        if connection is not None:
            connection.rollback()
        print(f"[ERROR] import failed and was rolled back ({type(error).__name__})", file=sys.stderr)
        return 1
    finally:
        if cursor is not None:
            cursor.close()
        if connection is not None:
            connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
