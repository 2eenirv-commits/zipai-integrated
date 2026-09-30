#!/usr/bin/env python3
import argparse
import json
import sys
from urllib.parse import urlencode
from urllib.request import urlopen
from urllib.error import HTTPError, URLError

DEFAULT_CASES = [
    ("Seoul", 37.5665, 126.9780),
    ("Suwon", 37.2636, 127.0286),
]

def get_json(base, path, params):
    url = base.rstrip("/") + path + "?" + urlencode(params)
    try:
        with urlopen(url, timeout=15) as response:
            body = response.read().decode("utf-8")
            return url, response.status, json.loads(body)
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {url}\n{body}") from exc
    except URLError as exc:
        raise RuntimeError(f"Cannot connect: {url}\n{exc}") from exc

def verify_case(base, label, lat, lng, radius):
    params = {"lat": lat, "lng": lng, "radius": radius}

    infra_url, infra_status, infra = get_json(
        base, "/api/safety/infrastructure", params
    )
    assert infra_status == 200
    assert infra.get("success") is True
    assert isinstance(infra.get("count"), int)
    assert isinstance(infra.get("facilities"), list)
    assert infra["count"] == len(infra["facilities"])

    score_url, score_status, score = get_json(
        base, "/api/safety/score", params
    )
    assert score_status == 200
    assert score.get("success") is True
    assert isinstance(score.get("score"), int)
    assert 0 <= score["score"] <= 100
    assert isinstance(score.get("summary"), dict)
    assert isinstance(score.get("facilities"), list)

    types = sorted(score["summary"].keys())
    raw_aliases = {"EMERGENCY_BELL", "SECURITY_LIGHT"}
    if raw_aliases.intersection(types):
        raise AssertionError(
            "API leaked DB raw facility aliases instead of canonical API types: "
            + ", ".join(types)
        )

    print(
        f"[OK] {label}: infra={infra['count']}, score={score['score']}, "
        f"grade={score.get('grade')}, types={types}"
    )
    print(f"     {infra_url}")
    print(f"     {score_url}")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:4173")
    parser.add_argument("--radius", type=int, default=500)
    parser.add_argument("--lat", type=float)
    parser.add_argument("--lng", type=float)
    args = parser.parse_args()

    cases = DEFAULT_CASES
    if args.lat is not None or args.lng is not None:
        if args.lat is None or args.lng is None:
            raise SystemExit("--lat and --lng must be used together.")
        cases = [("custom", args.lat, args.lng)]

    failed = 0
    for case in cases:
        try:
            verify_case(args.base, *case, args.radius)
        except Exception as exc:
            failed += 1
            print(f"[FAIL] {case[0]}: {exc}", file=sys.stderr)

    if failed:
        raise SystemExit(1)

    print("Safety API verification passed.")

if __name__ == "__main__":
    main()
