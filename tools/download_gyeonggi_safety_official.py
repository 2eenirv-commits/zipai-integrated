#!/usr/bin/env python3
from pathlib import Path
import requests
import sys

DATASETS = {
    "gyeonggi_security_light.csv":
        "https://data.gg.go.kr/portal/data/sheet/downloadSheetData.do?downloadType=C&infId=VEY71398U2941WM4E7PV21507518&infSeq=1",
    "gyeonggi_emergency_bell.csv":
        "https://data.gg.go.kr/portal/data/sheet/downloadSheetData.do?downloadType=C&infId=64I5OUQUMRCWUEZI5WAQ27006963&infSeq=1",
}

OUT = Path("data/raw/official")
OUT.mkdir(parents=True, exist_ok=True)

session = requests.Session()
session.headers.update({
    "User-Agent": "Mozilla/5.0",
    "Accept": "text/csv,text/plain,application/octet-stream,*/*",
    "Referer": "https://data.gg.go.kr/",
})

failed = []
for name, url in DATASETS.items():
    print(f"[DOWNLOAD] {name}")
    try:
        r = session.get(url, timeout=120, allow_redirects=True)
        r.raise_for_status()
        content_type = (r.headers.get("Content-Type") or "").lower()
        head = r.content[:200].lstrip().lower()
        if b"<html" in head or b"<!doctype" in head:
            raise RuntimeError("CSV 대신 HTML 응답을 받았습니다.")
        path = OUT / name
        path.write_bytes(r.content)
        print(f"  -> {path} ({len(r.content):,} bytes, {content_type})")
    except Exception as e:
        failed.append((name, url, str(e)))
        print(f"  !! 실패: {e}")

if failed:
    print("\n일부 다운로드가 실패했습니다.")
    print("아래 URL은 로그인 없이 브라우저에서 직접 열 수 있는 경기데이터드림 CSV 다운로드 주소입니다.")
    for name, url, err in failed:
        print(f"- {name}: {url}")
    sys.exit(2)

print("\n완료.")
