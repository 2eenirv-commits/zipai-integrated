import unittest
from pathlib import Path

from tools.process_gyeonggi_women_safety import process_guard_houses, process_parcel_lockers, validate


ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "data/raw/gyeonggi/women_safety"


class GyeonggiWomenSafetyProcessorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guard = process_guard_houses(RAW / "gyeonggi_women_safe_guard_house_2025-06-24.csv")
        cls.parcel = process_parcel_lockers(RAW / "gyeonggi_safe_parcel_locker_2026-04-14.csv")

    def test_guard_house_rows_preserve_operation_status_and_official_coordinates(self):
        self.assertEqual(122, len(self.guard))
        self.assertEqual({"김포시", "안성시"}, {row["sigungu_name"] for row in self.guard})
        self.assertEqual(120, sum(row["coordinate_validation"] == "VERIFIED" for row in self.guard))
        self.assertEqual(2, sum(row["coordinate_validation"] == "INACTIVE" for row in self.guard))

    def test_parcel_locker_rows_keep_missing_coordinates_for_verified_geocoding_only(self):
        self.assertEqual(122, len(self.parcel))
        self.assertEqual(16, len({row["sigungu_name"] for row in self.parcel}))
        self.assertEqual(118, sum(row["coordinate_validation"] == "VERIFIED" for row in self.parcel))
        self.assertEqual(4, sum(row["coordinate_validation"] == "NEEDS_GEOCODING" for row in self.parcel))

    def test_combined_rows_are_unique_and_db_ready_only_when_verified(self):
        summary = validate(self.guard + self.parcel)
        self.assertEqual(244, summary["processedRows"])
        self.assertEqual(238, summary["verifiedCoordinates"])
        self.assertEqual(0, summary["duplicates"])
        self.assertEqual(0, summary["requiredNullRows"])


if __name__ == "__main__":
    unittest.main()
