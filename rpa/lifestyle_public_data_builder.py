import csv
import math
import os
import re
from collections import defaultdict
from datetime import date
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
RAW_DIR = BASE_DIR / 'data' / 'lifestyle' / 'raw'
OUTPUT = BASE_DIR / 'data' / 'lifestyle' / 'lifestyle_official_scores.csv'

FILES = {
    'population': RAW_DIR / 'population.csv',
    'hospital': RAW_DIR / 'hospital.csv',
    'large_store': RAW_DIR / 'large_store.csv',
    'park': RAW_DIR / 'park.csv',
    'cctv': RAW_DIR / 'cctv.csv',
    'bus_stop': RAW_DIR / 'bus_stop.csv',
    'school': RAW_DIR / 'school.csv',
    'academy': RAW_DIR / 'academy.csv',
    'library': RAW_DIR / 'library.csv',
    'vehicle': RAW_DIR / 'vehicle.csv',
    'rent': RAW_DIR / 'rent.csv',
}

SIGUN_ALIASES = [
    '시군명', '시군 명', '시군', 'SIGUN_NM', 'sigun_nm', 'sigungu', '시군구명', '시군구 명',
    '시군구별(1)', '시군구별'
]


ADDRESS_ALIASES = [
    '소재지도로명주소', '소재지지번주소',
    '도로명주소', '지번주소', '주소',
    'ROAD_NM_ADDR', 'REFINE_ROADNM_ADDR',
    'REFINE_LOTNO_ADDR'
]

POP_ALIASES = [
    '인구수', '총인구수', '총인구', '인구', 'population', 'POPULATION', '주민등록인구'
]
PARK_AREA_ALIASES = [
    '공원면적', '공원면적(㎡)', '공원면적(제곱미터)', 'park_area', 'PARK_AREA', '면적'
]
CCTV_COUNT_ALIASES = [
    '카메라대수', '카메라 대수', 'CCTV대수', 'cctv_count', 'CCTV_CNT', '설치대수'
]
BUSINESS_STATUS_ALIASES = [
    '영업상태명', '영업상태', '상태', 'BSN_STATE_NM', 'business_status'
]

VEHICLE_COUNT_ALIASES = [
    '자동차등록대수', '자동차 등록대수', '등록대수', '자동차수', '자동차 수',
    '총계', '합계', '계', 'VEHICLE_CNT', 'TOTAL_CNT'
]
DEPOSIT_ALIASES = [
    '보증금액', '보증금', '임대보증금', 'DEPOSIT', 'RENT_GTN'
]
MONTHLY_RENT_ALIASES = [
    '월세금액', '월세', '월임대료', 'MONTHLY_RENT', 'MONTHLY_RENT_AMT'
]

RENT_AGGREGATE_PRICE_COLUMNS = [
    '일반주택전월세실거래가격',
    '아파트전월세실거래가격',
    '빌라전월세실거래가격'
]
YEAR_ALIASES = ['년도', '연도', 'YEAR', 'year']

GYEONGGI_CITIES = [
    '수원시','성남시','고양시','용인시','부천시','안산시','안양시','남양주시','화성시','평택시',
    '의정부시','시흥시','파주시','김포시','광명시','광주시','군포시','하남시','오산시','양주시',
    '이천시','구리시','안성시','포천시','의왕시','여주시','동두천시','과천시','양평군','가평군','연천군'
]
AREA_CODE = {
    '수원시':'GG-SUWON','성남시':'GG-SEONGNAM','고양시':'GG-GOYANG','용인시':'GG-YONGIN','부천시':'GG-BUCHEON',
    '안산시':'GG-ANSAN','안양시':'GG-ANYANG','남양주시':'GG-NAMYANGJU','화성시':'GG-HWASEONG','평택시':'GG-PYEONGTAEK',
    '의정부시':'GG-UIJEONGBU','시흥시':'GG-SIHEUNG','파주시':'GG-PAJU','김포시':'GG-GIMPO','광명시':'GG-GWANGMYEONG',
    '광주시':'GG-GWANGJU','군포시':'GG-GUNPO','하남시':'GG-HANAM','오산시':'GG-OSAN','양주시':'GG-YANGJU',
    '이천시':'GG-ICHEON','구리시':'GG-GURI','안성시':'GG-ANSEONG','포천시':'GG-POCHEON','의왕시':'GG-UIWANG',
    '여주시':'GG-YEOJU','동두천시':'GG-DONGDUCHEON','과천시':'GG-GWACHEON','양평군':'GG-YANGPYEONG','가평군':'GG-GAPYEONG','연천군':'GG-YEONCHEON'
}


