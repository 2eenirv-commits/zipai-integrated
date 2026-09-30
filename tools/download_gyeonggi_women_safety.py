import argparse
import http.cookiejar
import urllib.request
from pathlib import Path


DATASETS = {
    "guard": ("ZESFY6I0RPQ9OA61B0XS29014514", "gyeonggi_women_safe_guard_house_2025-06-24.csv"),
    "parcel": ("97BM2OZKIYD2GMWJZ0UI26817441", "gyeonggi_safe_parcel_locker_2026-04-14.csv"),
}
BASE = "https://data.gg.go.kr"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36"


def download_dataset(inf_id, filename, output_dir, opener):
    page = f"{BASE}/portal/data/service/selectServicePage.do?infId={inf_id}&infSeq=1"
    opener.open(urllib.request.Request(page, headers={"User-Agent": USER_AGENT}), timeout=30).read()
    url = f"{BASE}/portal/data/sheet/downloadSheetData.do?downloadType=C&infId={inf_id}&infSeq=1"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Referer": page})
    body = opener.open(request, timeout=60).read()
    if len(body) < 500 or b"alert(" in body[:500]:
        raise RuntimeError(f"official download failed for dataset {inf_id}")
    path = output_dir / filename
    path.write_bytes(body)
    return path, len(body)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=Path("rpa/safety/data/raw/gyeonggi/women_safety"))
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    for name, (inf_id, filename) in DATASETS.items():
        path, size = download_dataset(inf_id, filename, args.output_dir, opener)
        print(f"{name}: {path} ({size} bytes)")


if __name__ == "__main__":
    main()
