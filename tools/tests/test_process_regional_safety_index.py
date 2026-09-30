import csv
import tempfile
import unittest
from pathlib import Path
import sys

SAFETY_DIR = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(SAFETY_DIR))

from tools.process_regional_safety_index import CSV_COLUMNS, parse_hwpx, validate, write_csv


class RegionalSafetyIndexProcessorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SAFETY_DIR / "data/raw/regional_safety/2025_regional_safety_index_result.hwpx"
        cls.rows = parse_hwpx(cls.source)

    def test_extracts_all_official_rows_and_korean_names(self):
        summary = validate(self.rows)
        self.assertEqual(243, summary["total_rows"])
        self.assertEqual(17, summary["sido_rows"])
        self.assertEqual(226, summary["sigungu_rows"])
        gangnam = next(row for row in self.rows if row.sido_name == "서울특별시" and row.sigungu_name == "강남구")
        self.assertEqual((5, 4, 4, 3, 2, 1), (
            gangnam.traffic_grade, gangnam.fire_grade, gangnam.crime_grade,
            gangnam.life_safety_grade, gangnam.suicide_grade, gangnam.infectious_disease_grade,
        ))

    def test_csv_has_only_declared_official_grade_and_source_columns(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "regional.csv"
            write_csv(self.rows, output)
            with output.open(encoding="utf-8", newline="") as source:
                reader = csv.DictReader(source)
                csv_rows = list(reader)
            self.assertEqual(list(CSV_COLUMNS), reader.fieldnames)
            self.assertEqual(243, len(csv_rows))
            self.assertNotIn("overall_grade", reader.fieldnames)
            self.assertNotIn("region_code", reader.fieldnames)

    def test_sejong_and_jeju_exist_only_at_the_official_sido_level(self):
        sejong = [row for row in self.rows if row.sido_name == "세종특별자치시"]
        jeju = [row for row in self.rows if row.sido_name == "제주특별자치도"]

        self.assertEqual(["sido"], [row.region_level for row in sejong])
        self.assertEqual(["sido"], [row.region_level for row in jeju])
        self.assertFalse(any(row.sigungu_name in {"제주시", "서귀포시"} for row in self.rows))


if __name__ == "__main__":
    unittest.main()