def read_csv(path: Path):
    if not path.exists():
        raise FileNotFoundError(f'필수 원천 CSV가 없습니다: {path}')
    encodings = ('utf-8-sig', 'cp949', 'euc-kr')
    last = None
    for enc in encodings:
        try:
            with path.open('r', encoding=enc, newline='') as f:
                return list(csv.DictReader(f))
        except UnicodeDecodeError as e:
            last = e
    raise last


def find_col(rows, aliases, required=True):
    if not rows:
        if required:
            raise ValueError('CSV에 데이터가 없습니다.')
        return None
    columns = list(rows[0].keys())
    normalized = {normalize_key(c): c for c in columns if c is not None}
    for alias in aliases:
        key = normalize_key(alias)
        if key in normalized:
            return normalized[key]
    if required:
        raise ValueError(f'필수 컬럼을 찾지 못했습니다. 후보={aliases}, 실제={columns}')
    return None


def normalize_key(value):
    return re.sub(r'[^0-9A-Za-z가-힣]', '', str(value or '')).lower()


def normalize_sigun(value):
    text = re.sub(r'\s+', ' ', str(value or '').strip())
    if not text:
        return None
    for city in GYEONGGI_CITIES:
        if city in text:
            return city
    return text if text in GYEONGGI_CITIES else None


def number(value):
    text = str(value or '').strip().replace(',', '')
    if not text:
        return None
    match = re.search(r'-?\d+(?:\.\d+)?', text)
    return float(match.group()) if match else None


def percentile_scores(values_by_city, higher_is_better=True):
    available = {
        c: v for c, v in values_by_city.items()
        if v is not None and math.isfinite(v)
    }
    if not available:
        return {}

    unique_values = sorted(set(available.values()))
    if len(unique_values) == 1:
        # 모든 시군의 값이 같으면 상대 우열이 없으므로 중립점수.
        base_scores = {unique_values[0]: 50}
    else:
        base_scores = {
            value: round(index * 100 / (len(unique_values) - 1))
            for index, value in enumerate(unique_values)
        }

    scores = {}
    for city, value in available.items():
        score = base_scores[value]
        scores[city] = score if higher_is_better else 100 - score

    return scores


def population_by_city(rows):
    if not rows:
        return {}

    city_col = find_col(rows, SIGUN_ALIASES)

    pop_col = find_col(rows, POP_ALIASES, required=False)
    if not pop_col:
        columns = [c for c in rows[0].keys() if c is not None]
        month_columns = [
            c for c in columns
            if re.fullmatch(r'\d{4}\.\d{1,2}', str(c).strip())
        ]
        if month_columns:
            pop_col = sorted(
                month_columns,
                key=lambda value: tuple(
                    int(part) for part in str(value).split('.')
                )
            )[-1]

    if not pop_col:
        columns = list(rows[0].keys())
        raise ValueError(
            '인구 컬럼을 찾지 못했습니다. '
            f'후보={POP_ALIASES} 또는 YYYY.MM 형식, 실제={columns}'
        )

    columns = list(rows[0].keys())
    subtotal_col = None
    for candidate in ('시군구별(2)', '구분', '항목'):
        if candidate in columns:
            subtotal_col = candidate
            break

    result = {}
    for row in rows:
        if subtotal_col:
            subtotal_value = str(row.get(subtotal_col, '') or '').strip()
            if subtotal_value and subtotal_value not in {'소계', '계', '합계'}:
                continue

        city = normalize_sigun(row.get(city_col))
        if not city:
            continue

        pop = number(row.get(pop_col))
        if pop is None or pop <= 0:
            continue

        result[city] = pop

    return result


