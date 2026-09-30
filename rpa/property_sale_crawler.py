"""ZipAI 매매 데이터 수집기.

두 가지 소스를 지원한다.

1) PROPERTY_SOURCE_MODE=html
   - 허용된/계약된 매물 사이트 HTML을 CSS selector로 수집
   - /api/properties/crawl-import 로 업로드
   - 실제 활성 매물용

2) PROPERTY_SOURCE_MODE=molit
   - 국토교통부 공공데이터포털 '아파트 매매 실거래가 상세 자료' API 사용
   - /api/properties/market-import 로 업로드
   - 최근 실거래 참고 데이터용(활성 매물로 취급하지 않음)

예:
  python property_sale_crawler.py --check
  python property_sale_crawler.py --upload
  python property_sale_crawler.py --upload --resume
  python property_sale_crawler.py --upload-only
  python property_sale_crawler.py --geocode-only
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import quote, unquote, urljoin

import requests
from bs4 import BeautifulSoup


def configure_utf8_output() -> None:
    """Keep Korean console and redirected log output consistently UTF-8."""
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            reconfigure(encoding="utf-8", errors="replace", line_buffering=True, write_through=True)


configure_utf8_output()

BASE_DIR = Path(__file__).resolve().parent
OUT = BASE_DIR / "data" / "processed" / "property_sale_processed.json"
RAW_OUT = BASE_DIR / "data" / "raw" / "property_sale_raw.json"
CHECKPOINT_OUT = BASE_DIR / "data" / "processed" / "property_sale_checkpoint.json"
UPLOAD_STATE_OUT = BASE_DIR / "data" / "processed" / "property_sale_upload_state.json"
GEOCODE_FAILURE_OUT = BASE_DIR / "data" / "processed" / "property_market_geocode_failures.json"
GEOCODE_STATE_OUT = BASE_DIR / "data" / "processed" / "property_market_geocode_state.json"

def load_project_env() -> None:
    """Load zipai/.env without overriding values already exported by the shell."""
    env_path = BASE_DIR.parent / ".env"
    if not env_path.exists():
        return
    try:
        from dotenv import load_dotenv
        load_dotenv(env_path, override=False)
        return
    except Exception:
        pass
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        name = name.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        if name and name not in os.environ:
            os.environ[name] = value


load_project_env()


def env(name: str, default: str = "") -> str:
    return os.getenv(name, default).strip()


MODE = env("PROPERTY_SOURCE_MODE", "html").lower()
BACKEND = env("ZIPAI_BASE_URL", "http://localhost:8080").rstrip("/")
TOKEN = env("PROPERTY_IMPORT_TOKEN")
TIMEOUT = max(5, min(int(env("PROPERTY_CRAWL_TIMEOUT_SECONDS", "20") or "20"), 120))
USER_AGENT = env("PROPERTY_CRAWL_USER_AGENT", "ZipAI-property-research/1.0")
IMPORT_BATCH_SIZE = max(50, min(int(env("PROPERTY_IMPORT_BATCH_SIZE", "500") or "500"), 2000))
UPLOAD_RETRIES = max(0, min(int(env("PROPERTY_UPLOAD_RETRIES", "3") or "3"), 10))
UPLOAD_RETRY_DELAY = max(0.5, min(float(env("PROPERTY_UPLOAD_RETRY_DELAY_SECONDS", "3") or "3"), 60.0))
MARKET_GEOCODE_LIMIT = max(0, min(int(env("MOLIT_GEOCODE_LIMIT", "200") or "200"), 2000))
MARKET_GEOCODE_DELAY = max(0.0, float(env("MOLIT_GEOCODE_DELAY_SECONDS", "0.25") or "0.25"))
MARKET_GEOCODE_TIMEOUT = max(1, min(int(env("MOLIT_GEOCODE_TIMEOUT_SECONDS", "5") or "5"), 30))
MARKET_GEOCODE_MAX_SECONDS = max(30, min(int(env("MOLIT_GEOCODE_MAX_SECONDS", "300") or "300"), 3600))
MARKET_GEOCODE_FAILURE_LIMIT = max(3, min(int(env("MOLIT_GEOCODE_FAILURE_LIMIT", "10") or "10"), 100))
MARKET_GEO_CACHE = BASE_DIR / "data" / "processed" / "property_market_geocode_cache.json"

# HTML listing source
START_URL = env("PROPERTY_CRAWL_START_URL")
CARD = env("PROPERTY_CRAWL_CARD_SELECTOR")
TITLE = env("PROPERTY_CRAWL_TITLE_SELECTOR")
ADDRESS = env("PROPERTY_CRAWL_ADDRESS_SELECTOR")
PRICE = env("PROPERTY_CRAWL_PRICE_SELECTOR")
LINK = env("PROPERTY_CRAWL_LINK_SELECTOR", "a") or "a"
IMAGE = env("PROPERTY_CRAWL_IMAGE_SELECTOR", "img") or "img"
AREA = env("PROPERTY_CRAWL_AREA_SELECTOR")
FLOOR = env("PROPERTY_CRAWL_FLOOR_SELECTOR")
BUILDING_TYPE = env("PROPERTY_CRAWL_BUILDING_TYPE_SELECTOR")
DESCRIPTION = env("PROPERTY_CRAWL_DESCRIPTION_SELECTOR")
NEXT_PAGE = env("PROPERTY_CRAWL_NEXT_PAGE_SELECTOR")
SOURCE_NAME = env("PROPERTY_CRAWL_SOURCE_NAME", "configured-source")
MAX_PAGES = max(1, min(int(env("PROPERTY_CRAWL_MAX_PAGES", "1") or "1"), 50))
REQUEST_DELAY = max(0.0, float(env("PROPERTY_CRAWL_DELAY_SECONDS", "0.8") or "0.8"))

# MOLIT official actual-transaction API
MOLIT_API_URL = "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade"
MOLIT_KEY = env("MOLIT_SERVICE_KEY")
MOLIT_KEY_FORMAT = env("MOLIT_SERVICE_KEY_FORMAT", "auto").lower()
MOLIT_LAWD_CD = env("MOLIT_LAWD_CD", "41117")  # 예: 수원시 영통구
MOLIT_REGION_SCOPE = env("MOLIT_REGION_SCOPE", "single").lower()
MOLIT_LAWD_CDS = env("MOLIT_LAWD_CDS")
MOLIT_DEAL_YMD = env("MOLIT_DEAL_YMD") or datetime.now().strftime("%Y%m")
MOLIT_ROWS = max(10, min(int(env("MOLIT_NUM_OF_ROWS", "1000") or "1000"), 5000))

# 국토교통부 실거래가 API의 LAWD_CD는 시군구 5자리 코드다.
# capital 범위는 서울 25개 자치구와 경기 47개 시군구를 포함한다.
CAPITAL_LAWD_CODES = {
    "11110": "서울 종로구", "11140": "서울 중구", "11170": "서울 용산구", "11200": "서울 성동구",
    "11215": "서울 광진구", "11230": "서울 동대문구", "11260": "서울 중랑구", "11290": "서울 성북구",
    "11305": "서울 강북구", "11320": "서울 도봉구", "11350": "서울 노원구", "11380": "서울 은평구",
    "11410": "서울 서대문구", "11440": "서울 마포구", "11470": "서울 양천구", "11500": "서울 강서구",
    "11530": "서울 구로구", "11545": "서울 금천구", "11560": "서울 영등포구", "11590": "서울 동작구",
    "11620": "서울 관악구", "11650": "서울 서초구", "11680": "서울 강남구", "11710": "서울 송파구",
    "11740": "서울 강동구",
    "41111": "경기 수원시 장안구", "41113": "경기 수원시 권선구", "41115": "경기 수원시 팔달구", "41117": "경기 수원시 영통구",
    "41131": "경기 성남시 수정구", "41133": "경기 성남시 중원구", "41135": "경기 성남시 분당구", "41150": "경기 의정부시",
    "41171": "경기 안양시 만안구", "41173": "경기 안양시 동안구", "41192": "경기 부천시 원미구", "41194": "경기 부천시 소사구",
    "41196": "경기 부천시 오정구", "41210": "경기 광명시",
    "41220": "경기 평택시", "41250": "경기 동두천시", "41271": "경기 안산시 상록구", "41273": "경기 안산시 단원구",
    "41281": "경기 고양시 덕양구", "41285": "경기 고양시 일산동구", "41287": "경기 고양시 일산서구", "41290": "경기 과천시",
    "41310": "경기 구리시", "41360": "경기 남양주시", "41370": "경기 오산시", "41390": "경기 시흥시",
    "41410": "경기 군포시", "41430": "경기 의왕시", "41450": "경기 하남시", "41461": "경기 용인시 처인구",
    "41463": "경기 용인시 기흥구", "41465": "경기 용인시 수지구", "41480": "경기 파주시", "41500": "경기 이천시",
    "41550": "경기 안성시", "41570": "경기 김포시", "41591": "경기 화성시 만세구", "41593": "경기 화성시 효행구",
    "41595": "경기 화성시 병점구", "41597": "경기 화성시 동탄구", "41610": "경기 광주시",
    "41630": "경기 양주시", "41650": "경기 포천시", "41670": "경기 여주시", "41800": "경기 연천군",
    "41820": "경기 가평군", "41830": "경기 양평군",
}

SESSION = requests.Session()
SESSION.headers.update({"User-Agent": USER_AGENT, "Accept-Language": "ko-KR,ko;q=0.9,en;q=0.5"})


@dataclass
class CrawlStats:
    pages: int = 0
    cards: int = 0
    accepted: int = 0
    skipped: int = 0
    geocoded: int = 0
    geocode_failed: int = 0
    failed_regions: int = 0


def node_text(node, selector: str) -> str:
    if not selector:
        return ""
    found = node.select_one(selector)
    return found.get_text(" ", strip=True) if found else ""


def node_attr(node, selector: str, *attrs: str) -> str:
    found = node.select_one(selector) if selector else None
    if not found:
        return ""
    for attr in attrs:
        value = found.get(attr)
        if value:
            return str(value).strip()
    return ""


def parse_manwon(raw: str) -> int | None:
    s = re.sub(r"[,\s원]", "", raw or "")
    eok = re.search(r"(\d+(?:\.\d+)?)억", s)
    man = re.search(r"(\d+)만", s)
    if eok or man:
        return int(float(eok.group(1)) * 10000 if eok else 0) + int(man.group(1) if man else 0)
    digits = re.sub(r"[^0-9]", "", s)
    return int(digits) if digits else None


def parse_area(raw: str) -> float | None:
    if not raw:
        return None
    m = re.search(r"(\d+(?:\.\d+)?)\s*(?:㎡|m2|m²|평)?", raw.replace(",", ""), re.I)
    if not m:
        return None
    value = float(m.group(1))
    if "평" in raw:
        value *= 3.305785
    return round(value, 2)


def normalize_building_type(raw: str) -> str:
    value = (raw or "").strip()
    compact = value.replace(" ", "")
    mapping = [
        ("아파트", "아파트"), ("오피스텔", "오피스텔"), ("빌라", "빌라"),
        ("연립", "연립주택"), ("다세대", "다세대주택"), ("단독", "단독주택"), ("다가구", "다가구주택"),
    ]
    for key, normalized in mapping:
        if key in compact:
            return normalized
    return value or "기타"


def source_id(source_url: str, title: str, address: str) -> str:
    base = source_url if source_url else f"{SOURCE_NAME}|{title}|{address}"
    return hashlib.sha1(base.encode("utf-8")).hexdigest()


def write_json_atomic(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def read_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def derive_region_from_address(address: str) -> dict[str, str]:
    tokens = address.split()
    sido = ""
    sigungu_parts: list[str] = []
    neighborhood = ""
    for token in tokens:
        if not sido and (token.endswith("도") or token.endswith("특별시") or token.endswith("광역시") or token.endswith("특별자치시") or token.endswith("특별자치도")):
            sido = token
        elif token.endswith("시") or token.endswith("군") or token.endswith("구"):
            if token != sido and len(sigungu_parts) < 2:
                sigungu_parts.append(token)
        if not neighborhood and (token.endswith("동") or token.endswith("읍") or token.endswith("면")):
            neighborhood = token
    return {"sido": sido, "sigungu": " ".join(sigungu_parts), "neighborhood": neighborhood}


def geocode(address: str, timeout_seconds: int | None = None) -> dict[str, Any]:
    try:
        response = SESSION.get(
            BACKEND + "/api/safety/geocode",
            params={"query": address},
            timeout=timeout_seconds or TIMEOUT,
        )
        response.raise_for_status()
        data = response.json()
        candidates = data.get("candidates") if isinstance(data.get("candidates"), list) else []
        location = data.get("location") or (candidates[0] if candidates else {})
        lat = location.get("latitude", location.get("lat"))
        lng = location.get("longitude", location.get("lng"))
        if lat is None or lng is None:
            return {}
        return {"lat": float(lat), "lng": float(lng), "resolvedAddress": location.get("address") or location.get("roadAddress") or ""}
    except Exception:
        return {}


def enrich_market_coordinates(rows: list[dict[str, Any]]) -> None:
    try:
        cache = json.loads(MARKET_GEO_CACHE.read_text(encoding="utf-8")) if MARKET_GEO_CACHE.exists() else {}
    except (OSError, json.JSONDecodeError):
        cache = {}
    requested = 0
    succeeded = 0
    started_at = time.monotonic()
    budget_exhausted = False
    disabled = False
    consecutive_failures = 0
    failures: list[str] = []
    print(
        f"market_geocode_start limit={MARKET_GEOCODE_LIMIT} "
        f"request_timeout={MARKET_GEOCODE_TIMEOUT}s max_seconds={MARKET_GEOCODE_MAX_SECONDS}s"
    )
    for row in rows:
        region_name = CAPITAL_LAWD_CODES.get(str(row.get("lawdCd", "")), "")
        address = " ".join(str(value).strip() for value in [region_name, row.get("neighborhood"), row.get("jibun")] if value).strip()
        if not address:
            continue
        coordinate = cache.get(address)
        if coordinate is None and requested < MARKET_GEOCODE_LIMIT and not budget_exhausted and not disabled:
            elapsed = time.monotonic() - started_at
            if elapsed >= MARKET_GEOCODE_MAX_SECONDS:
                budget_exhausted = True
                print(f"market_geocode_time_limit elapsed={elapsed:.1f}s requested={requested}")
                continue
            coordinate = geocode(address, MARKET_GEOCODE_TIMEOUT)
            requested += 1
            if coordinate:
                cache[address] = coordinate
                succeeded += 1
                consecutive_failures = 0
            else:
                failures.append(address)
                consecutive_failures += 1
                if consecutive_failures >= MARKET_GEOCODE_FAILURE_LIMIT:
                    disabled = True
                    print(
                        f"market_geocode_circuit_open consecutive_failures={consecutive_failures} "
                        "remaining_rows_will_upload_without_coordinates"
                    )
            if requested % 10 == 0 or requested == MARKET_GEOCODE_LIMIT:
                elapsed = time.monotonic() - started_at
                print(
                    f"market_geocode_progress={requested}/{MARKET_GEOCODE_LIMIT} "
                    f"success={succeeded} elapsed={elapsed:.1f}s"
                )
            if MARKET_GEOCODE_DELAY:
                time.sleep(MARKET_GEOCODE_DELAY)
        if coordinate:
            row["lat"] = coordinate.get("lat")
            row["lng"] = coordinate.get("lng")
    write_json_atomic(MARKET_GEO_CACHE, cache)
    write_json_atomic(GEOCODE_FAILURE_OUT, {
        "createdAt": datetime.now(timezone.utc).isoformat(), "count": len(failures), "addresses": failures
    })
    print(
        f"market_geocode_requested={requested} success={succeeded} "
        f"cache_size={len(cache)} elapsed={time.monotonic() - started_at:.1f}s"
    )


def geocode_dataset_fingerprint(rows: list[dict[str, Any]]) -> str:
    """Identify the saved MOLIT dataset without changing when coordinates are added."""
    source_ids = [str(row.get("sourceId", "")) for row in rows]
    content = json.dumps(source_ids, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha1(content.encode("utf-8")).hexdigest()


def geocode_saved_rows(
    payload: dict[str, Any], rows: list[dict[str, Any]]
) -> tuple[list[dict[str, Any]], dict[str, int]]:
    """Add missing coordinates to saved rows and checkpoint progress safely."""
    try:
        cache = json.loads(MARKET_GEO_CACHE.read_text(encoding="utf-8")) if MARKET_GEO_CACHE.exists() else {}
    except (OSError, json.JSONDecodeError):
        cache = {}

    fingerprint = geocode_dataset_fingerprint(rows)
    state = read_json(GEOCODE_STATE_OUT)
    if state.get("fingerprint") == fingerprint:
        next_index = max(0, min(int(state.get("nextIndex", 0) or 0), len(rows)))
        changed_source_ids = {
            str(value) for value in state.get("pendingUploadSourceIds", []) if str(value)
        }
    else:
        next_index = 0
        changed_source_ids = set()

    if next_index >= len(rows) and any(row.get("lat") is None or row.get("lng") is None for row in rows):
        next_index = 0

    requested = 0
    succeeded = 0
    cached = 0
    failures: list[str] = []
    consecutive_failures = 0
    disabled = False
    started_at = time.monotonic()
    current_index = next_index

    def save_progress(index: int) -> None:
        payload["items"] = rows
        payload["geocodedAt"] = datetime.now(timezone.utc).isoformat()
        write_json_atomic(OUT, payload)
        write_json_atomic(MARKET_GEO_CACHE, cache)
        write_json_atomic(GEOCODE_STATE_OUT, {
            "fingerprint": fingerprint,
            "nextIndex": index,
            "totalRows": len(rows),
            "pendingUploadSourceIds": sorted(changed_source_ids),
            "updatedAt": datetime.now(timezone.utc).isoformat(),
        })

    print(
        f"geocode_only_start rows={len(rows)} resume_index={next_index} "
        f"limit={MARKET_GEOCODE_LIMIT} cache_size={len(cache)}"
    )
    try:
        for index in range(next_index, len(rows)):
            current_index = index
            row = rows[index]
            if row.get("lat") is not None and row.get("lng") is not None:
                current_index = index + 1
                continue
            region_name = CAPITAL_LAWD_CODES.get(str(row.get("lawdCd", "")), "")
            address = " ".join(
                str(value).strip()
                for value in [region_name, row.get("neighborhood"), row.get("jibun")]
                if value
            ).strip()
            if not address:
                current_index = index + 1
                continue

            coordinate = cache.get(address)
            checkpoint_due = False
            if coordinate:
                cached += 1
            else:
                if requested >= MARKET_GEOCODE_LIMIT or disabled:
                    break
                elapsed = time.monotonic() - started_at
                if elapsed >= MARKET_GEOCODE_MAX_SECONDS:
                    print(f"geocode_only_time_limit elapsed={elapsed:.1f}s requested={requested}")
                    break
                coordinate = geocode(address, MARKET_GEOCODE_TIMEOUT)
                requested += 1
                if coordinate:
                    cache[address] = coordinate
                    succeeded += 1
                    consecutive_failures = 0
                else:
                    failures.append(address)
                    consecutive_failures += 1
                    if consecutive_failures >= MARKET_GEOCODE_FAILURE_LIMIT:
                        disabled = True
                        print(
                            f"geocode_only_circuit_open consecutive_failures={consecutive_failures}"
                        )

                checkpoint_due = requested % 10 == 0
                if MARKET_GEOCODE_DELAY:
                    time.sleep(MARKET_GEOCODE_DELAY)

            if coordinate:
                row["lat"] = coordinate.get("lat")
                row["lng"] = coordinate.get("lng")
                source_id = str(row.get("sourceId", ""))
                if source_id:
                    changed_source_ids.add(source_id)
            current_index = index + 1
            if checkpoint_due:
                save_progress(current_index)
                print(
                    f"geocode_only_progress index={current_index}/{len(rows)} "
                    f"requested={requested}/{MARKET_GEOCODE_LIMIT} success={succeeded}"
                )
    except BaseException:
        save_progress(current_index)
        raise

    save_progress(current_index)
    write_json_atomic(GEOCODE_FAILURE_OUT, {
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "count": len(failures),
        "addresses": failures,
    })
    coordinates = sum(1 for row in rows if row.get("lat") is not None and row.get("lng") is not None)
    summary = {
        "requested": requested,
        "succeeded": succeeded,
        "cached": cached,
        "coordinates": coordinates,
        "missing": len(rows) - coordinates,
        "nextIndex": current_index,
    }
    print(
        f"geocode_only_result requested={requested} success={succeeded} cached={cached} "
        f"coordinates={coordinates} missing={len(rows) - coordinates} next_index={current_index}"
    )
    return [row for row in rows if str(row.get("sourceId", "")) in changed_source_ids], summary


def complete_geocode_upload(rows: list[dict[str, Any]]) -> None:
    """Clear pending coordinate uploads only after all changed rows reached the DB."""
    state = read_json(GEOCODE_STATE_OUT)
    state["pendingUploadSourceIds"] = []
    state["uploadedAt"] = datetime.now(timezone.utc).isoformat()
    state["coordinates"] = sum(
        1 for row in rows if row.get("lat") is not None and row.get("lng") is not None
    )
    write_json_atomic(GEOCODE_STATE_OUT, state)


def fetch_html(url: str) -> BeautifulSoup:
    response = SESSION.get(url, timeout=TIMEOUT)
    response.raise_for_status()
    return BeautifulSoup(response.text, "html.parser")


def parse_card(card, page_url: str, stats: CrawlStats) -> dict[str, Any] | None:
    stats.cards += 1
    title = node_text(card, TITLE)
    address = node_text(card, ADDRESS)
    price = parse_manwon(node_text(card, PRICE))
    if not title or not address or not price:
        stats.skipped += 1
        return None
    href = node_attr(card, LINK, "href")
    item_url = urljoin(page_url, href) if href else page_url
    image_src = node_attr(card, IMAGE, "data-src", "data-lazy-src", "src")
    image_url = urljoin(page_url, image_src) if image_src else None
    region = derive_region_from_address(address)
    geo = geocode(address)
    if geo: stats.geocoded += 1
    else: stats.geocode_failed += 1
    now = datetime.now(timezone.utc).isoformat()
    return {
        "sourceId": source_id(item_url, title, address), "sourceUrl": item_url,
        "title": title, "address": address, "sido": region["sido"], "sigungu": region["sigungu"],
        "neighborhood": region["neighborhood"], "lat": geo.get("lat"), "lng": geo.get("lng"),
        "salePrice": price, "buildingType": normalize_building_type(node_text(card, BUILDING_TYPE)),
        "area": parse_area(node_text(card, AREA)), "floor": node_text(card, FLOOR),
        "description": node_text(card, DESCRIPTION), "imageUrl": image_url, "sourceUpdatedAt": now,
    }


def crawl_html() -> tuple[list[dict[str, Any]], CrawlStats]:
    validate_html_config(require_token=False)
    stats = CrawlStats()
    items: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    page_url = START_URL
    for _ in range(MAX_PAGES):
        soup = fetch_html(page_url)
        stats.pages += 1
        for card in soup.select(CARD):
            item = parse_card(card, page_url, stats)
            if not item or item["sourceId"] in seen_ids:
                continue
            seen_ids.add(item["sourceId"])
            items.append(item)
            stats.accepted += 1
        if not NEXT_PAGE:
            break
        next_link = soup.select_one(NEXT_PAGE)
        href = next_link.get("href") if next_link else None
        if not href:
            break
        next_url = urljoin(page_url, href)
        if next_url == page_url:
            break
        page_url = next_url
        time.sleep(REQUEST_DELAY)
    return items, stats


def xml_item_to_dict(item: ET.Element) -> dict[str, str]:
    out: dict[str, str] = {}
    for child in item:
        tag = child.tag.split("}")[-1]
        out[tag] = (child.text or "").strip()
    return out


def first(d: dict[str, str], *keys: str) -> str:
    for key in keys:
        v = d.get(key)
        if v is not None and str(v).strip() != "":
            return str(v).strip()
    return ""


def digits_int(value: str) -> int | None:
    s = re.sub(r"[^0-9]", "", value or "")
    return int(s) if s else None


def float_value(value: str) -> float | None:
    try:
        return float((value or "").replace(",", "").strip())
    except ValueError:
        return None


def molit_source_id(raw: dict[str, str], lawd_cd: str, deal_date: str, amount: int | None, area: float | None) -> str:
    parts = [
        lawd_cd, deal_date, first(raw, "aptNm", "아파트"), first(raw, "umdNm", "법정동"),
        first(raw, "jibun", "지번"), first(raw, "floor", "층"), str(area or ""), str(amount or ""),
        first(raw, "dealType", "거래유형"), first(raw, "cdealDay", "해제사유발생일")
    ]
    return hashlib.sha1("|".join(parts).encode("utf-8")).hexdigest()


def resolve_molit_regions() -> list[tuple[str, str]]:
    if MOLIT_LAWD_CDS:
        codes = [code.strip() for code in MOLIT_LAWD_CDS.split(",") if code.strip()]
        invalid = [code for code in codes if not re.fullmatch(r"\d{5}", code)]
        if invalid:
            raise SystemExit("MOLIT_LAWD_CDS에는 5자리 시군구 코드만 입력하세요: " + ", ".join(invalid))
        return [(code, CAPITAL_LAWD_CODES.get(code, code)) for code in dict.fromkeys(codes)]
    if MOLIT_REGION_SCOPE == "capital":
        return list(CAPITAL_LAWD_CODES.items())
    if MOLIT_REGION_SCOPE != "single":
        raise SystemExit("MOLIT_REGION_SCOPE은 single 또는 capital 이어야 합니다.")
    return [(MOLIT_LAWD_CD, CAPITAL_LAWD_CODES.get(MOLIT_LAWD_CD, MOLIT_LAWD_CD))]


def request_molit_page(lawd_cd: str, page_no: int, key_format: str):
    common_params = {
        "LAWD_CD": lawd_cd,
        "DEAL_YMD": MOLIT_DEAL_YMD,
        "pageNo": page_no,
        "numOfRows": MOLIT_ROWS,
    }
    if key_format == "encoded":
        request_url = MOLIT_API_URL + "?serviceKey=" + MOLIT_KEY
        return SESSION.get(request_url, params=common_params, timeout=max(TIMEOUT, 30))
    return SESSION.get(
        MOLIT_API_URL,
        params={"serviceKey": MOLIT_KEY, **common_params},
        timeout=max(TIMEOUT, 30),
    )


def checkpoint_key(regions: list[tuple[str, str]]) -> str:
    raw = MOLIT_DEAL_YMD + "|" + ",".join(code for code, _ in regions)
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()


def save_checkpoint(rows: list[dict[str, Any]], stats: CrawlStats, completed_regions: set[str], regions: list[tuple[str, str]]) -> None:
    write_json_atomic(CHECKPOINT_OUT, {
        "key": checkpoint_key(regions),
        "dealYmd": MOLIT_DEAL_YMD,
        "completedRegions": sorted(completed_regions),
        "stats": stats.__dict__,
        "items": rows,
        "updatedAt": datetime.now(timezone.utc).isoformat(),
    })


def load_checkpoint(regions: list[tuple[str, str]]) -> tuple[list[dict[str, Any]], CrawlStats, set[str]]:
    payload = read_json(CHECKPOINT_OUT)
    if payload.get("key") != checkpoint_key(regions):
        return [], CrawlStats(), set()
    raw_stats = payload.get("stats") if isinstance(payload.get("stats"), dict) else {}
    allowed = CrawlStats.__dataclass_fields__.keys()
    stats = CrawlStats(**{key: int(raw_stats.get(key, 0) or 0) for key in allowed})
    items = payload.get("items") if isinstance(payload.get("items"), list) else []
    completed = {str(code) for code in payload.get("completedRegions", [])}
    return items, stats, completed


def crawl_molit(max_regions: int | None = None, resume: bool = False) -> tuple[list[dict[str, Any]], CrawlStats]:
    if not MOLIT_KEY:
        raise SystemExit("필수 환경변수 누락: MOLIT_SERVICE_KEY")
    # 공공데이터포털은 인증키 화면에서 Encoding/Decoding 키를 모두 제공할 수 있다.
    # requests의 params=에 이미 인코딩된 키(예: %2B, %2F)를 넣으면 %가 다시 인코딩될 수 있으므로
    # encoded 키는 serviceKey 부분만 URL에 그대로 붙인다. decoded 키는 requests가 안전하게 인코딩한다.
    key_format = MOLIT_KEY_FORMAT
    if key_format not in {"auto", "decoded", "encoded"}:
        raise SystemExit("MOLIT_SERVICE_KEY_FORMAT은 auto, decoded, encoded 중 하나여야 합니다.")
    if key_format == "auto":
        key_format = "encoded" if re.search(r"%[0-9A-Fa-f]{2}", MOLIT_KEY) else "decoded"

    regions = resolve_molit_regions()
    if max_regions is not None:
        regions = regions[:max_regions]
    if resume:
        rows, stats, completed_regions = load_checkpoint(regions)
        if completed_regions:
            print(f"resume_checkpoint completed_regions={len(completed_regions)}/{len(regions)} rows={len(rows)}")
        expected_codes = {code for code, _ in regions}
        if completed_regions >= expected_codes:
            state = read_json(UPLOAD_STATE_OUT)
            fingerprint = upload_fingerprint(rows, "MOLIT_APT_TRADE", "molit")
            expected_batches = (len(rows) + IMPORT_BATCH_SIZE - 1) // IMPORT_BATCH_SIZE if rows else 0
            completed_batches = {int(value) for value in state.get("completedBatches", [])}
            if state.get("fingerprint") == fingerprint and len(completed_batches) >= expected_batches:
                print("resume_checkpoint_previous_run_complete=true action=fresh_crawl")
                rows, stats, completed_regions = [], CrawlStats(), set()
            else:
                print("resume_checkpoint_collection_complete=true action=retry_pending_upload")
        stats.failed_regions = 0
    else:
        rows, stats, completed_regions = [], CrawlStats(), set()
    failed_regions: list[str] = []
    RAW_OUT.parent.mkdir(parents=True, exist_ok=True)

    for region_index, (lawd_cd, region_name) in enumerate(regions, start=1):
        if lawd_cd in completed_regions:
            print(f"region_skip={region_index}/{len(regions)} lawd_cd={lawd_cd} name={region_name} reason=checkpoint")
            continue
        page_no = 1
        region_rows = 0
        try:
            while True:
                response = request_molit_page(lawd_cd, page_no, key_format)
                RAW_OUT.write_text(response.text, encoding="utf-8")
                if not response.ok:
                    raise RuntimeError(f"HTTP {response.status_code}")
                try:
                    root = ET.fromstring(response.text)
                except ET.ParseError as exc:
                    raise RuntimeError("XML 파싱 실패") from exc
                result_code = root.findtext(".//resultCode") or ""
                result_msg = root.findtext(".//resultMsg") or ""
                if result_code not in ("", "000", "00"):
                    raise RuntimeError(f"MOLIT API error {result_code}: {result_msg}")

                items = root.findall(".//item")
                stats.pages += 1
                for item in items:
                    stats.cards += 1
                    raw = xml_item_to_dict(item)
                    apt_name = first(raw, "aptNm", "아파트")
                    neighborhood = first(raw, "umdNm", "법정동")
                    amount = digits_int(first(raw, "dealAmount", "거래금액"))
                    area = float_value(first(raw, "excluUseAr", "전용면적"))
                    year = digits_int(first(raw, "dealYear", "년"))
                    month = digits_int(first(raw, "dealMonth", "월"))
                    day = digits_int(first(raw, "dealDay", "일"))
                    if not apt_name or not year or not month or not day or amount is None:
                        stats.skipped += 1
                        continue
                    deal_date = f"{year:04d}-{month:02d}-{day:02d}"
                    rows.append({
                        "sourceId": molit_source_id(raw, lawd_cd, deal_date, amount, area),
                        "lawdCd": lawd_cd,
                        "dealYmd": MOLIT_DEAL_YMD,
                        "aptName": apt_name,
                        "neighborhood": neighborhood,
                        "jibun": first(raw, "jibun", "지번"),
                        "roadName": first(raw, "roadNm", "도로명"),
                        "dealAmount": amount,
                        "area": area,
                        "floor": first(raw, "floor", "층"),
                        "buildYear": digits_int(first(raw, "buildYear", "건축년도")),
                        "dealDate": deal_date,
                        "dealingType": first(raw, "dealingGbn", "dealType", "거래유형"),
                        "cancelDealYn": first(raw, "cdealType", "해제여부"),
                    })
                    stats.accepted += 1
                    region_rows += 1

                total_count = digits_int(root.findtext(".//totalCount") or "") or len(items)
                print(f"region={region_index}/{len(regions)} lawd_cd={lawd_cd} name={region_name} page={page_no} rows={len(items)} total={total_count}")
                if page_no * MOLIT_ROWS >= total_count or not items:
                    break
                page_no += 1
                if REQUEST_DELAY:
                    time.sleep(REQUEST_DELAY)
            print(f"region_complete={region_name} accepted={region_rows}")
            completed_regions.add(lawd_cd)
            save_checkpoint(rows, stats, completed_regions, regions)
        except Exception as exc:
            if region_rows:
                rows = [row for row in rows if str(row.get("lawdCd", "")) != lawd_cd]
                stats.accepted = max(0, stats.accepted - region_rows)
            failed_regions.append(f"{lawd_cd} {region_name}: {exc}")
            print(f"region_error={lawd_cd} name={region_name} error={exc}", file=sys.stderr)

    if failed_regions:
        stats.failed_regions = len(failed_regions)
        print(f"failed_regions={len(failed_regions)}", file=sys.stderr)
        for failure in failed_regions:
            print(f"- {failure}", file=sys.stderr)
    if not rows and failed_regions:
        raise RuntimeError("모든 MOLIT 지역 수집이 실패했습니다.")
    enrich_market_coordinates(rows)
    save_checkpoint(rows, stats, completed_regions, regions)
    return rows, stats


def save(items: list[dict[str, Any]], stats: CrawlStats, source_name: str) -> None:
    payload = {"sourceName": source_name, "sourceMode": MODE, "crawledAt": datetime.now(timezone.utc).isoformat(), "stats": stats.__dict__, "items": items}
    write_json_atomic(OUT, payload)


def import_endpoint(source_mode: str) -> str:
    return "/api/properties/market-import" if source_mode == "molit" else "/api/properties/crawl-import"


def validate_import_token() -> None:
    if not TOKEN:
        raise SystemExit("필수 환경변수 누락: PROPERTY_IMPORT_TOKEN")
    try:
        response = SESSION.post(
            BACKEND + "/api/properties/import-check",
            headers={"X-Property-Import-Token": TOKEN},
            timeout=TIMEOUT,
        )
    except requests.RequestException as exc:
        raise RuntimeError(f"ZipAI Import Token 확인 요청 실패: {exc}") from exc
    print(f"import_token_status={response.status_code}")
    if response.status_code == 401:
        raise RuntimeError("PROPERTY_IMPORT_TOKEN이 실행 중인 ZipAI 서버의 토큰과 일치하지 않습니다.")
    response.raise_for_status()


def upload_fingerprint(items: list[dict[str, Any]], source_name: str, source_mode: str) -> str:
    content = json.dumps({"source": source_name, "mode": source_mode, "items": items}, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha1(content.encode("utf-8")).hexdigest()


def upload(items: list[dict[str, Any]], source_name: str, source_mode: str = MODE) -> dict[str, Any]:
    validate_import_token()
    if not items:
        return {"success": True, "collected": 0, "inserted": 0, "updated": 0, "errors": 0, "batches": 0}
    endpoint = import_endpoint(source_mode)
    fingerprint = upload_fingerprint(items, source_name, source_mode)
    previous = read_json(UPLOAD_STATE_OUT)
    if previous.get("fingerprint") == fingerprint:
        completed = {int(value) for value in previous.get("completedBatches", [])}
        raw_totals = previous.get("totals") if isinstance(previous.get("totals"), dict) else {}
        totals = {key: int(raw_totals.get(key, 0) or 0) for key in ("collected", "inserted", "updated", "errors")}
    else:
        completed = set()
        totals = {"collected": 0, "inserted": 0, "updated": 0, "errors": 0}
    batches = (len(items) + IMPORT_BATCH_SIZE - 1) // IMPORT_BATCH_SIZE
    for index in range(batches):
        batch_number = index + 1
        if batch_number in completed:
            print(f"upload_batch_skip={batch_number}/{batches} reason=checkpoint")
            continue
        batch = items[index * IMPORT_BATCH_SIZE:(index + 1) * IMPORT_BATCH_SIZE]
        response = None
        for attempt in range(1, UPLOAD_RETRIES + 2):
            try:
                response = SESSION.post(BACKEND + endpoint,
                    headers={"X-Property-Import-Token": TOKEN, "Content-Type": "application/json"},
                    json={"sourceName": source_name, "items": batch}, timeout=max(TIMEOUT, 120))
                print(f"upload_batch={batch_number}/{batches} rows={len(batch)} status={response.status_code} attempt={attempt}")
                if response.text: print(response.text)
                if response.status_code == 401:
                    raise RuntimeError("업로드 중 PROPERTY_IMPORT_TOKEN 인증이 거부되었습니다.")
                if response.status_code not in (429,) and response.status_code < 500:
                    if response.status_code >= 400:
                        raise RuntimeError(f"업로드 요청 오류 HTTP {response.status_code}: {response.text[:300]}")
                    break
                response.raise_for_status()
            except RuntimeError:
                raise
            except requests.RequestException as exc:
                if attempt > UPLOAD_RETRIES:
                    raise RuntimeError(f"업로드 배치 {batch_number}/{batches} 최종 실패: {exc}") from exc
                delay = UPLOAD_RETRY_DELAY * attempt
                print(f"upload_retry={batch_number}/{batches} next_attempt={attempt + 1} delay={delay:.1f}s error={exc}", file=sys.stderr)
                time.sleep(delay)
        if response is None:
            raise RuntimeError(f"업로드 배치 {batch_number}/{batches} 응답이 없습니다.")
        result = response.json()
        for key in totals:
            totals[key] += int(result.get(key, 0) or 0)
        completed.add(batch_number)
        write_json_atomic(UPLOAD_STATE_OUT, {
            "fingerprint": fingerprint, "sourceName": source_name, "sourceMode": source_mode,
            "completedBatches": sorted(completed), "totals": totals, "updatedAt": datetime.now(timezone.utc).isoformat()
        })
    return {"success": totals["errors"] == 0, **totals, "batches": batches, "completedBatches": len(completed)}


def load_saved_result() -> tuple[list[dict[str, Any]], str, str]:
    payload = read_json(OUT)
    items = payload.get("items") if isinstance(payload.get("items"), list) else None
    if items is None:
        raise RuntimeError(f"업로드할 저장 파일이 없거나 올바르지 않습니다: {OUT}")
    source_name = str(payload.get("sourceName") or ("MOLIT_APT_TRADE" if payload.get("sourceMode") == "molit" else SOURCE_NAME))
    source_mode = str(payload.get("sourceMode") or MODE).lower()
    if source_mode not in {"molit", "html"}:
        raise RuntimeError("저장 파일의 sourceMode가 올바르지 않습니다.")
    return items, source_name, source_mode


def validate_html_config(require_token: bool) -> None:
    missing = []
    for name, value in [
        ("PROPERTY_CRAWL_START_URL", START_URL), ("PROPERTY_CRAWL_CARD_SELECTOR", CARD),
        ("PROPERTY_CRAWL_TITLE_SELECTOR", TITLE), ("PROPERTY_CRAWL_ADDRESS_SELECTOR", ADDRESS),
        ("PROPERTY_CRAWL_PRICE_SELECTOR", PRICE),
    ]:
        if not value: missing.append(name)
    if require_token and not TOKEN: missing.append("PROPERTY_IMPORT_TOKEN")
    if missing: raise SystemExit("필수 환경변수 누락: " + ", ".join(missing))


def check_backend() -> None:
    print(f"source_mode={MODE}")
    print(f"backend={BACKEND}")
    if MODE == "molit":
        if not MOLIT_KEY:
            raise RuntimeError("필수 환경변수 누락: MOLIT_SERVICE_KEY")
        regions = resolve_molit_regions()
        print(f"molit_region_scope={MOLIT_REGION_SCOPE}")
        print(f"molit_region_count={len(regions)}")
        print(f"molit_lawd_cd={MOLIT_LAWD_CD}")
        print(f"molit_deal_ymd={MOLIT_DEAL_YMD}")
        print(f"molit_service_key={'SET' if MOLIT_KEY else 'MISSING'}")
        print(f"property_import_token={'SET' if TOKEN else 'MISSING'}")
        url = BACKEND + "/api/properties/market-transactions"
        params = {"lawdCd": MOLIT_LAWD_CD, "dealYmd": MOLIT_DEAL_YMD}
    else:
        validate_html_config(require_token=False)
        print(f"start_url={START_URL}")
        url = BACKEND + "/api/properties"
        params = {"dealType": "SALE"}
    try:
        response = SESSION.get(url, params=params, timeout=TIMEOUT)
        print(f"backend_status={response.status_code}")
        response.raise_for_status()
        validate_import_token()
    except Exception as exc:
        raise RuntimeError(f"사전 점검 실패: {exc}") from exc


def main() -> int:
    parser = argparse.ArgumentParser(description="ZipAI 매매 데이터 수집기")
    parser.add_argument("--upload", action="store_true", help="수집 후 ZipAI DB로 업로드")
    parser.add_argument("--check", action="store_true", help="설정과 ZipAI 백엔드 연결 확인")
    parser.add_argument("--max-regions", type=int, help="점검용으로 앞에서 N개 지역만 수집")
    parser.add_argument("--resume", action="store_true", help="같은 거래월의 완료 지역은 건너뛰고 이어서 수집")
    parser.add_argument("--upload-only", action="store_true", help="재수집 없이 저장된 JSON을 DB로 업로드")
    parser.add_argument(
        "--geocode-only",
        action="store_true",
        help="재수집 없이 저장된 MOLIT JSON의 누락 좌표를 보완하고 변경 건만 DB로 업로드",
    )
    args = parser.parse_args()
    if MODE not in {"html", "molit"}:
        raise SystemExit("PROPERTY_SOURCE_MODE는 html 또는 molit 이어야 합니다.")
    if args.max_regions is not None and args.max_regions < 1:
        raise SystemExit("--max-regions 값은 1 이상이어야 합니다.")
    if args.check:
        check_backend()
        return 0
    started_at = time.monotonic()
    if args.geocode_only:
        rows, source_name, saved_mode = load_saved_result()
        if saved_mode != "molit":
            raise RuntimeError("--geocode-only는 저장된 MOLIT 실거래가 자료에만 사용할 수 있습니다.")
        validate_import_token()
        payload = read_json(OUT)
        changed_rows, geocode_summary = geocode_saved_rows(payload, rows)
        if changed_rows:
            result = upload(changed_rows, source_name, saved_mode)
            print("import_result=" + json.dumps(result, ensure_ascii=False))
            if not result.get("success"):
                raise RuntimeError("좌표 보완 자료 업로드 중 오류가 발생했습니다.")
            complete_geocode_upload(rows)
        else:
            print("import_result=" + json.dumps({
                "success": True, "collected": 0, "inserted": 0, "updated": 0,
                "errors": 0, "batches": 0, "completedBatches": 0,
            }, ensure_ascii=False))
        print(
            f"run_summary mode={saved_mode} action=geocode_only rows={len(rows)} "
            f"coordinates={geocode_summary['coordinates']} missing={geocode_summary['missing']} "
            f"elapsed={time.monotonic() - started_at:.1f}s result=SUCCESS"
        )
        return 0
    if args.upload_only:
        rows, source_name, saved_mode = load_saved_result()
        result = upload(rows, source_name, saved_mode)
        print("import_result=" + json.dumps(result, ensure_ascii=False))
        print(f"run_summary mode={saved_mode} action=upload_only rows={len(rows)} elapsed={time.monotonic() - started_at:.1f}s result=SUCCESS")
        return 0
    if args.upload:
        validate_import_token()
    if MODE == "molit":
        rows, stats = crawl_molit(args.max_regions, args.resume)
        source_name = "MOLIT_APT_TRADE"
    else:
        rows, stats = crawl_html()
        source_name = SOURCE_NAME
    save(rows, stats, source_name)
    print(
        f"mode={MODE} pages={stats.pages} rows={stats.cards} "
        f"accepted={stats.accepted} skipped={stats.skipped} "
        f"failed_regions={stats.failed_regions}"
    )
    print(f"saved={OUT}")
    if args.upload:
        result = upload(rows, source_name)
        print("import_result=" + json.dumps(result, ensure_ascii=False))
    print(
        f"run_summary mode={MODE} regions_failed={stats.failed_regions} rows={len(rows)} "
        f"elapsed={time.monotonic() - started_at:.1f}s result=SUCCESS"
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("cancelled", file=sys.stderr)
        sys.exit(130)
