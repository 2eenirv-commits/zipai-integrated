import unittest
from pathlib import Path

from tools.process_gyeonggi_crime_statistics import process_north, process_south, validate


ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "data/raw/gyeonggi/crime"


class GyeonggiCrimeStatisticsProcessorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.north = process_north(RAW / "police_gyeonggi_north_five_major_crimes_2025.csv")
        cls.south = process_south(RAW / "police_gyeonggi_south_five_major_crimes_2022.csv")

    def test_north_is_agency_total_and_marks_2025_provisional(self):
        self.assertEqual(50, len(self.north))
        self.assertEqual({"경기북부경찰청"}, {row["police_station_name"] for row in self.north})
        self.assertTrue(all(row["provisional"] == "true" for row in self.north if row["year"] == 2025))
        self.assertTrue(all(row["provisional"] == "false" for row in self.north if row["year"] == 2024))
        self.assertTrue(all("2026년 10월" in row["coverage_note"] for row in self.north if row["year"] == 2025))

    def test_south_excludes_headquarters_and_maps_major_cities(self):
        self.assertEqual(155, len(self.south))
        self.assertEqual(31, len({row["police_station_name"] for row in self.south}))
        self.assertEqual(
            {"수원중부경찰서", "수원남부경찰서", "수원서부경찰서"},
            {row["police_station_name"] for row in self.south if row["sigungu_name"] == "수원시"},
        )
        self.assertEqual(
            {"안산단원경찰서", "안산상록경찰서"},
            {row["police_station_name"] for row in self.south if row["sigungu_name"] == "안산시"},
        )
        self.assertEqual(
            {"성남수정경찰서", "성남중원경찰서", "분당경찰서"},
            {row["police_station_name"] for row in self.south if row["sigungu_name"] == "성남시"},
        )

    def test_combined_output_has_no_duplicates(self):
        summary = validate(self.north + self.south)
        self.assertEqual(205, summary["processedRows"])
        self.assertEqual(0, summary["duplicates"])
        self.assertEqual(21, summary["southRegions"])


if __name__ == "__main__":
    unittest.main()
