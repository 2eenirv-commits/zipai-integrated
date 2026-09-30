#!/usr/bin/env python3
"""공식 주거금융 페이지의 정책별 핵심 영역 변경을 감지해 ZipAI에 전달한다."""

import argparse
import hashlib
import html
import json
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
DEFAULT_SOURCES = ROOT / "finance_policy_sources.json"
DEFAULT_OUTPUT = ROOT / "data" / "processed" / "finance_policy_snapshots.json"


def load_dotenv():
    path = PROJECT_ROOT / ".env"
    if not path.is_file():
        return
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        if key:
            os.environ.setdefault(key, value)


class TextExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style", "noscript"}:
            self.hidden += 1

    def handle_endtag(self, tag):
        if tag in {"script", "style", "noscript"} and self.hidden:
            self.hidden -= 1

    def handle_data(self, data):
        if not self.hidden:
            self.parts.append(data)


def page_text(payload, charset):
    extractor = TextExtractor()
    extractor.feed(payload.decode(charset or "utf-8", errors="replace"))
    return re.sub(r"\s+", " ", html.unescape(" ".join(extractor.parts))).strip()


def fetch(source, timeout):
    response = requests.get(
        source["sourceUrl"],
        timeout=timeout,
        headers={"User-Agent": "ZipAI-Study/1.0 (official-policy-change-checker)"},
    )
    response.raise_for_status()
    content_type = response.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
    if content_type not in {"text/html", "application/xhtml+xml", ""}:
        raise RuntimeError(f"지원하지 않는 응답 형식: {content_type}")
    charset = response.encoding
    if not charset or charset.lower() == "iso-8859-1":
        charset = response.apparent_encoding or "utf-8"
    text = page_text(response.content, charset)
    keyword = re.sub(r"\s+", "", source["keyword"])
    compact = re.sub(r"\s+", "", text)
    compact_index = compact.find(keyword)
    if compact_index < 0:
        raise RuntimeError(f"정책 키워드를 찾지 못했습니다: {source['keyword']}")

    # 공백 제거 문자열의 위치를 원문 위치에 근사 변환한다.
    seen = 0
    original_index = 0
    for original_index, char in enumerate(text):
        if not char.isspace():
            if seen >= compact_index:
                break
            seen += 1
    start = max(0, original_index - 1800)
    end = min(len(text), original_index + len(source["keyword"]) + 3600)
    excerpt = text[start:end].strip()
    normalized = re.sub(r"\s+", " ", excerpt)
    return {
        "policyName": source["policyName"],
        "sourceName": source["sourceName"],
        "sourceUrl": source["sourceUrl"],
        "sourceHash": hashlib.sha256(normalized.encode("utf-8")).hexdigest(),
        "snapshotExcerpt": normalized[:6000],
        "checkedAt": datetime.now(timezone.utc).isoformat(),
    }


def upload(base_url, token, snapshots, timeout):
    data = json.dumps(snapshots, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/api/finance/policy-updates/import",
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "X-Finance-Import-Token": token,
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def main():
    load_dotenv()
    parser = argparse.ArgumentParser()
    parser.add_argument("--sources", default=str(DEFAULT_SOURCES))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--no-upload", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    base_url = os.getenv("ZIPAI_BASE_URL", "http://localhost:8080")
    token = os.getenv("FINANCE_IMPORT_TOKEN", "")
    timeout = float(os.getenv("FINANCE_CRAWL_TIMEOUT_SECONDS", "20"))
    sources = json.loads(Path(args.sources).read_text(encoding="utf-8"))
    print(f"backend={base_url}")
    print(f"sources={len(sources)}")
    print(f"finance_import_token={'SET' if token else 'MISSING'}")
    if args.check:
        if not token:
            print("필수 환경변수 누락: FINANCE_IMPORT_TOKEN", file=sys.stderr)
            return 2
        try:
            status, result = upload(base_url, token, [], timeout)
        except urllib.error.HTTPError as error:
            print(f"import_token_status={error.code}", file=sys.stderr)
            return 1
        except urllib.error.URLError as error:
            print(f"backend_check_failed={error}", file=sys.stderr)
            return 1
        print(f"import_token_status={status}")
        print("check_result=" + json.dumps(result, ensure_ascii=False))
        return 0

    snapshots = []
    failures = []
    for index, source in enumerate(sources, 1):
        try:
            snapshot = fetch(source, timeout)
            snapshots.append(snapshot)
            print(f"source={index}/{len(sources)} policy={source['policyName']} result=OK")
        except (OSError, ValueError, RuntimeError, requests.RequestException) as error:
            failures.append({"policyName": source.get("policyName"), "error": str(error)})
            print(f"source={index}/{len(sources)} policy={source.get('policyName')} result=FAILED error={error}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"items": snapshots, "failures": failures}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"saved={output}")
    if not snapshots:
        print("수집에 성공한 공식 페이지가 없습니다.", file=sys.stderr)
        return 1
    if args.no_upload:
        print(f"run_summary collected={len(snapshots)} failed={len(failures)} action=save_only")
        return 0
    if not token:
        print("필수 환경변수 누락: FINANCE_IMPORT_TOKEN", file=sys.stderr)
        return 2
    try:
        status, result = upload(base_url, token, snapshots, timeout)
    except urllib.error.HTTPError as error:
        print(f"upload_status={error.code} body={error.read().decode('utf-8', errors='replace')}", file=sys.stderr)
        return 1
    except urllib.error.URLError as error:
        print(f"upload_failed={error}", file=sys.stderr)
        return 1
    print(f"upload_status={status}")
    print("import_result=" + json.dumps(result, ensure_ascii=False))
    print(f"run_summary collected={len(snapshots)} failed={len(failures)} result=SUCCESS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