def city_from_address(value):
    address = str(value or '').strip()
    if not address:
        return None
    for city in GYEONGGI_CITIES:
        if city in address:
            return city
    return None


def find_address_cols(rows):
    cols = []
    for alias in ADDRESS_ALIASES:
        col = find_col(rows, [alias], required=False)
        if col and col not in cols:
            cols.append(col)
    return cols


def resolve_city(row, city_col=None, address_cols=None):
    if city_col:
        city = normalize_sigun(row.get(city_col))
        if city:
            return city

    for col in address_cols or []:
        city = city_from_address(row.get(col))
        if city:
            return city

    return None


def count_rows_by_city(rows, status_filter=False):
    if not rows:
        return {}

    city_col = find_col(rows, SIGUN_ALIASES, required=False)
    address_cols = find_address_cols(rows)

    if not city_col and not address_cols:
        columns = list(rows[0].keys())
        raise ValueError(
            '시군 컬럼 또는 주소 컬럼을 찾지 못했습니다. '
            f'시군 후보={SIGUN_ALIASES}, 주소 후보={ADDRESS_ALIASES}, 실제={columns}'
        )

    status_col = find_col(rows, BUSINESS_STATUS_ALIASES, required=False) if status_filter else None
    result = defaultdict(int, {city: 0 for city in GYEONGGI_CITIES})

    for row in rows:
        city = resolve_city(row, city_col=city_col, address_cols=address_cols)
        if not city:
            continue

        if status_col:
            status = str(row.get(status_col) or '').strip()
            if status and not any(word in status for word in ('영업', '정상', '운영')):
                continue

        result[city] += 1

    return dict(result)


def park_area_by_city(rows):
    if not rows:
        return {}

    city_col = find_col(rows, SIGUN_ALIASES, required=False)
    address_cols = find_address_cols(rows)
    area_col = find_col(rows, PARK_AREA_ALIASES)

    if not city_col and not address_cols:
        columns = list(rows[0].keys())
        raise ValueError(
            '공원 CSV에서 시군 컬럼 또는 주소 컬럼을 찾지 못했습니다. '
            f'실제={columns}'
        )

    result = defaultdict(float, {city: 0.0 for city in GYEONGGI_CITIES})
    for row in rows:
        city = resolve_city(row, city_col=city_col, address_cols=address_cols)
        area = number(row.get(area_col))
        if city and area is not None and area >= 0:
            result[city] += area

    return dict(result)


def cctv_by_city(rows):
    if not rows:
        return {}

    city_col = find_col(rows, SIGUN_ALIASES, required=False)
    address_cols = find_address_cols(rows)
    count_col = find_col(rows, CCTV_COUNT_ALIASES, required=False)

    if not city_col and not address_cols:
        columns = list(rows[0].keys())
        raise ValueError(
            'CCTV CSV에서 시군 컬럼 또는 주소 컬럼을 찾지 못했습니다. '
            f'실제={columns}'
        )

    result = defaultdict(float, {city: 0.0 for city in GYEONGGI_CITIES})
    for row in rows:
        city = resolve_city(row, city_col=city_col, address_cols=address_cols)
        if not city:
            continue

        count = number(row.get(count_col)) if count_col else 1
        result[city] += 1 if count is None else max(count, 0)

    return dict(result)



def read_csv_optional(path: Path):
    if not path.exists():
        return []
    return read_csv(path)


def median(values):
    clean = sorted(v for v in values if v is not None and math.isfinite(v))
    if not clean:
        return None
    n = len(clean)
    mid = n // 2
    if n % 2:
        return clean[mid]
    return (clean[mid - 1] + clean[mid]) / 2


