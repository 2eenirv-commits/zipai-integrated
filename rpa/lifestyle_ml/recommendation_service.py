"""ZipAI Lifestyle AI/ML 3차 실행기.

비교 모델:
  1. 기존 Weighted Score (baseline)
  2. 중요도 보정 Cosine Similarity
  3. 중요도 보정 KNN Euclidean

3차 추가:
  - 각 TOP K 추천 지역에 Feature별 추천 이유(Explainability) 제공

예:
  python rpa/lifestyle_ml/recommendation_service.py --source db --weights 5,4,3,2,4,5,3,4,5
  python rpa/lifestyle_ml/recommendation_service.py --source csv --csv sample.csv --weights 5,4,3,2,4,5,3,4,5
"""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass

import pandas as pd

from cosine_recommender import recommend_cosine
from data_loader import FEATURE_COLUMNS, load_from_csv, load_from_mysql
from explainability import attach_explanations
from knn_recommender import recommend_knn
from weighted_recommender import recommend_weighted


def _configure_utf8_stdio() -> None:
    """Windows/redirected pipe에서도 Spring이 읽을 출력 인코딩을 UTF-8로 고정한다."""
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        if stream is not None and hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")


_configure_utf8_stdio()


@dataclass(frozen=True)
class ComparisonResult:
    weighted: pd.DataFrame
    cosine: pd.DataFrame
    knn: pd.DataFrame
    overlap_at_k: dict[str, int]


def compare_recommenders(
    frame: pd.DataFrame,
    weights: list[float],
    top_k: int = 3,
    explanation_top_n: int = 4,
) -> ComparisonResult:
    weighted = attach_explanations(
        frame,
        recommend_weighted(frame, weights, top_k),
        weights,
        "weighted",
        explanation_top_n,
    )
    cosine = attach_explanations(
        frame,
        recommend_cosine(frame, weights, top_k),
        weights,
        "cosine",
        explanation_top_n,
    )
    knn = attach_explanations(
        frame,
        recommend_knn(frame, weights, top_k),
        weights,
        "knn_euclidean",
        explanation_top_n,
    )

    sets = {
        "weighted": set(weighted["area_id"]),
        "cosine": set(cosine["area_id"]),
        "knn": set(knn["area_id"]),
    }
    overlap = {
        "weighted_cosine": len(sets["weighted"] & sets["cosine"]),
        "weighted_knn": len(sets["weighted"] & sets["knn"]),
        "cosine_knn": len(sets["cosine"] & sets["knn"]),
        "all_three": len(sets["weighted"] & sets["cosine"] & sets["knn"]),
    }
    return ComparisonResult(weighted, cosine, knn, overlap)



def _selected_row(
    ranking: pd.DataFrame,
    selected_sigungu: str | None,
) -> pd.Series | None:
    """전체 순위에서 사용자가 선택한 시·군·구의 결과를 찾는다."""
    if not selected_sigungu:
        return None

    matches = ranking[
        ranking["sigungu"].astype(str).str.strip() == selected_sigungu.strip()
    ]
    if matches.empty:
        return None

    row = matches.iloc[0].copy()
    row["rank"] = int(matches.index[0]) + 1
    return row


def selected_area_results(
    frame: pd.DataFrame,
    weights: list[float],
    selected_sigungu: str | None,
) -> dict[str, pd.Series]:
    """TOP 3 여부와 무관하게 선택 지역의 세 알고리즘 점수와 전체 순위를 계산한다."""
    if not selected_sigungu:
        return {}

    full_k = len(frame)
    rankings = {
        "weighted": recommend_weighted(frame, weights, full_k),
        "cosine": recommend_cosine(frame, weights, full_k),
        "knn_euclidean": recommend_knn(frame, weights, full_k),
    }

    selected: dict[str, pd.Series] = {}
    for algorithm, ranking in rankings.items():
        row = _selected_row(ranking, selected_sigungu)
        if row is not None:
            selected[algorithm] = row
    return selected


def parse_weights(text: str) -> list[float]:
    try:
        values = [float(item.strip()) for item in text.split(",")]
    except ValueError as exc:
        raise argparse.ArgumentTypeError("가중치는 쉼표로 구분한 숫자여야 합니다.") from exc
    if len(values) != len(FEATURE_COLUMNS):
        raise argparse.ArgumentTypeError(f"가중치는 정확히 {len(FEATURE_COLUMNS)}개가 필요합니다.")
    return values


def print_result(title: str, frame: pd.DataFrame) -> None:
    print(f"\n[{title}]")
    for index, row in frame.iterrows():
        suffix = ""
        if "distance" in frame.columns:
            suffix = f" | distance={row['distance']:.6f}"
        print(f"{index + 1}. {row['sido']} {row['sigungu']} | score={row['score']:.4f}{suffix}")
        print(f"   추천 이유: {row['reason_summary']}")




