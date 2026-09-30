"""Process authorized property-listing CSV/JSON files from the incoming folder."""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
DEFAULT_INCOMING = BASE_DIR / "data" / "listing_import" / "incoming"
DEFAULT_ARCHIVE = BASE_DIR / "data" / "listing_import" / "archive"
DEFAULT_ERRORS = BASE_DIR / "data" / "listing_import" / "errors"
IMPORTER = BASE_DIR / "property_listing_importer.py"


def load_project_env() -> None:
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
        name, value = name.strip(), value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        if name and name not in os.environ:
            os.environ[name] = value


def env_bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(command, cwd=BASE_DIR, text=True, encoding="utf-8", errors="replace",
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.stdout:
        print(result.stdout, end="" if result.stdout.endswith("\n") else "\n")
    return result


def unique_destination(directory: Path, source: Path) -> Path:
    stamp = time.strftime("%Y%m%d_%H%M%S")
    candidate = directory / f"{source.stem}_{stamp}{source.suffix.lower()}"
    sequence = 1
    while candidate.exists():
        candidate = directory / f"{source.stem}_{stamp}_{sequence}{source.suffix.lower()}"
        sequence += 1
    return candidate


def main() -> int:
    load_project_env()
    parser = argparse.ArgumentParser(description="ZipAI 현재 매물 CSV/JSON 예약 Import")
    parser.add_argument("--incoming", type=Path, default=DEFAULT_INCOMING)
    parser.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    parser.add_argument("--errors", type=Path, default=DEFAULT_ERRORS)
    args = parser.parse_args()

    source_name = os.getenv("PROPERTY_LISTING_SOURCE_NAME", "AUTHORIZED_BROKER_CSV").strip()
    complete_snapshot = env_bool("PROPERTY_LISTING_COMPLETE_SNAPSHOT", False)
    study_data = env_bool("PROPERTY_LISTING_STUDY_DATA", False)
    if not source_name:
        print("ERROR: PROPERTY_LISTING_SOURCE_NAME이 비어 있습니다.", file=sys.stderr)
        return 2
    if not IMPORTER.exists():
        print(f"ERROR: importer not found: {IMPORTER}", file=sys.stderr)
        return 2

    incoming = args.incoming.resolve()
    archive = args.archive.resolve()
    errors = args.errors.resolve()
    incoming.mkdir(parents=True, exist_ok=True)
    archive.mkdir(parents=True, exist_ok=True)
    errors.mkdir(parents=True, exist_ok=True)

    files = sorted((path for path in incoming.iterdir()
                    if path.is_file() and path.suffix.lower() in {".csv", ".json"}),
                   key=lambda path: (path.stat().st_mtime, path.name.lower()))
    print(f"source_name={source_name}")
    print(f"complete_snapshot={str(complete_snapshot).lower()}")
    print(f"study_data={str(study_data).lower()}")
    print(f"incoming_files={len(files)}")
    if not files:
        print("result=NO_FILES")
        return 0

    preflight = run([sys.executable, str(IMPORTER), "--check"])
    if preflight.returncode != 0:
        print("result=ERROR preflight_failed", file=sys.stderr)
        return preflight.returncode

    completed = failed = 0
    archive_day = archive / time.strftime("%Y%m%d")
    archive_day.mkdir(parents=True, exist_ok=True)
    for path in files:
        print(f"file_start={path.name}")
        command = [sys.executable, str(IMPORTER), "--file", str(path), "--source-name", source_name, "--upload"]
        if study_data:
            command.append("--study-data")
        if complete_snapshot:
            command.append("--complete-snapshot")
        result = run(command)
        if result.returncode == 0:
            destination = unique_destination(archive_day, path)
            shutil.move(str(path), str(destination))
            completed += 1
            print(f"file_archived={destination}")
            continue

        failed += 1
        report = errors / f"{path.stem}_{time.strftime('%Y%m%d_%H%M%S')}.error.txt"
        report.write_text(
            f"file={path}\nsource_name={source_name}\nexit_code={result.returncode}\n\n{result.stdout or ''}",
            encoding="utf-8",
        )
        print(f"file_failed={path}", file=sys.stderr)
        print(f"error_report={report}", file=sys.stderr)

    print(f"batch_result completed={completed} failed={failed} total={len(files)}")
    return 0 if failed == 0 else 3


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("cancelled", file=sys.stderr)
        raise SystemExit(130)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