def vehicle_count_by_city(rows):
    if not rows:
        return {}

    city_col = find_col(rows, SIGUN_ALIASES, required=False)
    address_cols = find_address_cols(rows)
    count_col = find_col(rows, VEHICLE_COUNT_ALIASES, required=False)

    if not city_col and not address_cols:
        columns = list(rows[0].keys())
        raise ValueError(
            '자동차 CSV에서 시군 또는 주소 컬럼을 찾지 못했습니다. '
            f'실제={columns}'
        )
    if not count_col:
        columns = list(rows[0].keys())
        raise ValueError(
            '자동차 등록대수 컬럼을 찾지 못했습니다. '
            f'후보={VEHICLE_COUNT_ALIASES}, 실제={columns}'
        )

    values = defaultdict(list)
    for row in rows:
        city = resolve_city(row, city_col=city_col, address_cols=address_cols)
        value = number(row.get(count_col))
        if city and value is not None and value >= 0:
            values[city].append(value)

    # 일부 집계 CSV는 같은 시군을 차종/용도별로 반복할 수 있다.
    # '계/총계' 성격의 컬럼을 사용하므로 중복합산보다 최대값을 시군 총량으로 사용.
    return {
        city: (max(values.get(city, [])) if values.get(city) else 0.0)
        for city in GYEONGGI_CITIES
    }


def rent_medians_by_city(rows):
    if not rows:
        return {}, {}

    city_col = find_col(rows, SIGUN_ALIASES, required=False)
    address_cols = find_address_cols(rows)

    if not city_col and not address_cols:
        columns = list(rows[0].keys())
        raise ValueError(
            '전월세 CSV에서 시군 또는 주소 컬럼을 찾지 못했습니다. '
            f'실제={columns}'
        )

    # 형식 A: 개별 거래형 CSV (보증금 / 월세 컬럼)
    deposit_col = find_col(rows, DEPOSIT_ALIASES, required=False)
    monthly_col = find_col(rows, MONTHLY_RENT_ALIASES, required=False)

    if deposit_col or monthly_col:
        deposits = defaultdict(list)
        monthlies = defaultdict(list)

        for row in rows:
            city = resolve_city(row, city_col=city_col, address_cols=address_cols)
            if not city:
                continue

            if deposit_col:
                value = number(row.get(deposit_col))
                if value is not None and value >= 0:
                    deposits[city].append(value)

            if monthly_col:
                value = number(row.get(monthly_col))
                if value is not None and value >= 0:
                    monthlies[city].append(value)

        deposit_median = {
            city: median(deposits.get(city, []))
            for city in GYEONGGI_CITIES
        }
        monthly_median = {
            city: median(monthlies.get(city, []))
            for city in GYEONGGI_CITIES
        }
        return deposit_median, monthly_median

    # 형식 B: 경기데이터드림 '시군분석마트실거래가기본'
    # 실제 헤더:
    # 일반주택전월세실거래가격 / 아파트전월세실거래가격 / 빌라전월세실거래가격
    aggregate_cols = [
        col for col in RENT_AGGREGATE_PRICE_COLUMNS
        if col in rows[0]
    ]
    if not aggregate_cols:
        columns = list(rows[0].keys())
        raise ValueError(
            '전월세 가격 컬럼을 찾지 못했습니다. '
            f'개별거래 후보={DEPOSIT_ALIASES + MONTHLY_RENT_ALIASES}, '
            f'집계형 후보={RENT_AGGREGATE_PRICE_COLUMNS}, 실제={columns}'
        )

    year_col = find_col(rows, YEAR_ALIASES, required=False)

    # 같은 시군에 여러 연도가 있으면 가장 최신 연도 행만 사용한다.
    latest_rows = {}
    for row in rows:
        city = resolve_city(row, city_col=city_col, address_cols=address_cols)
        if not city:
            continue

        year_value = number(row.get(year_col)) if year_col else None
        current = latest_rows.get(city)
        if current is None:
            latest_rows[city] = (year_value, row)
        else:
            current_year = current[0]
            if year_value is not None and (current_year is None or year_value > current_year):
                latest_rows[city] = (year_value, row)

    # 주택 유형별 전월세 실거래가격을 각각 반환한다.
    # downstream에서 각 컬럼을 '낮을수록 좋은 비용점수'로 독립 정규화하기 위해
    # 첫 번째 dict는 유형별 평균 가격, 두 번째 dict는 비워두지 않고 동일값으로
    # 반환하던 기존 인터페이스를 사용하지 않는다. 대신 아래 별도 함수가 처리한다.
    # 이 함수 자체는 하위호환을 위해 시군별 유형가격 중앙값(=평균)을 첫 dict에 반환.
    aggregate_price = {}
    for city in GYEONGGI_CITIES:
        item = latest_rows.get(city)
        if not item:
            aggregate_price[city] = None
            continue
        row = item[1]
        values = []
        for col in aggregate_cols:
            value = number(row.get(col))
            if value is not None and value >= 0:
                values.append(value)
        aggregate_price[city] = median(values)

    return aggregate_price, {}