def _tsv_text(value: object) -> str:
    """Spring Boot 브리지용 TSV에서 탭/개행이 레코드를 깨지 않도록 정리한다."""
    return str(value).replace("\t", " ").replace("\r", " ").replace("\n", " ")


def print_tsv_result(algorithm: str, frame: pd.DataFrame) -> None:
    for rank, (_, row) in enumerate(frame.iterrows(), start=1):
        distance = ""
        if "distance" in frame.columns:
            distance = f"{float(row['distance']):.12f}"
        reason = _tsv_text(row.get("reason_summary", ""))
        reason_details = row.get("reason_details", [])
        reason_count = len(reason_details) if isinstance(reason_details, list) else 0
        fields = [
            "RESULT", algorithm, str(rank), str(int(row["area_id"])),
            _tsv_text(row["area_code"]), _tsv_text(row["sido"]), _tsv_text(row["sigungu"]),
            f"{float(row['score']):.12f}", distance, str(reason_count), reason,
        ]
        print("\t".join(fields))



def print_tsv_selected(selected: dict[str, pd.Series], frame: pd.DataFrame) -> None:
    for algorithm, row in selected.items():
        distance = ""
        if "distance" in row.index and pd.notna(row["distance"]):
            distance = f"{float(row['distance']):.12f}"

        fields = [
            "SELECTED",
            algorithm,
            str(int(row["rank"])),
            str(int(row["area_id"])),
            _tsv_text(row["area_code"]),
            _tsv_text(row["sido"]),
            _tsv_text(row["sigungu"]),
            f"{float(row['score']):.12f}",
            distance,
        ]
        print("\t".join(fields))

    # 선택지역의 원본 9개 공공데이터 정규화 점수(0~100)는 알고리즘과 무관하므로 1회만 전달한다.
    first = next(iter(selected.values()), None)
    if first is None:
        return

    matched = frame[frame["area_id"] == int(first["area_id"])]
    if matched.empty:
        return

    area = matched.iloc[0]
    feature_fields = ["SELECTED_FEATURES"]
    feature_fields.extend(f"{float(area[column]):.12f}" for column in FEATURE_COLUMNS)
    print("\t".join(feature_fields))


def print_tsv(result: ComparisonResult, area_count: int, selected: dict[str, pd.Series] | None = None, frame: pd.DataFrame | None = None) -> None:
    print(f"META\t{area_count}\t{len(FEATURE_COLUMNS)}")
    print_tsv_result("weighted", result.weighted)
    print_tsv_result("cosine", result.cosine)
    print_tsv_result("knn_euclidean", result.knn)
    if selected and frame is not None:
        print_tsv_selected(selected, frame)
    for key, value in result.overlap_at_k.items():
        print(f"OVERLAP\t{key}\t{value}")


def main() -> None:
    parser = argparse.ArgumentParser(description="ZipAI Lifestyle AI/ML 3차 추천 비교 + 설명")
    parser.add_argument("--source", choices=("db", "csv"), default="db")
    parser.add_argument("--csv", help="--source csv 사용 시 CSV 경로")
    parser.add_argument("--sido", default="경기도")
    parser.add_argument(
        "--weights",
        type=parse_weights,
        default=parse_weights("5,4,3,2,4,5,3,4,5"),
        help="교통,편의,의료,교육,공원,안전,상권,조용함,주거비 중요도 (각 0~5)",
    )
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--selected-sigungu", help="사용자가 이사 관심 지역으로 선택한 시·군·구")
    parser.add_argument("--reason-top-n", type=int, default=4, help="추천 이유에 표시할 Feature 수")
    parser.add_argument("--format", choices=("text", "tsv"), default="text", help="출력 형식")
    args = parser.parse_args()

    if args.source == "csv":
        if not args.csv:
            parser.error("--source csv일 때 --csv 경로가 필요합니다.")
        frame = load_from_csv(args.csv)
    else:
        frame = load_from_mysql(args.sido)

    result = compare_recommenders(frame, args.weights, args.top_k, args.reason_top_n)
    selected = selected_area_results(frame, args.weights, args.selected_sigungu)

    if args.format == "tsv":
        print_tsv(result, len(frame), selected, frame)
        return

    print(f"[DATA] {len(frame)}개 지역 / {len(FEATURE_COLUMNS)}개 Feature")
    print_result("Weighted Score (Baseline)", result.weighted)
    print_result("Weighted Cosine Similarity", result.cosine)
    print_result("KNN Euclidean", result.knn)
    print("\n[Overlap@K]")
    for key, value in result.overlap_at_k.items():
        print(f"{key}: {value}")


if __name__ == "__main__":
    main()
