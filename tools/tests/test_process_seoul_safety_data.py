import csv
import tempfile
import unittest
from pathlib import Path

from tools.process_seoul_safety_data import (
    CRIME_TYPES, GuardHouse, _address_candidates, process_crime, process_guard_houses,
)


ROOT = Path(__file__).resolve().parents[2]


class SeoulSafetyDataProcessorTest(unittest.TestCase):
    def test_official_crime_file_is_complete_and_normalized(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "crime.csv"
            stats = process_crime(ROOT / "data/raw/seoul_police_crime_statistics_2024.csv", output)
            with output.open(encoding="utf-8-sig") as handle:
                rows = list(csv.DictReader(handle))
        self.assertEqual(310, stats["rawRows"])
        self.assertEqual(155, stats["processedRows"])
        self.assertEqual(31, stats["stations"])
        self.assertEqual(5, stats["crimeTypes"])
        self.assertEqual(0, stats["duplicates"])
        self.assertEqual(set(CRIME_TYPES), {row["crime_type"] for row in rows})
        self.assertEqual({"서울강남경찰서", "서울수서경찰서"},
                         {row["police_station_name"] for row in rows if row["sigungu_name"] == "강남구"})

    def test_official_guard_house_file_has_addresses_without_fake_coordinates(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "women.csv"
            stats = process_guard_houses(ROOT / "data/raw/seoul_women_safe_houses_2019.xlsx", output)
            with output.open(encoding="utf-8-sig") as handle:
                rows = list(csv.DictReader(handle))
        self.assertEqual(stats["rawRows"], len(rows))
        self.assertEqual(938, stats["rawRows"])
        self.assertEqual(stats["rawRows"], stats["validAddresses"])
        self.assertEqual(0, stats["geocodingSuccess"])
        self.assertEqual(0, stats["dbReadyRows"])
        self.assertTrue(all(not row["latitude"] and not row["longitude"] for row in rows))
        self.assertEqual(25, len({row["sigungu_name"] for row in rows}))
        self.assertEqual(0, stats["duplicates"])
        self.assertEqual("2019-11-04", stats["sourceUpdatedAt"])

    def test_guard_house_retry_candidates_add_seoul_district_and_remove_store_detail(self):
        row = GuardHouse("id", "CU", "점포", "강남구", "논현동 86-4번지, 상가 101호")
        candidates = _address_candidates(row)
        self.assertEqual("서울특별시 강남구 논현동 86-4번지, 상가 101호", candidates[0])
        self.assertIn("서울특별시 강남구 논현동 86-4", candidates)


if __name__ == "__main__":
    unittest.main()