def rent_cost_score_by_city(rows):
    if not rows:
        return {}

    city_col = find_col(rows, SIGUN_ALIASES, required=False)
    address_cols = find_address_cols(rows)
    deposit_col = find_col(rows, DEPOSIT_ALIASES, required=False)
    monthly_col = find_col(rows, MONTHLY_RENT_ALIASES, required=False)

    # 개별 거래형
    if deposit_col or monthly_col:
        deposit_median, monthly_median = rent_medians_by_city(rows)
        parts = []
        if any(v is not None for v in deposit_median.values()):
            parts.append(percentile_scores(deposit_median, higher_is_better=False))
        if any(v is not None for v in monthly_median.values()):
            parts.append(percentile_scores(monthly_median, higher_is_better=False))
        return average_available_scores(*parts) if parts else {}

    # 집계형: 일반주택/아파트/빌라 전월세 실거래가격을 각각 역-percentile 후 평균
    aggregate_cols = [col for col in RENT_AGGREGATE_PRICE_COLUMNS if col in rows[0]]
    if not aggregate_cols:
        return {}

    year_col = find_col(rows, YEAR_ALIASES, required=False)
    latest_rows = {}
    for row in rows:
        city = resolve_city(row, city_col=city_col, address_cols=address_cols)
        if not city:
            continue
        year_value = number(row.get(year_col)) if year_col else None
        current = latest_rows.get(city)
        if current is None or (
            year_value is not None and
            (current[0] is None or year_value > current[0])
        ):
            latest_rows[city] = (year_value, row)

    score_maps = []
    for col in aggregate_cols:
        values = {}
        for city in GYEONGGI_CITIES:
            item = latest_rows.get(city)
            values[city] = number(item[1].get(col)) if item else None
        score_maps.append(percentile_scores(values, higher_is_better=False))

    return average_available_scores(*score_maps)


def per_capita(raw, population, scale):
    result = {}
    for city in GYEONGGI_CITIES:
        pop = population.get(city)
        value = raw.get(city)
        result[city] = None if not pop or value is None else value / pop * scale
    return result



def average_available_scores(*score_maps):
    result = {}
    for city in GYEONGGI_CITIES:
        values = [scores.get(city) for scores in score_maps if scores.get(city) is not None]
        result[city] = None if not values else round(sum(values) / len(values))
    return result

def source_date():
    value = os.getenv('LIFESTYLE_SOURCE_DATE', '').strip()
    return value or date.today().isoformat()


def build():
    population_rows = read_csv(FILES['population'])
    hospital_rows = read_csv(FILES['hospital'])
    large_store_rows = read_csv(FILES['large_store'])
    park_rows = read_csv(FILES['park'])
    cctv_rows = read_csv(FILES['cctv'])
    bus_stop_rows = read_csv(FILES['bus_stop'])
    school_rows = read_csv(FILES['school'])
    academy_rows = read_csv(FILES['academy'])
    library_rows = read_csv(FILES['library'])
    vehicle_rows = read_csv_optional(FILES['vehicle'])
    rent_rows = read_csv_optional(FILES['rent'])

    population = population_by_city(population_rows)
    missing_pop = [c for c in GYEONGGI_CITIES if not population.get(c)]
    if missing_pop:
        raise ValueError('31개 시군 인구가 모두 필요합니다. 누락: ' + ', '.join(missing_pop))

    hospitals = count_rows_by_city(hospital_rows)
    stores = count_rows_by_city(large_store_rows, status_filter=True)
    parks = park_area_by_city(park_rows)
    cctvs = cctv_by_city(cctv_rows)
    bus_stops = count_rows_by_city(bus_stop_rows)
    schools = count_rows_by_city(school_rows)
    academies = count_rows_by_city(academy_rows)
    libraries = count_rows_by_city(library_rows)

    hospital_rate = per_capita(hospitals, population, 10000)
    store_rate = per_capita(stores, population, 100000)
    park_rate = per_capita(parks, population, 1)
    cctv_rate = per_capita(cctvs, population, 1000)
    bus_stop_rate = per_capita(bus_stops, population, 10000)
    school_rate = per_capita(schools, population, 10000)
    academy_rate = per_capita(academies, population, 10000)
    library_rate = per_capita(libraries, population, 100000)

    medical_score = percentile_scores(hospital_rate)
    commercial_score = percentile_scores(store_rate)
    convenience_score = dict(commercial_score)
    park_score = percentile_scores(park_rate)
    safety_score = percentile_scores(cctv_rate)
    transport_score = percentile_scores(bus_stop_rate)
    school_score = percentile_scores(school_rate)
    academy_score = percentile_scores(academy_rate)
    library_score = percentile_scores(library_rate)
    education_score = average_available_scores(school_score, academy_score, library_score)

    # quiet_score: 실제 소음 측정치가 아니라 자동차 등록 압력의 역점수(대체지표).
    quiet_score = {}
    if vehicle_rows:
        vehicles = vehicle_count_by_city(vehicle_rows)
        vehicle_rate = per_capita(vehicles, population, 1000)
        quiet_score = percentile_scores(vehicle_rate, higher_is_better=False)

    # cost_score: 보증금과 월세를 서로 다른 단위로 섞지 않고,
    # 각각의 시군 중앙값을 역-percentile로 계산한 뒤 평균한다.
    cost_score = {}
    if rent_rows:
        cost_score = rent_cost_score_by_city(rent_rows)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        'area_code','sido','sigungu','dong','latitude','longitude',
        'transport_score','convenience_score','medical_score','education_score',
        'park_score','safety_score','commercial_score','quiet_score','cost_score',
        'source_name','source_date'
    ]
    with OUTPUT.open('w', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for city in GYEONGGI_CITIES:
            writer.writerow({
                'area_code': AREA_CODE[city],
                'sido': '경기도',
                'sigungu': city,
                'dong': '', 'latitude': '', 'longitude': '',
                'transport_score': transport_score.get(city, ''),
                'convenience_score': convenience_score.get(city, ''),
                'medical_score': medical_score.get(city, ''),
                'education_score': education_score.get(city, ''),
                'park_score': park_score.get(city, ''),
                'safety_score': safety_score.get(city, ''),
                'commercial_score': commercial_score.get(city, ''),
                'quiet_score': quiet_score.get(city, ''),
                'cost_score': cost_score.get(city, ''),
                'source_name': '경기데이터드림 기반 Lifestyle 공공데이터 정규화',
                'source_date': source_date(),
            })

    print('[OK] 공공데이터 정규화 CSV 생성:', OUTPUT)
    print(' - 의료: 병원 수 / 인구 1만명 → percentile 0~100')
    print(' - 생활편의·상권: 영업 중 대규모점포 수 / 인구 10만명 → percentile 0~100')
    print(' - 공원: 공원면적 / 인구 1명 → percentile 0~100')
    print(' - 안전: CCTV 대수 / 인구 1천명 → percentile 0~100')
    print(' - 교통: 버스정류소 수 / 인구 1만명 → percentile 0~100')
    print(' - 교육: 학교·학원/교습소·도서관 인구대비 percentile 평균 0~100')
    if vehicle_rows:
        print(' - 조용함: 자동차 등록대수 / 인구 1천명 역-percentile 0~100 (소음 대체지표)')
    else:
        print(' - 조용함: vehicle.csv 없음 → NULL 유지')
    if rent_rows:
        if any(col in rent_rows[0] for col in RENT_AGGREGATE_PRICE_COLUMNS):
            print(' - 비용: 일반주택·아파트·빌라 전월세 실거래가격 각각 역-percentile 평균 0~100')
        else:
            print(' - 비용: 시군별 보증금 중앙값 + 월세 중앙값의 각각 역-percentile 평균 0~100')
    else:
        print(' - 비용: rent.csv 없음 → NULL 유지')


if __name__ == '__main__':
    build()
