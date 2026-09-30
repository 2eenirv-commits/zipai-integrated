import csv
import hashlib
import json
import os
import re
import shutil
import sys
import time
from datetime import datetime
from pathlib import Path

import requests
from bs4 import BeautifulSoup

try:
    from pypdf import PdfReader
except ImportError:
    PdfReader = None

try:
    from dotenv import load_dotenv
except ImportError:
    load_dotenv = None

try:
    import mysql.connector
except ImportError:
    mysql = None
else:
    mysql = mysql.connector


# =========================================================
# ZipAI - Happy Housing Crawler
# LH 청약플러스 행복주택 공고 수집 + PDF 다운로드
#
# 수집 범위
# 1. LH 임대주택 공고 목록
# 2. "행복주택" 공고 필터링
# 3. 공고별 상세페이지 POST 조회
# 4. 공고명 / 공고상태 / 유형 / 공고일
# 5. 목록의 지역 / 게시일 / 마감일 / 상태
# 6. 첨부파일명 / 파일 ID 추출
# 7. PDF 첨부파일 다운로드
# 8. PDF 페이지별 텍스트 추출
# 9. 계층별 자격조건 구간 분리
# 10. 구간 미검출 PDF 제목 후보 진단
# 11. 기존 TXT 재사용 + Windows 콘솔 문자 안전 처리
# 12. PDF 문서유형 분류 + LH 계층 제목 형식 확장
# 13. 반복 계층 제목 중 실제 자격조건 위치 선택
# 14. 전체 공고 청년 계층 조건값 + 출산자녀 가산표 구조화(JSON)
# 15. 나머지 4개 계층 조건값 구조화
# 16. 대학생 자동차 미소유 / 고령자 / 완화 신혼부부 보완
# 17. 계층별 JSON 저장경로 통일
# 18. JSON / CSV 저장
# 19. MySQL housing_notice / housing_notice_rule / crawl_history 자동 저장
#
# 주의
# - eligibility_rule DB는 이 파일에서 직접 수정하지 않습니다.
# - 실제 자격기준 해석(PDF/HWPX 본문 분석)은 다음 단계입니다.
# =========================================================


LIST_URL = (
    "https://apply.lh.or.kr/lhapply/apply/wt/wrtanc/"
    "selectWrtancList.do?mi=1026"
)

DETAIL_URL = (
    "https://apply.lh.or.kr/lhapply/apply/wt/wrtanc/"
    "selectWrtancInfo.do"
)

DOWNLOAD_URL = "https://apply.lh.or.kr/lhapply/lhFile.do"

BASE_DIR = Path(__file__).resolve().parent

RAW_DIR = BASE_DIR / "data" / "raw"
PROCESSED_DIR = BASE_DIR / "data" / "processed"
FILES_DIR = BASE_DIR / "data" / "files"
DEBUG_DIR = BASE_DIR / "data" / "debug"
TEXT_DIR = BASE_DIR / "data" / "text"
RULE_SECTION_DIR = BASE_DIR / "data" / "rule_sections"
RULE_VALUE_DIR = BASE_DIR / "data" / "rule_values"
DEBUG_PATH = DEBUG_DIR / "lh_download_debug.txt"

RAW_JSON_PATH = RAW_DIR / "happy_housing_raw.json"
PROCESSED_JSON_PATH = PROCESSED_DIR / "happy_housing_processed.json"
PROCESSED_CSV_PATH = PROCESSED_DIR / "happy_housing_processed.csv"

REQUEST_TIMEOUT = 15
REQUEST_DELAY = 0.5


def create_session():
    """공통 HTTP Session 생성."""
    session = requests.Session()

    session.headers.update({
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/152.0.0.0 Safari/537.36"
        )
    })

    return session


def clean_text(text):
    """공백과 줄바꿈을 정리."""
    if text is None:
        return None

    return " ".join(text.split()).strip()


def safe_console_text(text):
    """
    Windows 터미널(cp949 등)에서 PDF 특수문자 때문에
    print가 중단되지 않도록 안전하게 변환한다.
    """
    value = "" if text is None else str(text)

    try:
        encoding = getattr(sys.stdout, "encoding", None) or "utf-8"
        return value.encode(
            encoding,
            errors="replace"
        ).decode(
            encoding,
            errors="replace"
        )
    except Exception:
        return value


def normalize_date(text):
    """
    2026.08.31 -> 2026-08-31
    변환이 불가능하면 원래 문자열 반환.
    """
    text = clean_text(text)

    if not text:
        return None

    match = re.search(
        r"(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})",
        text
    )

    if not match:
        return text

    year, month, day = match.groups()

    return f"{int(year):04d}-{int(month):02d}-{int(day):02d}"


def extract_title(notice_link):
    """목록의 공고 제목에서 '1일전' 같은 em.day 문구 제거."""
    title_tag = notice_link.find("span")

    if title_tag:
        day_tag = title_tag.find("em", class_="day")

        if day_tag:
            day_tag.extract()

        return clean_text(
            title_tag.get_text(" ", strip=True)
        )

    return clean_text(
        notice_link.get_text(" ", strip=True)
    )


def extract_list_row_data(notice_link):
    """
    목록 <tr>에서 가능한 기본정보 추출.
    LH 페이지 구조가 바뀌면 일부 값은 None이 될 수 있음.
    """
    row = notice_link.find_parent("tr")

    result = {
        "region": None,
        "list_posting_date": None,
        "closing_date": None,
        "list_status": None,
    }

    if not row:
        return result

    cells = row.find_all("td")

    # 현재 LH 목록 구조:
    # 0 번호
    # 1 유형
    # 2 공고명
    # 3 지역
    # 4 첨부
    # 5 게시일
    # 6 마감일
    # 7 상태
    # 8 조회수
    if len(cells) >= 8:
        result["region"] = clean_text(
            cells[3].get_text(" ", strip=True)
        )

        result["list_posting_date"] = normalize_date(
            cells[5].get_text(" ", strip=True)
        )

        result["closing_date"] = normalize_date(
            cells[6].get_text(" ", strip=True)
        )

        result["list_status"] = clean_text(
            cells[7].get_text(" ", strip=True)
        )

    return result


def get_happy_housing_list(session):
    """행복주택 공고 목록 수집."""
    response = session.get(
        LIST_URL,
        timeout=REQUEST_TIMEOUT
    )

    print("목록 상태 코드:", response.status_code)

    response.raise_for_status()

    soup = BeautifulSoup(
        response.text,
        "html.parser"
    )

    notice_links = soup.find_all(
        "a",
        class_="wrtancInfoBtn"
    )

    print("전체 공고 개수:", len(notice_links))

    notices = []

    for i, notice_link in enumerate(
        notice_links,
        start=1
    ):
        title = extract_title(notice_link)

        if not title:
            continue

        if "행복주택" not in title:
            continue

        row_data = extract_list_row_data(
            notice_link
        )

        notice = {
            "source": "LH",
            "number": i,
            "title": title,

            # LH 상세페이지 이동용 값
            "pan_id": notice_link.get("data-id1"),
            "ccr_cnnt_sys_ds_cd": notice_link.get("data-id2"),
            "upp_ais_tp_cd": notice_link.get("data-id3"),
            "ais_tp_cd": notice_link.get("data-id4"),

            # 목록 기본정보
            "region": row_data["region"],
            "list_posting_date": row_data["list_posting_date"],
            "closing_date": row_data["closing_date"],
            "list_status": row_data["list_status"],
        }

        notices.append(notice)

    # pan_id 기준 중복 제거
    unique_notices = {}
    for notice in notices:
        pan_id = notice.get("pan_id")

        if pan_id:
            unique_notices[pan_id] = notice

    result = list(unique_notices.values())

    print("행복주택 공고 개수:", len(result))

    return result


def build_detail_form_data(notice):
    """LH 상세페이지 POST용 form data."""
    return {
        "panId": notice["pan_id"],
        "ccrCnntSysDsCd": notice["ccr_cnnt_sys_ds_cd"],
        "srchUppAisTpCd": "061339",
        "uppAisTpCd": notice["upp_ais_tp_cd"],
        "aisTpCd": notice["ais_tp_cd"],
        "mi": "1026",
        "currPage": "1",
        "srchY": "N",
        "viewType": "",
        "netbgn": "prwrt"
    }


def extract_detail_basic_info(detail_soup):
    """상세페이지 공고상태 / 유형 / 공고일 추출."""
    result = {
        "status": None,
        "housing_type": None,
        "notice_date": None,
    }

    info_list = detail_soup.find(
        "ul",
        class_="bbsV_data"
    )

    if not info_list:
        return result

    items = info_list.find_all("li")

    for item in items:
        label_tag = item.find("strong")

        if not label_tag:
            continue

        label = clean_text(
            label_tag.get_text(" ", strip=True)
        )

        full_text = clean_text(
            item.get_text(" ", strip=True)
        )

        if not full_text or not label:
            continue

        value = full_text.replace(
            label,
            "",
            1
        ).strip()

        if label == "공고상태":
            result["status"] = value

        elif label == "유형":
            result["housing_type"] = value

        elif label == "공고일":
            result["notice_date"] = normalize_date(value)

    return result


def extract_detail_title(detail_soup, fallback_title):
    """실제 공고 영역 안의 h3 제목 추출."""
    notice_view = detail_soup.find(
        "div",
        class_="bbs_ViewA"
    )

    if not notice_view:
        return fallback_title

    title_tag = notice_view.find("h3")

    if not title_tag:
        return fallback_title

    return clean_text(
        title_tag.get_text(" ", strip=True)
    )


def extract_attachments(detail_soup):
    """
    상세페이지 첨부파일 추출.

    예:
    javascript:fileDownLoad('68233835');
    """
    attachments = []

    attachment_area = detail_soup.find(
        "div",
        class_="bbsV_atchmnfl"
    )

    if not attachment_area:
        return attachments

    links = attachment_area.find_all("a")

    for link in links:
        file_name = clean_text(
            link.get_text(" ", strip=True)
        )

        href = link.get("href", "")

        match = re.search(
            r"fileDownLoad\(['\"]?(\d+)['\"]?\)",
            href
        )

        if not match:
            continue

        file_id = match.group(1)

        suffix = Path(file_name).suffix.lower()

        attachments.append({
            "file_id": file_id,
            "file_name": file_name,
            "file_type": suffix.lstrip(".") if suffix else None
        })

    return attachments


def get_detail(session, notice):
    """공고 하나의 상세페이지 조회 및 파싱."""
    form_data = build_detail_form_data(
        notice
    )

    response = session.post(
        DETAIL_URL,
        data=form_data,
        timeout=REQUEST_TIMEOUT
    )

    response.raise_for_status()

    # 첫 번째 상세 응답에서 다운로드 구조를 한 번만 분석한다.
    if not DEBUG_PATH.exists():
        save_download_debug(
            response
        )

    detail_soup = BeautifulSoup(
        response.text,
        "html.parser"
    )

    detail_title = extract_detail_title(
        detail_soup,
        notice["title"]
    )

    basic_info = extract_detail_basic_info(
        detail_soup
    )

    attachments = extract_attachments(
        detail_soup
    )

    result = dict(notice)

    result.update({
        "title": detail_title,
        "status": basic_info["status"],
        "housing_type": basic_info["housing_type"],
        "notice_date": basic_info["notice_date"],
        "attachments": attachments,
        "detail_response_length": len(response.text),
        "crawled_at": datetime.now().isoformat(
            timespec="seconds"
        )
    })

    return result




def save_download_debug(detail_response):
    """
    LH 첨부파일 다운로드 JavaScript / form 구조를
    실제 상세페이지 HTML에서 찾아 텍스트 파일로 저장한다.

    다운로드 방식을 추측하지 않고 정확히 확인하기 위한 진단 함수.
    """
    DEBUG_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    html = detail_response.text

    keywords = [
        "function fileDownLoad",
        "fileDownLoad",
        "wrtFileDownl.do",
        "fileForm",
        "lsSst1",
        "panId1",
        "uppAisTpCd1",
        "aisTpCd1",
        "ccrCnntSysDsCd1"
    ]

    debug_parts = []

    debug_parts.append(
        "===== LH DOWNLOAD DEBUG =====\n"
    )

    for keyword in keywords:
        debug_parts.append(
            f"\n\n===== KEYWORD: {keyword} =====\n"
        )

        start = 0
        found_count = 0

        while True:
            position = html.find(
                keyword,
                start
            )

            if position == -1:
                break

            found_count += 1

            context_start = max(
                0,
                position - 1200
            )

            context_end = min(
                len(html),
                position + 2200
            )

            context = html[
                context_start:context_end
            ]

            debug_parts.append(
                f"\n--- occurrence {found_count} "
                f"/ position {position} ---\n"
            )

            debug_parts.append(
                context
            )

            start = position + len(keyword)

        if found_count == 0:
            debug_parts.append(
                "\nNOT FOUND\n"
            )

    # BeautifulSoup으로 fileForm 자체도 별도 저장
    soup = BeautifulSoup(
        html,
        "html.parser"
    )

    file_form = soup.find(
        "form",
        id="fileForm"
    )

    debug_parts.append(
        "\n\n===== FILE FORM OUTER HTML =====\n"
    )

    if file_form:
        debug_parts.append(
            file_form.prettify()
        )
    else:
        debug_parts.append(
            "fileForm NOT FOUND"
        )

    # 다운로드 관련 script 전체 저장
    debug_parts.append(
        "\n\n===== RELATED SCRIPT BLOCKS =====\n"
    )

    scripts = soup.find_all("script")

    related_script_count = 0

    for script in scripts:
        script_text = script.get_text(
            "\n",
            strip=False
        )

        if (
            "fileDownLoad" in script_text
            or "wrtFileDownl" in script_text
            or "lsSst1" in script_text
        ):
            related_script_count += 1

            debug_parts.append(
                f"\n\n--- SCRIPT {related_script_count} ---\n"
            )

            debug_parts.append(
                script_text
            )

    if related_script_count == 0:
        debug_parts.append(
            "\n관련 script를 찾지 못했습니다.\n"
        )

    DEBUG_PATH.write_text(
        "".join(debug_parts),
        encoding="utf-8"
    )

    print()
    print("====================================")
    print("LH 다운로드 구조 진단 완료")
    print("====================================")
    print("진단 파일:", DEBUG_PATH)


def sanitize_file_name(file_name):
    """Windows에서 사용할 수 없는 파일명 문자를 안전하게 바꾼다."""
    if not file_name:
        return "unknown_file"

    return re.sub(
        r'[<>:"/\\|?*]',
        "_",
        file_name
    )


def download_attachment(session, notice, attachment):
    """
    LH 상세페이지의 실제 JavaScript와 동일하게 첨부파일을 다운로드한다.

    JavaScript:
        location.href = "/lhapply/lhFile.do?fileid=" + fileId

    따라서 POST form이 아니라 GET + fileid 파라미터를 사용한다.
    """
    file_name = sanitize_file_name(
        attachment.get("file_name")
    )

    pan_id = notice.get("pan_id") or "unknown"

    notice_dir = FILES_DIR / pan_id
    notice_dir.mkdir(
        parents=True,
        exist_ok=True
    )

    file_path = notice_dir / file_name

    if file_path.exists() and file_path.stat().st_size > 0:
        return {
            "success": True,
            "path": str(file_path),
            "size": file_path.stat().st_size,
            "skipped": True,
            "message": "이미 존재하는 파일"
        }

    file_id = attachment.get("file_id")

    if not file_id:
        return {
            "success": False,
            "path": None,
            "size": 0,
            "skipped": False,
            "message": "file_id가 없습니다."
        }

    response = session.get(
        DOWNLOAD_URL,
        params={"fileid": file_id},
        timeout=REQUEST_TIMEOUT
    )

    response.raise_for_status()

    content_type = (
        response.headers
        .get("Content-Type", "")
        .lower()
    )

    content = response.content

    if attachment.get("file_type") == "pdf":
        if not content.startswith(b"%PDF"):
            preview = (
                response.text[:200]
                if "text" in content_type or "html" in content_type
                else ""
            )

            return {
                "success": False,
                "path": None,
                "size": len(content),
                "skipped": False,
                "message": (
                    "PDF가 아닌 응답을 받았습니다. "
                    f"Content-Type={content_type}, "
                    f"preview={preview!r}"
                )
            }

    with file_path.open("wb") as file:
        file.write(content)

    return {
        "success": True,
        "path": str(file_path),
        "size": len(content),
        "skipped": False,
        "message": "다운로드 완료"
    }

def download_pdf_files(session, raw_results):
    """수집된 모든 공고의 PDF 첨부파일을 다운로드한다."""
    print()
    print("====================================")
    print("PDF 다운로드 시작")
    print("====================================")

    downloaded_count = 0
    failed_count = 0

    for notice in raw_results:

        attachments = notice.get(
            "attachments",
            []
        )

        pdf_attachments = [
            attachment
            for attachment in attachments
            if attachment.get("file_type") == "pdf"
        ]

        notice["downloaded_files"] = []

        for attachment in pdf_attachments:
            try:
                result = download_attachment(
                    session,
                    notice,
                    attachment
                )

                download_record = {
                    "file_id": attachment.get("file_id"),
                    "file_name": attachment.get("file_name"),
                    "file_type": attachment.get("file_type"),
                    **result
                }

                notice["downloaded_files"].append(
                    download_record
                )

                if result["success"]:
                    downloaded_count += 1

                    print()
                    print("PDF:", attachment.get("file_name"))
                    print("저장:", result["path"])
                    print("크기:", result["size"], "bytes")
                    print("결과:", result["message"])

                else:
                    failed_count += 1

                    print()
                    print("[PDF 다운로드 실패]")
                    print("공고:", notice.get("title"))
                    print("파일:", attachment.get("file_name"))
                    print("이유:", result["message"])

            except requests.RequestException as error:
                failed_count += 1

                notice["downloaded_files"].append({
                    "file_id": attachment.get("file_id"),
                    "file_name": attachment.get("file_name"),
                    "file_type": attachment.get("file_type"),
                    "success": False,
                    "path": None,
                    "size": None,
                    "skipped": False,
                    "message": str(error)
                })

                print()
                print("[PDF 접속 오류]")
                print("공고:", notice.get("title"))
                print("파일:", attachment.get("file_name"))
                print(error)

            except Exception as error:
                failed_count += 1

                print()
                print("[PDF 처리 오류]")
                print("공고:", notice.get("title"))
                print("파일:", attachment.get("file_name"))
                print(error)

            time.sleep(REQUEST_DELAY)

    print()
    print("PDF 다운로드 완료")
    print("성공:", downloaded_count)
    print("실패:", failed_count)

    return downloaded_count, failed_count



def clean_pdf_text(text):
    """PDF에서 추출된 텍스트의 불필요한 제어문자를 정리한다."""
    if text is None:
        return ""

    return (
        text
        .replace("\x00", "")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .strip()
    )


def extract_pdf_text(pdf_path):
    """
    PDF 한 개를 페이지별로 읽어 텍스트를 추출한다.

    반환:
        page_count : PDF 전체 페이지 수
        char_count : 추출된 전체 문자 수
        pages      : 페이지별 텍스트
    """
    if PdfReader is None:
        raise RuntimeError(
            "pypdf가 설치되어 있지 않습니다. "
            "터미널에서 'pip install pypdf'를 먼저 실행해 주세요."
        )

    # 일부 LH 첨부 PDF는 교차참조 표가 완전하지 않습니다. strict=False로
    # 복구 가능한 경고는 허용하고 실제 페이지 추출 실패만 개별 파일 오류로 처리합니다.
    reader = PdfReader(str(pdf_path), strict=False)

    pages = []

    for page_number, page in enumerate(reader.pages, start=1):
        text = clean_pdf_text(
            page.extract_text() or ""
        )

        pages.append({
            "page": page_number,
            "text": text
        })

    char_count = sum(
        len(page["text"])
        for page in pages
    )

    return {
        "page_count": len(pages),
        "char_count": char_count,
        "pages": pages
    }


def save_pdf_text_file(pdf_path, pan_id, extracted):
    """
    추출한 PDF 텍스트를 공고별 폴더에 TXT로 저장한다.

    예:
    data/text/2015122300020647/공고문.txt
    """
    notice_text_dir = TEXT_DIR / (pan_id or "unknown")

    notice_text_dir.mkdir(
        parents=True,
        exist_ok=True
    )

    text_file_name = (
        Path(pdf_path).stem + ".txt"
    )

    text_path = notice_text_dir / text_file_name

    parts = []

    for page in extracted["pages"]:
        parts.append(
            f"===== PAGE {page['page']} / "
            f"{extracted['page_count']} =====\n"
        )
        parts.append(page["text"])
        parts.append("\n\n")

    text_path.write_text(
        "".join(parts),
        encoding="utf-8"
    )

    return text_path


def extract_downloaded_pdf_texts(raw_results):
    """
    다운로드가 성공한 PDF들을 읽어 TXT로 저장한다.

    아직 자격기준을 자동 판정하지 않는다.
    이번 단계의 목적은 'PDF -> 페이지별 텍스트' 변환까지다.
    """
    print()
    print("====================================")
    print("PDF 텍스트 추출 시작")
    print("====================================")

    if PdfReader is None:
        print("pypdf가 설치되어 있지 않습니다.")
        print("다음 명령을 한 번 실행해 주세요:")
        print("pip install pypdf")
        return 0, 0

    success_count = 0
    failed_count = 0

    for notice in raw_results:
        notice["extracted_text_files"] = []

        downloaded_files = notice.get(
            "downloaded_files",
            []
        )

        for downloaded in downloaded_files:
            if not downloaded.get("success"):
                continue

            if downloaded.get("file_type") != "pdf":
                continue

            file_path_text = downloaded.get("path")

            if not file_path_text:
                continue

            pdf_path = Path(file_path_text)

            try:
                if not pdf_path.exists():
                    raise FileNotFoundError(
                        f"PDF 파일이 없습니다: {pdf_path}"
                    )

                notice_text_dir = (
                    TEXT_DIR
                    / (notice.get("pan_id") or "unknown")
                )
                text_path = (
                    notice_text_dir
                    / (pdf_path.stem + ".txt")
                )

                if (
                    text_path.exists()
                    and text_path.stat().st_size > 0
                ):
                    existing_text = text_path.read_text(
                        encoding="utf-8"
                    )

                    page_count = len(
                        re.findall(
                            r"^===== PAGE \d+ / \d+ =====$",
                            existing_text,
                            flags=re.MULTILINE
                        )
                    )

                    extracted = {
                        "page_count": page_count,
                        "char_count": len(existing_text),
                        "pages": []
                    }

                    reused_text = True

                else:
                    extracted = extract_pdf_text(
                        pdf_path
                    )

                    text_path = save_pdf_text_file(
                        pdf_path,
                        notice.get("pan_id"),
                        extracted
                    )

                    reused_text = False

                record = {
                    "file_id": downloaded.get("file_id"),
                    "file_name": downloaded.get("file_name"),
                    "source_pdf_path": str(pdf_path),
                    "text_path": str(text_path),
                    "page_count": extracted["page_count"],
                    "char_count": extracted["char_count"],
                    "success": True,
                    "message": (
                        "기존 TXT 재사용"
                        if reused_text
                        else "텍스트 추출 완료"
                    )
                }

                notice["extracted_text_files"].append(
                    record
                )

                success_count += 1

                print()
                print("PDF:", downloaded.get("file_name"))
                print("페이지:", extracted["page_count"])
                print("문자 수:", extracted["char_count"])
                print("TXT:", text_path)
                print(
                    "결과:",
                    "기존 TXT 재사용"
                    if reused_text
                    else "텍스트 추출 완료"
                )

            except Exception as error:
                failed_count += 1

                notice["extracted_text_files"].append({
                    "file_id": downloaded.get("file_id"),
                    "file_name": downloaded.get("file_name"),
                    "source_pdf_path": file_path_text,
                    "text_path": None,
                    "page_count": None,
                    "char_count": None,
                    "success": False,
                    "message": str(error)
                })

                print()
                print("[PDF 텍스트 추출 실패]")
                print("공고:", notice.get("title"))
                print("파일:", downloaded.get("file_name"))
                print("이유:", safe_console_text(error))

    print()
    print("PDF 텍스트 추출 완료")
    print("성공:", success_count)
    print("실패:", failed_count)

    return success_count, failed_count



RULE_SECTION_PATTERNS = [
    (
        "industrial",
        "산업단지근로자 계층",
        r"산업단지근로자\s*계층"
    ),
    (
        "student",
        "대학생 계층",
        r"대학생\s*계층"
    ),
    (
        "youth",
        "청년 계층",
        r"청년\s*계층"
    ),
    (
        "newlywed_single_parent",
        "신혼부부·한부모가족 계층",
        r"신혼부부\s*[·ㆍ/및, ]*\s*한부모가족\s*계층"
    ),
    (
        "senior",
        "고령자 계층",
        r"고령자\s*계층"
    ),
    (
        "benefit",
        "주거급여수급자 계층",
        r"주거급여\s*수급자\s*계층"
    ),
]

# 실제 상세 자격조건 제목은 공고에 따라 계층 번호가 달라질 수 있다.
# 예: 어떤 공고는 3-1 산업단지근로자, 다른 공고는 3-1 대학생.
# 따라서 번호와 계층을 고정 매핑하지 않고 "3-x + 계층명" 자체를 우선한다.
DETAIL_HEADING_PREFIX = r"3\s*-\s*\d+\s*\.?\s*"
SUMMARY_HEADING_PREFIX = r"[①②③④⑤⑥⑦⑧⑨⑩]\s*"


def normalize_section_search_text(text):
    """PDF 줄바꿈 차이를 줄여 제목 검색을 안정화한다."""
    return re.sub(r"[ \t]+", " ", text or "")


def find_rule_section_starts(full_text):
    """
    계층별 상세 자격조건 제목을 찾는다.

    우선순위:
    1) '3-1. 대학생 계층' 같은 상세 본문 제목
    2) 상세 제목이 없는 공고에서만 '① 대학생 계층' 같은 요약형 제목

    핵심은 3-1/3-2 번호를 특정 계층과 고정 연결하지 않는 것이다.
    공고마다 공급계층 구성에 따라 번호가 달라질 수 있다.
    """
    search_text = normalize_section_search_text(full_text)
    selected = []

    eligibility_keywords = (
        "월평균소득", "소득기준", "총자산", "자산보유",
        "자동차가액", "자동차 가액", "무주택", "청약저축",
        "주택청약", "입주자저축", "소득", "자산",
    )

    for section_key, section_name, name_pattern in RULE_SECTION_PATTERNS:
        detail_pattern = DETAIL_HEADING_PREFIX + name_pattern
        detail_matches = list(re.finditer(
            detail_pattern, search_text, flags=re.IGNORECASE
        ))

        # 상세 본문 제목이 있으면 요약 제목은 완전히 무시한다.
        if detail_matches:
            match = detail_matches[0]
            heading_type = "detail"
        else:
            summary_pattern = SUMMARY_HEADING_PREFIX + name_pattern
            summary_matches = list(re.finditer(
                summary_pattern, search_text, flags=re.IGNORECASE
            ))
            if not summary_matches:
                continue

            # 상세형 제목이 없는 예외 공고에서는 기존 방식처럼
            # 자격조건 키워드가 풍부한 요약형 후보를 선택한다.
            scored = []
            for candidate_match in summary_matches:
                preview = search_text[
                    candidate_match.end():
                    min(len(search_text), candidate_match.end() + 5000)
                ]
                keyword_hits = sum(
                    1 for keyword in eligibility_keywords
                    if keyword in preview
                )
                scored.append((keyword_hits, candidate_match.start(), candidate_match))

            _, _, match = max(scored, key=lambda item: (item[0], item[1]))
            heading_type = "summary_fallback"

        preview = search_text[
            match.end():min(len(search_text), match.end() + 5000)
        ]
        keyword_hits = sum(
            1 for keyword in eligibility_keywords
            if keyword in preview
        )

        selected.append({
            "section_key": section_key,
            "section_name": section_name,
            "start": match.start(),
            "match_end": match.end(),
            "heading_type": heading_type,
            "segment_length": len(preview),
            "keyword_hits": keyword_hits,
            "context_hits": 0,
            "score": keyword_hits * 2500 + min(len(preview), 5000),
        })

    selected.sort(key=lambda item: item["start"])
    return search_text, selected


def split_rule_sections(full_text):
    """
    계층별 자격조건 구간을 분리한다.

    마지막 계층은 다음 큰 장(예: 4. 공급일정)이 나오기 전까지를 사용한다.
    """
    search_text, starts = find_rule_section_starts(
        full_text
    )

    sections = []

    for index, item in enumerate(starts):
        start = item["start"]

        if index + 1 < len(starts):
            end = starts[index + 1]["start"]
        else:
            next_chapter = re.search(
                r"\n\s*4\s*\.\s*",
                search_text[start:]
            )

            if next_chapter:
                end = start + next_chapter.start()
            else:
                end = len(search_text)

        section_text = search_text[start:end].strip()

        sections.append({
            "section_key": item["section_key"],
            "section_name": item["section_name"],
            "char_count": len(section_text),
            "text": section_text,
            "selection_score": item.get("score"),
            "selection_segment_length": item.get(
                "segment_length"
            ),
            "selection_keyword_hits": item.get(
                "keyword_hits"
            ),
            "heading_type": item.get("heading_type")
        })

    return sections


def save_rule_sections(pan_id, pdf_path, sections):
    """
    공고별/계층별 TXT를 저장한다.

    data/rule_sections/<pan_id>/<pdf명>/
        industrial.txt
        student.txt
        youth.txt
        senior.txt
    """
    pdf_stem = sanitize_file_name(
        Path(pdf_path).stem
    )

    output_dir = (
        RULE_SECTION_DIR
        / (pan_id or "unknown")
        / pdf_stem
    )

    output_dir.mkdir(
        parents=True,
        exist_ok=True
    )

    saved = []

    for section in sections:
        text_path = (
            output_dir
            / f"{section['section_key']}.txt"
        )

        text_path.write_text(
            section["text"],
            encoding="utf-8"
        )

        saved.append({
            "section_key": section["section_key"],
            "section_name": section["section_name"],
            "char_count": section["char_count"],
            "text_path": str(text_path),
            "selection_score": section.get(
                "selection_score"
            ),
            "selection_segment_length": section.get(
                "selection_segment_length"
            ),
            "selection_keyword_hits": section.get(
                "selection_keyword_hits"
            ),
            "heading_type": section.get("heading_type")
        })

    return saved





def normalize_rule_value_text(text):
    """조건값 정규식 검색용으로 연속 공백/줄바꿈을 한 칸으로 정리한다."""
    return re.sub(r"\s+", " ", text or "").strip()


def extract_income_amount_table(section_text):
    """
    계층별 자격구간에 포함된 가구원수별 월평균소득 금액표를 구조화한다.

    예: 1인 4,576,036원 이하 120%
        3인 8,168,429원 이하 100% 9,802,115원 이하 120%

    일반소득과 맞벌이 소득이 한 행에 함께 있으면 둘 다 보존한다.
    7인 이상 가구의 추가 1인당 금액도 별도로 저장한다.
    """
    compact = re.sub(r"\s+", "", section_text or "")

    row_pattern = re.compile(
        r"(?<!\d)([1-6])인"
        r"([0-9,]+)원이하(\d{2,3})%"
        r"(?:([0-9,]+)원이하(\d{2,3})%)?"
    )

    rows = []
    seen = set()
    for match in row_pattern.finditer(compact):
        household_size = int(match.group(1))
        general_won = _to_int_number(match.group(2))
        general_percent = _to_int_number(match.group(3))
        dual_won = _to_int_number(match.group(4))
        dual_percent = _to_int_number(match.group(5))

        key = (household_size, general_won, general_percent, dual_won, dual_percent)
        if key in seen:
            continue
        seen.add(key)

        rows.append({
            "household_size": household_size,
            "general_limit_won": general_won,
            "general_limit_manwon": (general_won / 10000) if general_won is not None else None,
            "general_percent": general_percent,
            "dual_income_limit_won": dual_won,
            "dual_income_limit_manwon": (dual_won / 10000) if dual_won is not None else None,
            "dual_income_percent": dual_percent,
        })

    extra_per_person_won = None
    extra_match = re.search(
        r"7인이상(?:의)?가구는6인가구기준소득금액에추가1인당(?:평균금액)?([0-9,]+)원을합산",
        compact,
    )
    if extra_match:
        extra_per_person_won = _to_int_number(extra_match.group(1))

    return {
        "found": bool(rows),
        "rows": rows,
        "extra_per_person_won": extra_per_person_won,
        "extra_per_person_manwon": (
            extra_per_person_won / 10000 if extra_per_person_won is not None else None
        ),
    }


def _to_int_number(text):
    """'34,500' 같은 숫자 문자열을 int로 변환한다."""
    if text is None:
        return None

    try:
        return int(str(text).replace(",", "").strip())
    except (TypeError, ValueError):
        return None


def _evidence_from_match(text, match, padding=35):
    """자동 추출값의 근거가 된 원문 일부를 함께 보존한다."""
    if not match:
        return None

    start = max(0, match.start() - padding)
    end = min(len(text), match.end() + padding)
    return text[start:end].strip()


def _parse_youth_child_addition_rows(section_text):
    """
    청년 계층의 '출생자녀 수에 따른 가산 소득·자산 기준' 표를 구조화한다.

    PDF 표는 pypdf 추출 시 셀 사이 공백/줄바꿈이 사라질 수 있으므로
    공백을 제거한 compact 문자열을 사용한다. 현재 LH 표의 두 신청자 범위
    ('세대주인 청년(세대기준)', '세대원(본인)')를 각각 분리한 뒤 행을 읽는다.
    """
    compact = re.sub(r"\s+", "", section_text or "")

    table_start = compact.find("구분가구원수출생자녀수")
    if table_start < 0:
        table_start = compact.find("출생자녀수(태아포함)최종소득·자산기준")
    if table_start < 0:
        return {
            "found": False,
            "rows": [],
            "evidence": None,
        }

    table_text = compact[table_start:]

    # 다음 계층 제목이 이어지는 경우 그 앞까지만 표 영역으로 사용한다.
    next_heading = re.search(r"3-\d+\.[가-힣·ㆍ/]+계층", table_text[1:])
    if next_heading:
        table_text = table_text[: next_heading.start() + 1]

    scope_markers = [
        ("household_head", "세대주인청년(세대기준)"),
        ("member_or_single", "세대원(본인)"),
    ]

    rows = []

    for index, (scope_key, marker) in enumerate(scope_markers):
        marker_pos = table_text.find(marker)
        if marker_pos < 0:
            continue

        block_start = marker_pos + len(marker)
        block_end = len(table_text)

        for _, next_marker in scope_markers[index + 1:]:
            next_pos = table_text.find(next_marker, block_start)
            if next_pos >= 0:
                block_end = min(block_end, next_pos)

        block = table_text[block_start:block_end]

        # 행 형식 예:
        # 1인0인120%273,000,000원37,080,000원
        # 2인0인110%273,000,000원37,080,000원1인120%300,...
        row_pattern = re.compile(
            r"(?:(1인|2인|3인이상))?"
            r"(0인|1인|2인이상)"
            r"(\d{2,3})%"
            r"([0-9,]+)원"
            r"([0-9,]+)원"
        )

        current_household_size = None

        for match in row_pattern.finditer(block):
            explicit_household = match.group(1)
            child_count = match.group(2)

            if explicit_household:
                current_household_size = explicit_household

            if current_household_size is None:
                continue

            asset_won = _to_int_number(match.group(4))
            car_won = _to_int_number(match.group(5))

            rows.append({
                "applicant_scope": scope_key,
                "household_size": current_household_size,
                "birth_child_count": child_count,
                "income_percent": _to_int_number(match.group(3)),
                "asset_limit_won": asset_won,
                "asset_limit_manwon": (
                    asset_won // 10000 if asset_won is not None else None
                ),
                "car_limit_won": car_won,
                "car_limit_manwon": (
                    car_won // 10000 if car_won is not None else None
                ),
            })

    evidence = table_text[:1800] if table_text else None

    return {
        "found": bool(rows),
        "rows": rows,
        "evidence": evidence,
    }


def _find_zero_child_table_limits(child_rows):
    """가산표의 0자녀 행에서 대표 자산/자동차 기준값을 찾는다."""
    for row in child_rows or []:
        if row.get("birth_child_count") == "0인":
            return (
                row.get("asset_limit_manwon"),
                row.get("car_limit_manwon"),
            )
    return None, None




def _extract_relaxed_youth_values(full_text):
    """
    입주자격완화 공고의 청년 조건을 원본 전체 TXT에서 보완 추출한다.

    일반 행복주택 공고와 달리 완화 공고는 청년 상세조건의 일부가 다음 제목 뒤로
    밀려 추출되거나, 소득/총자산 요건 자체가 '배제'될 수 있다. 따라서 값을 억지로
    일반 기준으로 바꾸지 않고, 배제 여부와 일반요건 참고값을 별도 필드로 보존한다.
    """
    text = normalize_rule_value_text(full_text)
    # 진단파일을 다시 입력해 테스트하는 경우 붙는 [123] 형태의 줄 번호도 제거한다.
    # 실제 원본 TXT에는 없어도 동작에 영향이 없다.
    text = re.sub(r"\[\d+\]\s*", "", text)
    if not text:
        return None

    relaxed_notice = (
        "입주자격완화" in text
        or "완화조건" in text
        or "소득요건 배제" in text
        or "소득 요건 배제" in text
    )
    if not relaxed_notice:
        return None

    # pypdf의 읽기 순서 때문에 3-3 제목과 실제 청년 조건이 떨어져 있을 수 있다.
    # 실제 '①-㉮ (청년)' 문장을 우선 기준점으로 삼는다.
    anchor_match = re.search(
        r"[➀①]\s*-?\s*㉮\s*\(청년\)",
        text
    )
    if anchor_match:
        youth_text = text[anchor_match.start(): anchor_match.start() + 6500]
    else:
        heading_pos = text.find("청년 계층")
        youth_text = text[heading_pos: heading_pos + 7000] if heading_pos >= 0 else text

    age_match = re.search(
        r"(?:만\s*)?(\d{1,2})\s*세\s*이상\s*(?:만\s*)?(\d{1,2})\s*세\s*이하",
        youth_text
    )
    work_year_match = re.search(
        r"소득이\s*있는\s*업무에\s*종사한\s*기간이(?:\s*총)?(?:\s*[【\[]?완화조건[】\]]?)?\s*(\d+)\s*년\s*이내",
        youth_text
    )
    if not work_year_match:
        work_year_match = re.search(
            r"사회초년생[^.]{0,220}?(?:완화조건[^0-9]{0,30})?(\d+)\s*년\s*이내",
            youth_text
        )

    standard_income_match = re.search(
        r"월평균소득이.*?월평균소득의\s*(\d{2,3})\s*퍼센트\s*이하",
        youth_text
    )
    standard_asset_match = re.search(
        r"일\s*반\s*요건\s*:\s*해당세대가.*?총\s*자산가액\s*합산기준이\s*([0-9,]+)\s*만원\s*이하",
        youth_text
    )
    if not standard_asset_match:
        standard_asset_match = re.search(
            r"해당세대가\s*보유하고\s*있는\s*총\s*자산가액\s*합산기준이\s*([0-9,]+)\s*만원\s*이하",
            youth_text
        )

    car_match = re.search(
        r"자동차가액이\s*([0-9,]+)\s*만원\s*이하",
        youth_text
    )

    income_exempt = bool(re.search(r"소득\s*요건\s*배제", youth_text))
    asset_exempt = bool(re.search(r"(?:총\s*)?자산(?:가액)?\s*요건\s*배제|총\s*자산가액\s*배제", youth_text))

    # 같은 공고 안에서 단지별 완화 방식이 다르면 별도 변형이 존재함을 명시한다.
    has_jincheon_group = bool(re.search(r"진천성석\s*[·ㆍ,]?\s*진천이월", text))
    has_cheongju_uam = "청주우암" in text

    complex_rules = []

    if has_jincheon_group:
        j_pos = re.search(r"진천성석\s*[·ㆍ,]?\s*진천이월", text)
        block = text[j_pos.start(): j_pos.start() + 2400] if j_pos else ""
        j_asset = re.search(r"총\s*자산가액\s*합산기준\s*([0-9,]+)\s*만원\s*이하", block)
        j_car = re.search(r"자동차가액이\s*([0-9,]+)\s*만원\s*이하", block)
        j_work = re.search(r"청년계층\s*\(사회초년생\).*?(\d+)\s*년\s*이내", block)
        complex_rules.append({
            "complex_group": "진천성석·진천이월",
            "income_requirement_exempt": bool(re.search(r"소득\s*요건\s*배제", block)),
            "asset_requirement_exempt": bool(re.search(r"자산\s*요건\s*배제", block)),
            "standard_asset_limit_manwon": _to_int_number(j_asset.group(1)) if j_asset else None,
            "car_limit_manwon": _to_int_number(j_car.group(1)) if j_car else None,
            "social_beginner_work_years_max": _to_int_number(j_work.group(1)) if j_work else None,
        })

    if has_cheongju_uam:
        # '청주우암'은 제목/본문에 여러 번 나올 수 있으므로, 뒤쪽에
        # '입주자격완화 순위'가 실제로 이어지는 위치를 선택한다.
        u_pos = -1
        for u_match in re.finditer(r"청주\s*우암|청주우암", text):
            probe = text[u_match.start(): u_match.start() + 900]
            if "입주자격완화" in probe and "1순위" in probe:
                u_pos = u_match.start()
                break
        if u_pos < 0:
            u_pos = text.find("청주우암")

        block = text[u_pos: u_pos + 5200] if u_pos >= 0 else ""

        rank_matches = list(re.finditer(
            r"(?<![0-9])([123])\s*순위\s*\((?:일반|완화)입주자격\)",
            block
        ))
        rank_blocks = {}
        for idx, rank_match in enumerate(rank_matches):
            rank_no = rank_match.group(1)
            end = rank_matches[idx + 1].start() if idx + 1 < len(rank_matches) else len(block)
            rank_blocks[rank_no] = block[rank_match.start():end]

        def _tier_values(rank_no):
            tier = rank_blocks.get(str(rank_no), "")
            if not tier:
                return None

            one = re.search(r"1\s*인\s*(\d{2,3})%\s*이하", tier)
            two = re.search(r"2\s*인\s*(\d{2,3})%\s*이하", tier)
            three = re.search(r"3\s*인\s*이상\s*(\d{2,3})%\s*이하", tier)
            all_match = re.search(r"전체.*?(\d{2,3})%\s*이하", tier)
            if not all_match:
                # PDF 표 추출에서 '전체 / 1인 / 150% / 2인 / 3인 이상'처럼
                # 순서가 갈라지는 경우를 허용한다.
                all_match = re.search(r"전체.{0,180}?(\d{2,3})%\s*이하", tier)
            work = re.search(r"청년.*?종사한\s*기간\s*(\d+)\s*년\s*이내", tier)

            all_value = _to_int_number(all_match.group(1)) if all_match else None
            return {
                "one_person_percent": _to_int_number(one.group(1)) if one else all_value,
                "two_person_percent": _to_int_number(two.group(1)) if two else all_value,
                "three_or_more_percent": _to_int_number(three.group(1)) if three else all_value,
                "social_beginner_work_years_max": _to_int_number(work.group(1)) if work else None,
            }

        complex_rules.append({
            "complex_group": "청주우암",
            "rank_rules": {
                "rank_1": _tier_values(1),
                "rank_2": _tier_values(2),
                "rank_3": _tier_values(3),
            },
        })

    return {
        "relaxed_notice": True,
        "age_min": _to_int_number(age_match.group(1)) if age_match else None,
        "age_max": _to_int_number(age_match.group(2)) if age_match else None,
        "social_beginner_work_years_max": _to_int_number(work_year_match.group(1)) if work_year_match else None,
        "income_requirement_exempt": income_exempt,
        "asset_requirement_exempt": asset_exempt,
        "standard_income_percent_general": _to_int_number(standard_income_match.group(1)) if standard_income_match else None,
        "standard_asset_limit_manwon": _to_int_number(standard_asset_match.group(1)) if standard_asset_match else None,
        "car_limit_manwon": _to_int_number(car_match.group(1)) if car_match else None,
        "subscription_required": bool(re.search(r"주택청약종합저축.*?(?:가입사실|가입).*?(?:증명|제출)", youth_text)),
        "complex_specific_rules_detected": len(complex_rules) > 1,
        "complex_rules": complex_rules,
        "evidence": youth_text[:2800],
    }

def extract_youth_rule_values(section_text, full_text=None, notice_title=None):
    """
    청년 계층 상세 구간에서 핵심 조건과 출산자녀 가산표를 구조화한다.

    중요:
    - DB의 eligibility_rule을 수정하지 않는다.
    - 본문에 직접 적힌 기본 기준과 표의 0자녀 기준을 둘 다 보존한다.
    - 둘이 다르면 자동으로 경고하고 needs_validation 상태를 유지한다.
    - 추출 근거(evidence)를 함께 저장하여 사람이 검증할 수 있게 한다.
    """
    text = normalize_rule_value_text(section_text)

    age_match = re.search(
        r"(\d{1,2})\s*세\s*이상\s*(\d{1,2})\s*세\s*이하",
        text
    )

    birth_range_match = re.search(
        r"출생일\s*([0-9.]+)\s*[~∼～-]\s*([0-9.]+)",
        text
    )

    work_year_match = re.search(
        r"소득이\s*있는\s*업무에\s*종사한\s*기간이\s*총\s*(\d+)\s*년\s*이내",
        text
    )

    income_general_match = re.search(
        r"월평균소득이.*?월평균소득의\s*(\d{2,3})\s*퍼센트\s*이하",
        text
    )

    income_one_match = re.search(
        r"1\s*인(?:인\s*경우)?[^0-9]{0,30}(\d{2,3})\s*퍼센트",
        text
    )

    income_two_match = re.search(
        r"2\s*인(?:인\s*경우)?[^0-9]{0,30}(\d{2,3})\s*퍼센트",
        text
    )

    asset_match = re.search(
        r"총\s*자산가액이\s*([0-9,]+)\s*만원\s*이하",
        text
    )

    car_match = re.search(
        r"자동차가액이\s*([0-9,]+)\s*만원\s*이하",
        text
    )

    child_two_match = re.search(
        r"출산자녀\s*2\s*인\s*이상\s*(\d{1,3})\s*%\s*가산",
        text
    )

    child_one_match = re.search(
        r"출산자녀\s*1\s*인\s*(\d{1,3})\s*%\s*가산",
        text
    )

    child_definition_date_match = re.search(
        r"출산자녀는\s*[’']?(\d{2,4})\.\s*(\d{1,2})\.\s*(\d{1,2})\s*이후",
        text
    )

    age_min = _to_int_number(age_match.group(1)) if age_match else None
    age_max = _to_int_number(age_match.group(2)) if age_match else None
    work_years_max = _to_int_number(work_year_match.group(1)) if work_year_match else None

    income_general = _to_int_number(income_general_match.group(1)) if income_general_match else None
    income_one = _to_int_number(income_one_match.group(1)) if income_one_match else None
    income_two = _to_int_number(income_two_match.group(1)) if income_two_match else None

    asset_manwon = _to_int_number(asset_match.group(1)) if asset_match else None
    car_manwon = _to_int_number(car_match.group(1)) if car_match else None

    unmarried_required = bool(re.search(r"혼인\s*중이\s*아닐\s*것", text))
    homeless_required = "무주택자로서" in text or "무주택자" in text[:700]
    subscription_required = bool(
        re.search(
            r"주택청약종합저축.*?(?:가입사실|가입).*?(?:증명|제출)",
            text
        )
    )

    child_addition_exists = bool(
        child_two_match or child_one_match or "출생자녀 수에 따른 가산" in text
    )

    child_table = _parse_youth_child_addition_rows(section_text)
    child_rows = child_table.get("rows", [])
    zero_child_asset_manwon, zero_child_car_manwon = _find_zero_child_table_limits(child_rows)

    if child_definition_date_match:
        year = _to_int_number(child_definition_date_match.group(1))
        month = _to_int_number(child_definition_date_match.group(2))
        day = _to_int_number(child_definition_date_match.group(3))
        if year is not None and year < 100:
            year += 2000
        child_definition_from = f"{year:04d}-{month:02d}-{day:02d}" if None not in (year, month, day) else None
    else:
        child_definition_from = None

    relaxed = _extract_relaxed_youth_values(full_text) if full_text else None

    # 입주자격완화 공고는 일반 상세구간에서 값이 누락될 수 있으므로
    # 전체 공고문에서 찾은 완화조건으로 보완한다. 소득/총자산이 '배제'된 경우
    # 일반 기준 숫자를 실효 기준으로 오인하지 않도록 별도 참고필드에만 저장한다.
    income_requirement_exempt = bool(relaxed and relaxed.get("income_requirement_exempt"))
    asset_requirement_exempt = bool(relaxed and relaxed.get("asset_requirement_exempt"))
    standard_income_general = relaxed.get("standard_income_percent_general") if relaxed else None
    standard_asset_manwon = relaxed.get("standard_asset_limit_manwon") if relaxed else None

    if relaxed:
        age_min = age_min if age_min is not None else relaxed.get("age_min")
        age_max = age_max if age_max is not None else relaxed.get("age_max")
        work_years_max = work_years_max if work_years_max is not None else relaxed.get("social_beginner_work_years_max")
        car_manwon = car_manwon if car_manwon is not None else relaxed.get("car_limit_manwon")
        subscription_required = subscription_required or bool(relaxed.get("subscription_required"))

    warnings = []

    required_core = {
        "age_min": age_min,
        "age_max": age_max,
        "car_limit_manwon": car_manwon,
    }
    if not income_requirement_exempt:
        required_core["income_percent_general"] = income_general
    if not asset_requirement_exempt:
        required_core["asset_limit_manwon"] = asset_manwon

    missing = [key for key, value in required_core.items() if value is None]
    if missing:
        warnings.append("핵심 자동 추출 누락: " + ", ".join(missing))

    if child_addition_exists and not child_table.get("found"):
        warnings.append("출산자녀 가산표 문구는 있으나 상세 표 행을 자동 구조화하지 못했습니다.")

    if child_table.get("found"):
        if (
            asset_manwon is not None
            and zero_child_asset_manwon is not None
            and asset_manwon != zero_child_asset_manwon
        ):
            warnings.append(
                "본문 기본 총자산 기준과 가산표 0자녀 총자산 기준이 서로 다릅니다. "
                f"본문={asset_manwon}만원, 표={zero_child_asset_manwon}만원"
            )

        if (
            car_manwon is not None
            and zero_child_car_manwon is not None
            and car_manwon != zero_child_car_manwon
        ):
            warnings.append(
                "본문 기본 자동차 기준과 가산표 0자녀 자동차 기준이 서로 다릅니다. "
                f"본문={car_manwon}만원, 표={zero_child_car_manwon}만원"
            )

    if relaxed and relaxed.get("complex_specific_rules_detected"):
        warnings.append(
            "입주자격완화 공고 안에 단지별 조건이 서로 달라 단지별 규칙을 함께 확인해야 합니다."
        )

    # 출산자녀 가산표가 존재한다는 사실만으로는 검증 실패로 보지 않는다.
    # 핵심값이 모두 추출되고, 표가 구조화되었으며, 본문과 0자녀 표 값이
    # 일치하면 자동 검증 완료(parsed)로 처리한다.
    validation_status = "needs_validation" if warnings else "parsed"

    return {
        "schema_version": "0.3",
        "section_key": "youth",
        "section_name": "청년 계층",
        "extraction_stage": "child_addition_structured",
        "validation_status": validation_status,
        "parsed": {
            "age_min": age_min,
            "age_max": age_max,
            "birth_date_from": birth_range_match.group(1) if birth_range_match else None,
            "birth_date_to": birth_range_match.group(2) if birth_range_match else None,
            "social_beginner_work_years_max": work_years_max,
            "unmarried_required": unmarried_required,
            "homeless_required": homeless_required,
            "income_requirement_exempt": income_requirement_exempt,
            "asset_requirement_exempt": asset_requirement_exempt,
            "income_percent_general": income_general,
            "income_percent_one_person": income_one,
            "income_percent_two_person": income_two,
            "standard_income_percent_general": standard_income_general,
            "asset_limit_manwon": asset_manwon,
            "standard_asset_limit_manwon": standard_asset_manwon,
            "asset_limit_won": asset_manwon * 10000 if asset_manwon is not None else None,
            "car_limit_manwon": car_manwon,
            "car_limit_won": car_manwon * 10000 if car_manwon is not None else None,
            "subscription_required": subscription_required,
            "relaxed_notice": bool(relaxed),
            "complex_specific_rules_detected": bool(relaxed and relaxed.get("complex_specific_rules_detected")),
            "complex_rules": relaxed.get("complex_rules", []) if relaxed else [],
            "child_addition_exists": child_addition_exists,
            "child_addition": {
                "one_child_add_percent": (
                    _to_int_number(child_one_match.group(1)) if child_one_match else None
                ),
                "two_or_more_children_add_percent": (
                    _to_int_number(child_two_match.group(1)) if child_two_match else None
                ),
                "definition_from": child_definition_from,
                "includes_adopted_child": "입양자녀" in text,
                "includes_fetus": "태아" in text,
                "recognized_child_count_max": 2 if "최대 2자녀" in text else None,
                "table_found": child_table.get("found"),
                "table_rows": child_rows,
                "zero_child_table_asset_limit_manwon": zero_child_asset_manwon,
                "zero_child_table_car_limit_manwon": zero_child_car_manwon,
            },
        },
        "evidence": {
            "age": _evidence_from_match(text, age_match),
            "birth_date_range": _evidence_from_match(text, birth_range_match),
            "social_beginner_work_years": _evidence_from_match(text, work_year_match),
            "income_general": _evidence_from_match(text, income_general_match),
            "income_one_person": _evidence_from_match(text, income_one_match),
            "income_two_person": _evidence_from_match(text, income_two_match),
            "asset_limit": _evidence_from_match(text, asset_match),
            "car_limit": _evidence_from_match(text, car_match),
            "child_addition_one_child": _evidence_from_match(text, child_one_match),
            "child_addition_two_or_more": _evidence_from_match(text, child_two_match),
            "child_definition": _evidence_from_match(text, child_definition_date_match),
            "child_addition_table": child_table.get("evidence"),
            "relaxed_notice": relaxed.get("evidence") if relaxed else None,
        },
        "warnings": warnings,
    }


def refine_validation_metadata(result, notice_title=None):
    """needs_validation의 원인을 코드/메시지로 세분화해 JSON에 함께 저장한다.

    기존 validation_status는 유지하되, 화면/Java가 사람이 확인해야 하는 이유를
    구체적으로 설명할 수 있도록 validation.reason_codes / reasons를 추가한다.
    """
    if not isinstance(result, dict):
        return result

    reasons = []
    seen = set()

    def add_reason(code, message, category="manual_review"):
        if code in seen:
            return
        seen.add(code)
        reasons.append({
            "code": code,
            "category": category,
            "message": message,
        })

    rules = result.get("rules") if isinstance(result.get("rules"), dict) else {}
    parsed = result.get("parsed") if isinstance(result.get("parsed"), dict) else {}
    values = {**rules, **parsed}
    title = notice_title or ""
    warnings = result.get("warnings") if isinstance(result.get("warnings"), list) else []

    relaxed_notice = bool(
        "입주자격완화" in title
        or values.get("relaxed_notice")
        or values.get("income_requirement_exempt")
        or values.get("asset_requirement_exempt")
    )
    if relaxed_notice:
        detail = []
        if values.get("income_requirement_exempt"):
            detail.append("소득요건 배제")
        if values.get("asset_requirement_exempt"):
            detail.append("총자산요건 배제")
        suffix = " (" + ", ".join(detail) + ")" if detail else ""
        add_reason(
            "RELAXED_NOTICE",
            "입주자격완화 공고이므로 일반 공고와 다른 완화조건을 확인해야 합니다." + suffix,
            "policy_exception",
        )

    complex_rules = values.get("complex_rules")
    if values.get("complex_specific_rules_detected") or (isinstance(complex_rules, list) and complex_rules):
        add_reason(
            "SITE_SPECIFIC_RULES",
            "단지별 또는 순위별 조건이 서로 달라 해당 단지의 세부조건을 확인해야 합니다.",
            "policy_exception",
        )

    special_rank_rules = values.get("special_rank_rules")
    if result.get("special_notice") or (isinstance(special_rank_rules, dict) and special_rank_rules):
        add_reason(
            "SPECIAL_RANK_RULES",
            "고령자복지주택 등 특수공고로 1·2·3순위별 자격조건을 별도로 확인해야 합니다.",
            "rank_specific",
        )
        if isinstance(special_rank_rules, dict):
            rank1 = special_rank_rules.get("rank1")
            if isinstance(rank1, dict) and rank1.get("income_asset_verification_exempt"):
                add_reason(
                    "RANK1_VERIFICATION_EXEMPT",
                    "1순위는 증빙 충족 시 소득·자산 검증이 생략되는 별도 조건이 있습니다.",
                    "rank_specific",
                )

    child = values.get("child_addition")
    if isinstance(child, dict) and child.get("table_found"):
        zero_asset = child.get("zero_child_table_asset_limit_manwon")
        zero_car = child.get("zero_child_table_car_limit_manwon")
        body_asset = values.get("asset_limit_manwon")
        body_car = values.get("car_limit_manwon")
        if body_asset is not None and zero_asset is not None and body_asset != zero_asset:
            add_reason(
                "SOURCE_CONFLICT_ASSET",
                f"본문 총자산 기준({body_asset}만원)과 출산자녀 가산표 0자녀 기준({zero_asset}만원)이 서로 다릅니다.",
                "source_conflict",
            )
        if body_car is not None and zero_car is not None and body_car != zero_car:
            add_reason(
                "SOURCE_CONFLICT_CAR",
                f"본문 자동차 기준({body_car}만원)과 출산자녀 가산표 0자녀 기준({zero_car}만원)이 서로 다릅니다.",
                "source_conflict",
            )

    for warning in warnings:
        text = str(warning).strip()
        if not text:
            continue
        if "서로 다릅니다" in text:
            if "총자산" in text and "SOURCE_CONFLICT_ASSET" in seen:
                continue
            if "자동차" in text and "SOURCE_CONFLICT_CAR" in seen:
                continue
            code = "SOURCE_CONFLICT"
            category = "source_conflict"
        elif "누락" in text:
            key = re.sub(r"[^A-Za-z0-9가-힣]+", "_", text)[:40].strip("_")
            code = "EXTRACTION_MISSING_" + hashlib.sha1(text.encode("utf-8")).hexdigest()[:8].upper()
            category = "extraction_missing"
        elif "완화" in text:
            code = "RELAXED_RULE_WARNING_" + hashlib.sha1(text.encode("utf-8")).hexdigest()[:8].upper()
            category = "policy_exception"
        elif "순위" in text or "고령자복지주택" in text:
            code = "RANK_RULE_WARNING_" + hashlib.sha1(text.encode("utf-8")).hexdigest()[:8].upper()
            category = "rank_specific"
        else:
            code = "REVIEW_WARNING_" + hashlib.sha1(text.encode("utf-8")).hexdigest()[:8].upper()
            category = "manual_review"
        add_reason(code, text, category)

    if result.get("validation_status") == "needs_validation" and not reasons:
        add_reason(
            "MANUAL_REVIEW_REQUIRED",
            "자동 파싱 결과만으로 최종 확정하기 어려워 공고 원문 확인이 필요합니다.",
            "manual_review",
        )

    result["validation"] = {
        "required": result.get("validation_status") == "needs_validation",
        "reason_codes": [item["code"] for item in reasons],
        "reasons": reasons,
        "reason_count": len(reasons),
        "summary": reasons[0]["message"] if reasons else "자동 검증 완료",
    }
    return result


def save_youth_rule_values(pan_id, source_pdf_path, result):
    """청년 계층 1차 구조화 결과를 공고별 JSON으로 저장한다."""
    result = refine_validation_metadata(result)
    pdf_stem = sanitize_file_name(Path(source_pdf_path).stem)
    output_dir = RULE_VALUE_DIR / (pan_id or "unknown") / pdf_stem
    output_dir.mkdir(parents=True, exist_ok=True)

    output_path = output_dir / "youth.json"
    output_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    return output_path


def _find_source_text_path_for_pdf(notice, source_pdf):
    """
    원본 PDF에 대응하는 전체 TXT 경로를 찾는다.

    현재 크롤러는 PDF 텍스트 추출 결과를 notice["extracted_text_files"]에
    저장한다. 과거 테스트 버전의 notice["pdf_texts"]도 호환을 위해 함께 확인한다.
    """
    candidates = []
    candidates.extend(notice.get("extracted_text_files", []) or [])
    candidates.extend(notice.get("pdf_texts", []) or [])

    for extracted_file in candidates:
        if extracted_file.get("file_name") == source_pdf:
            value = extracted_file.get("text_path")
            if value:
                return Path(value)

    return None


def _build_youth_missing_value_diagnostic(full_text):
    """
    청년 핵심값 자동 추출이 누락된 공고에서 실제 원문 구조를 관찰하기 위한
    진단 텍스트를 만든다. 값을 추측하거나 다른 계층의 값을 대신 사용하지 않는다.
    """
    if not full_text:
        return ""

    lines = full_text.splitlines()
    keywords = (
        "청년",
        "사회초년생",
        "입주자격완화",
        "자격완화",
        "소득요건",
        "소득 요건",
        "소득기준",
        "월평균소득",
        "자산요건",
        "자산 요건",
        "총 자산",
        "총자산",
        "자동차가액",
        "혼인 중이 아닐",
        "주택청약종합저축",
        "출산자녀",
    )

    selected = set()
    for idx, line in enumerate(lines):
        if any(keyword in line for keyword in keywords):
            start = max(0, idx - 3)
            end = min(len(lines), idx + 6)
            selected.update(range(start, end))

    if not selected:
        return ""

    out = []
    last = None
    for idx in sorted(selected):
        if last is not None and idx > last + 1:
            out.append("\n----- 생략 -----\n")
        out.append(f"[{idx + 1}] {lines[idx]}")
        last = idx

    return "\n".join(out).strip()


def save_youth_missing_value_diagnostic(pan_id, source_pdf, diagnostic_text):
    """청년 조건값 누락 진단 내용을 UTF-8 TXT로 저장한다."""
    if not diagnostic_text:
        return None

    pdf_stem = sanitize_file_name(Path(source_pdf or "unknown").stem)
    output_dir = DEBUG_DIR / "youth_value_candidates" / (pan_id or "unknown")
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / f"{pdf_stem}_youth_value_candidates.txt"
    output_path.write_text(diagnostic_text, encoding="utf-8")
    return output_path


def extract_all_youth_rule_values(raw_results):
    """
    현재 수집된 모든 모집공고의 '청년 계층' 상세구간을 구조화한다.

    원칙:
    - 특정 공고의 기준값을 다른 공고에 복사하지 않는다.
    - 각 공고의 원문에서 직접 값을 추출하여 공고별 youth.json으로 저장한다.
    - 본문과 가산표 값이 다르면 어느 한쪽을 임의 선택하지 않고
      needs_validation + warnings 상태로 보존한다.
    - 청년 계층이 없는 공고는 정상적인 '대상 없음'으로 처리한다.
    """
    print()
    print("====================================")
    print("전체 공고 청년 계층 조건값 + 출산자녀 가산표 구조화 시작")
    print("====================================")

    extracted_count = 0
    parsed_count = 0
    validation_count = 0
    missing_section_count = 0
    failed_count = 0

    for notice in raw_results:
        notice_youth_results = []
        youth_section_found = False

        for rule_doc in notice.get("rule_sections", []):
            if rule_doc.get("document_type") != "notice":
                continue

            source_pdf = rule_doc.get("source_pdf")

            for section in rule_doc.get("sections", []):
                if section.get("section_key") != "youth":
                    continue

                youth_section_found = True
                text_path_text = section.get("text_path")

                if not text_path_text:
                    failed_count += 1
                    print()
                    print("[청년 조건값 추출 실패]")
                    print("공고:", notice.get("title"))
                    print("이유: 청년 상세구간 text_path 없음")
                    continue

                text_path = Path(text_path_text)

                if not text_path.exists():
                    failed_count += 1
                    print()
                    print("[청년 조건값 추출 실패]")
                    print("공고:", notice.get("title"))
                    print("이유: 청년 상세구간 TXT 파일 없음")
                    print("경로:", text_path)
                    continue

                try:
                    section_text = text_path.read_text(encoding="utf-8")
                    source_text_path = _find_source_text_path_for_pdf(notice, source_pdf)
                    full_text = None
                    if source_text_path and source_text_path.exists():
                        full_text = source_text_path.read_text(encoding="utf-8")

                    result = extract_youth_rule_values(
                        section_text,
                        full_text=full_text,
                        notice_title=notice.get("title"),
                    )
                    # 가구원수별 실제 월평균소득 상한 금액표를 함께 저장한다.
                    result.setdefault("parsed", {})["income_amount_table"] = extract_income_amount_table(section_text)

                    diagnostic_path = None
                    if result.get("validation_status") == "needs_validation":
                        parsed_for_diag = result.get("parsed") or {}
                        core_keys = (
                            "age_min",
                            "age_max",
                            "income_percent_general",
                            "asset_limit_manwon",
                            "car_limit_manwon",
                        )
                        if any(parsed_for_diag.get(key) is None for key in core_keys):
                            if source_text_path and source_text_path.exists():
                                diagnostic_text = _build_youth_missing_value_diagnostic(full_text or "")
                                diagnostic_path = save_youth_missing_value_diagnostic(
                                    notice.get("pan_id"),
                                    source_pdf,
                                    diagnostic_text,
                                )
                            else:
                                print("진단파일 생성 실패: 원본 전체 TXT 경로를 찾지 못했습니다.")
                                print("원본 PDF:", source_pdf)

                    result.update({
                        "pan_id": notice.get("pan_id"),
                        "notice_title": notice.get("title"),
                        "notice_date": notice.get("notice_date"),
                        "region": notice.get("region"),
                        "source_pdf": source_pdf,
                        "source_section_path": str(text_path),
                        "missing_value_diagnostic_path": (
                            str(diagnostic_path) if diagnostic_path else None
                        ),
                    })

                    source_pdf_path = None
                    extracted_candidates = (
                        notice.get("extracted_text_files", [])
                        or notice.get("pdf_texts", [])
                    )
                    for extracted_file in extracted_candidates:
                        if extracted_file.get("file_name") == source_pdf:
                            source_pdf_path = extracted_file.get("source_pdf_path")
                            break

                    if not source_pdf_path:
                        source_pdf_path = text_path.with_suffix(".pdf")

                    output_path = save_youth_rule_values(
                        notice.get("pan_id"),
                        source_pdf_path,
                        result
                    )

                    notice_youth_results.append({
                        "source_pdf": source_pdf,
                        "json_path": str(output_path),
                        "validation_status": result.get("validation_status"),
                        "parsed": result.get("parsed"),
                        "warnings": result.get("warnings"),
                    })

                    extracted_count += 1
                    if result.get("validation_status") == "parsed":
                        parsed_count += 1
                    else:
                        validation_count += 1

                    parsed = result.get("parsed") or {}
                    child_addition = parsed.get("child_addition") or {}
                    child_rows = child_addition.get("table_rows") or []

                    print()
                    print("공고:", notice.get("title"))
                    print("계층: 청년")
                    print("원본 PDF:", source_pdf)
                    print(
                        "나이:",
                        f"{parsed.get('age_min')}~{parsed.get('age_max')}세"
                    )
                    if parsed.get("income_requirement_exempt"):
                        print(
                            "소득기준: 배제",
                            f"(일반요건 참고 {parsed.get('standard_income_percent_general')}%)"
                        )
                    else:
                        print(
                            "소득기준:",
                            f"기본 {parsed.get('income_percent_general')}% / "
                            f"1인 {parsed.get('income_percent_one_person')}% / "
                            f"2인 {parsed.get('income_percent_two_person')}%"
                        )

                    if parsed.get("asset_requirement_exempt"):
                        print(
                            "총자산: 배제 / 자동차:",
                            f"{parsed.get('car_limit_manwon')}만원",
                            f"(일반 총자산 참고 {parsed.get('standard_asset_limit_manwon')}만원)"
                        )
                    else:
                        print(
                            "본문 총자산/자동차:",
                            f"{parsed.get('asset_limit_manwon')}만원 / "
                            f"{parsed.get('car_limit_manwon')}만원"
                        )

                    if parsed.get("complex_specific_rules_detected"):
                        print("단지별 완화규칙:", len(parsed.get("complex_rules") or []), "개 그룹")
                    print("가산표 행 수:", len(child_rows))
                    print(
                        "가산표 0자녀 자산/자동차:",
                        f"{child_addition.get('zero_child_table_asset_limit_manwon')}만원 / "
                        f"{child_addition.get('zero_child_table_car_limit_manwon')}만원"
                    )
                    print(
                        "가산율:",
                        f"1명 +{child_addition.get('one_child_add_percent')}% / "
                        f"2명 이상 +{child_addition.get('two_or_more_children_add_percent')}%"
                    )
                    print("검증상태:", result.get("validation_status"))
                    print("JSON:", output_path)

                    for warning in result.get("warnings") or []:
                        print("주의:", safe_console_text(warning))

                    if diagnostic_path:
                        print("누락값 진단파일:", diagnostic_path)

                except Exception as error:
                    failed_count += 1
                    print()
                    print("[청년 조건값 추출 오류]")
                    print("공고:", notice.get("title"))
                    print("PDF:", source_pdf)
                    print(safe_console_text(error))

        notice["youth_rule_values"] = notice_youth_results

        if not youth_section_found:
            missing_section_count += 1

    print()
    print("전체 공고 청년 계층 조건값 + 출산자녀 가산표 구조화 완료")
    print("추출 성공:", extracted_count)
    print("자동 검증 완료(parsed):", parsed_count)
    print("사람 검증 필요(needs_validation):", validation_count)
    print("청년 계층 없음:", missing_section_count)
    print("추출 실패:", failed_count)

    return {
        "extracted_count": extracted_count,
        "parsed_count": parsed_count,
        "needs_validation_count": validation_count,
        "missing_section_count": missing_section_count,
        "failed_count": failed_count,
    }

def classify_pdf_document(file_name, full_text):
    """
    첨부 PDF를 자격조건 분석 대상인지 보수적으로 구분한다.

    notice    : 모집공고문 -> 자격조건 분석
    pamphlet  : 팸플릿/리플릿 -> 분석 제외
    reference : 참고/동의서/서식 -> 분석 제외
    unknown   : 명확하지 않음 -> 자동 규칙 추출하지 않음
    """
    name = clean_text(file_name or "") or ""
    sample = clean_text((full_text or "")[:5000]) or ""

    lower_name = name.lower()

    if any(word in name for word in (
        "팜플렛", "팸플릿", "리플릿", "브로슈어"
    )):
        return "pamphlet"

    if any(word in name for word in (
        "[참고]", "참고]", "동의서", "작성대상",
        "제출서류", "서식"
    )):
        return "reference"

    if any(word in name for word in (
        "모집공고", "공고문", "입주자모집"
    )):
        return "notice"

    # 파일명이 애매할 때만 본문을 보조 판단에 사용한다.
    if (
        "입주자 모집" in sample
        and "입주자격" in sample
    ):
        return "notice"

    return "unknown"


def is_senior_special_notice(file_name, full_text):
    """
    '고령자복지주택'처럼 공고 전체가 고령자 중심인 특수 공고인지 확인한다.
    일반 계층 제목 패턴과 섞지 않고 별도 유형으로 표시한다.
    """
    name = clean_text(file_name or "") or ""
    sample = clean_text((full_text or "")[:8000]) or ""

    return (
        "고령자복지주택" in name
        or (
            "고령자복지주택" in sample
            and "만65세 이상" in sample.replace(" ", "")
        )
    )


def save_special_notice_section(pan_id, pdf_path, full_text):
    """
    고령자복지주택 전용 공고는 전체 텍스트를 별도 파일로 보존한다.
    숫자 규칙은 아직 자동 확정하지 않는다.
    """
    pdf_stem = sanitize_file_name(Path(pdf_path).stem)
    output_dir = (
        RULE_SECTION_DIR
        / (pan_id or "unknown")
        / pdf_stem
    )
    output_dir.mkdir(parents=True, exist_ok=True)

    text_path = output_dir / "senior_special_notice.txt"
    text_path.write_text(full_text, encoding="utf-8")

    return {
        "section_key": "senior_special",
        "section_name": "고령자복지주택 전용 공고",
        "char_count": len(full_text),
        "text_path": str(text_path)
    }


RULE_KEYWORDS = (
    "산업단지근로자",
    "대학생",
    "청년",
    "고령자",
    "신혼부부",
    "한부모가족",
    "주거급여수급자",
)


def find_rule_heading_candidates(full_text):
    """
    기존 3-1~3-4 패턴으로 잡히지 않는 공고를 분석하기 위해
    자격계층 키워드가 포함된 '짧은 줄' 후보를 찾는다.

    아직 이 후보를 자격규칙으로 확정하지 않는다.
    공고마다 제목 형식이 어떻게 다른지 확인하기 위한 진단 자료다.
    """
    candidates = []
    seen = set()

    for raw_line in (full_text or "").splitlines():
        line = clean_text(raw_line)

        if not line:
            continue

        # 표/본문의 긴 문장보다 제목 가능성이 높은 짧은 줄을 우선한다.
        if len(line) > 120:
            continue

        if not any(
            keyword in line
            for keyword in RULE_KEYWORDS
        ):
            continue

        if line in seen:
            continue

        seen.add(line)
        candidates.append(line)

        if len(candidates) >= 30:
            break

    return candidates


def save_rule_heading_diagnostic(
        pan_id,
        pdf_path,
        candidates):
    """
    구간을 찾지 못한 PDF의 제목 후보를 debug 폴더에 저장한다.
    """
    diagnostic_dir = (
        DEBUG_DIR
        / "rule_heading_candidates"
        / (pan_id or "unknown")
    )

    diagnostic_dir.mkdir(
        parents=True,
        exist_ok=True
    )

    output_path = (
        diagnostic_dir
        / (
            sanitize_file_name(
                Path(pdf_path).stem
            )
            + "_candidates.txt"
        )
    )

    lines = [
        f"PDF: {pdf_path}",
        f"후보 개수: {len(candidates)}",
        "",
    ]

    if candidates:
        for index, candidate in enumerate(
                candidates,
                start=1):
            lines.append(
                f"{index:02d}. {candidate}"
            )
    else:
        lines.append(
            "자격계층 키워드가 포함된 제목 후보를 찾지 못했습니다."
        )

    output_path.write_text(
        "\n".join(lines),
        encoding="utf-8"
    )

    return output_path


def extract_rule_sections_from_downloaded_pdfs(raw_results):
    """
    텍스트 추출이 성공한 PDF 중 모집공고문에서
    산업단지근로자/대학생/청년/고령자 구간을 자동 분리한다.

    이번 단계에서는 숫자 기준을 DB에 넣지 않는다.
    먼저 '문서의 어느 부분이 어떤 계층 규칙인지'를 정확히 분리한다.
    """
    print()
    print("====================================")
    print("자격조건 구간 분리 시작")
    print("====================================")

    success_count = 0
    no_section_count = 0
    failed_count = 0

    for notice in raw_results:
        notice["rule_sections"] = []

        extracted_files = notice.get(
            "extracted_text_files",
            []
        )

        for extracted_file in extracted_files:
            if not extracted_file.get("success"):
                continue

            text_path_text = extracted_file.get(
                "text_path"
            )

            if not text_path_text:
                continue

            text_path = Path(text_path_text)

            try:
                full_text = text_path.read_text(
                    encoding="utf-8"
                )

                # 팸플릿처럼 텍스트가 없거나 매우 적은 파일은
                # 자격조건 공고문 분석 대상에서 제외한다.
                if len(full_text.strip()) < 500:
                    continue

                document_type = classify_pdf_document(
                    extracted_file.get("file_name"),
                    full_text
                )

                if document_type != "notice":
                    print()
                    print("[자격조건 분석 제외]")
                    print(
                        "PDF:",
                        extracted_file.get("file_name")
                    )
                    print(
                        "문서유형:",
                        document_type
                    )
                    continue

                if is_senior_special_notice(
                    extracted_file.get("file_name"),
                    full_text
                ):
                    special_section = (
                        save_special_notice_section(
                            notice.get("pan_id"),
                            extracted_file.get(
                                "source_pdf_path"
                            ),
                            full_text
                        )
                    )

                    notice["rule_sections"].append({
                        "source_pdf": extracted_file.get(
                            "file_name"
                        ),
                        "document_type": "senior_special_notice",
                        "sections": [special_section]
                    })

                    success_count += 1

                    print()
                    print(
                        "공고:",
                        notice.get("title")
                    )
                    print(
                        "PDF:",
                        extracted_file.get("file_name")
                    )
                    print(
                        " - 고령자복지주택 전용 공고",
                        f"({special_section['char_count']}자)"
                    )
                    continue

                sections = split_rule_sections(
                    full_text
                )

                if not sections:
                    no_section_count += 1

                    candidates = find_rule_heading_candidates(
                        full_text
                    )

                    diagnostic_path = (
                        save_rule_heading_diagnostic(
                            notice.get("pan_id"),
                            extracted_file.get(
                                "source_pdf_path"
                            ),
                            candidates
                        )
                    )

                    print()
                    print("[자격조건 구간 없음]")
                    print(
                        "공고:",
                        notice.get("title")
                    )
                    print(
                        "PDF:",
                        extracted_file.get("file_name")
                    )
                    print(
                        "제목 후보:",
                        len(candidates)
                    )

                    for candidate in candidates[:10]:
                        print(
                            "  >",
                            safe_console_text(candidate)
                        )

                    print(
                        "진단파일:",
                        diagnostic_path
                    )

                    continue

                saved_sections = save_rule_sections(
                    notice.get("pan_id"),
                    extracted_file.get(
                        "source_pdf_path"
                    ),
                    sections
                )

                notice["rule_sections"].append({
                    "source_pdf": extracted_file.get(
                        "file_name"
                    ),
                    "document_type": "notice",
                    "sections": saved_sections
                })

                success_count += 1

                print()
                print(
                    "공고:",
                    notice.get("title")
                )
                print(
                    "PDF:",
                    extracted_file.get("file_name")
                )

                for section in saved_sections:
                    print(
                        " -",
                        section["section_name"],
                        f"({section['char_count']}자)",
                        f"[선택점수 {section.get('selection_score', 0)}, "
                        f"조건키워드 {section.get('selection_keyword_hits', 0)}, "
                        f"제목 {section.get('heading_type', 'unknown')}]"
                    )

            except Exception as error:
                failed_count += 1
                print()
                print("[자격조건 구간 분리 실패]")
                print(
                    "공고:",
                    notice.get("title")
                )
                print(
                    "파일:",
                    extracted_file.get("file_name")
                )
                print("이유:", safe_console_text(error))

    print()
    print("자격조건 구간 분리 완료")
    print("성공 PDF:", success_count)
    print("구간 없음:", no_section_count)
    print("실패:", failed_count)

    return (
        success_count,
        no_section_count,
        failed_count
    )


def find_first_attachment(notice, file_type):
    """attachments에서 원하는 확장자의 첫 파일 찾기."""
    attachments = notice.get(
        "attachments",
        []
    )

    for attachment in attachments:
        if attachment.get("file_type") == file_type:
            return attachment

    return None


def build_processed_notice(raw_notice):
    """
    서비스/DB 연결 전에 사용할 정제 데이터.
    첨부파일 전체 목록은 raw JSON에 보존하고,
    processed에는 대표 PDF/HWPX 정보만 저장.
    """
    pdf = find_first_attachment(
        raw_notice,
        "pdf"
    )

    hwpx = find_first_attachment(
        raw_notice,
        "hwpx"
    )

    return {
        "source": raw_notice.get("source"),
        "pan_id": raw_notice.get("pan_id"),
        "title": raw_notice.get("title"),

        "region": raw_notice.get("region"),

        "notice_date": raw_notice.get("notice_date"),
        "posting_date": (
            raw_notice.get("notice_date")
            or raw_notice.get("list_posting_date")
        ),
        "closing_date": raw_notice.get("closing_date"),

        "status": (
            raw_notice.get("status")
            or raw_notice.get("list_status")
        ),

        "housing_type": raw_notice.get("housing_type"),

        "ccr_cnnt_sys_ds_cd": raw_notice.get(
            "ccr_cnnt_sys_ds_cd"
        ),
        "upp_ais_tp_cd": raw_notice.get(
            "upp_ais_tp_cd"
        ),
        "ais_tp_cd": raw_notice.get(
            "ais_tp_cd"
        ),

        "pdf_file_id": (
            pdf.get("file_id")
            if pdf else None
        ),
        "pdf_file_name": (
            pdf.get("file_name")
            if pdf else None
        ),

        "hwpx_file_id": (
            hwpx.get("file_id")
            if hwpx else None
        ),
        "hwpx_file_name": (
            hwpx.get("file_name")
            if hwpx else None
        ),

        "pdf_text_path": (
            raw_notice.get("extracted_text_files", [{}])[0].get("text_path")
            if raw_notice.get("extracted_text_files")
            else None
        ),

        "detail_endpoint": DETAIL_URL,
        "crawled_at": raw_notice.get("crawled_at")
    }


def save_json(path, data):
    """UTF-8 JSON 저장."""
    path.parent.mkdir(
        parents=True,
        exist_ok=True
    )

    with path.open(
        "w",
        encoding="utf-8"
    ) as file:
        json.dump(
            data,
            file,
            ensure_ascii=False,
            indent=2
        )


def save_csv(path, data):
    """정제 데이터를 CSV로 저장."""
    path.parent.mkdir(
        parents=True,
        exist_ok=True
    )

    if not data:
        return

    fieldnames = list(
        data[0].keys()
    )

    with path.open(
        "w",
        encoding="utf-8-sig",
        newline=""
    ) as file:

        writer = csv.DictWriter(
            file,
            fieldnames=fieldnames
        )

        writer.writeheader()
        writer.writerows(data)


def print_notice_summary(notice, index, total):
    """터미널 확인용 요약 출력."""
    print()
    print(
        f"===== 상세 수집 {index}/{total} ====="
    )
    print("공고명:", notice.get("title"))
    print("pan_id:", notice.get("pan_id"))
    print("지역:", notice.get("region"))
    print("공고상태:", notice.get("status"))
    print("유형:", notice.get("housing_type"))
    print("공고일:", notice.get("notice_date"))
    print("마감일:", notice.get("closing_date"))
    print(
        "첨부파일 수:",
        len(notice.get("attachments", []))
    )



# =========================================================
# 16. 나머지 공급계층 조건값 통합 구조화
#    - 대학생
#    - 신혼부부·한부모가족
#    - 산업단지근로자
#    - 고령자 / 고령자복지주택 전용 공고
# =========================================================


def _match_int(pattern, text, flags=re.IGNORECASE | re.DOTALL, group=1):
    match = re.search(pattern, text or "", flags)
    if not match:
        return None, None
    return _to_int_number(match.group(group)), match


def _extract_common_income_asset_car(text):
    """계층별 문구 차이를 허용해 공통 소득/자산/자동차 값을 읽는다."""
    normalized = normalize_rule_value_text(text)

    income_general, income_general_match = _match_int(
        r"월평균소득(?:이|의)?.{0,180}?월평균소득의\s*(\d{2,3})\s*(?:퍼센트|%)\s*이하",
        normalized,
    )
    if income_general is None:
        income_general, income_general_match = _match_int(
            r"(?:소득기준|소득요건).{0,120}?(\d{2,3})\s*%\s*이하",
            normalized,
        )

    income_one, income_one_match = _match_int(
        r"1\s*인(?:\s*가구|인\s*경우)?.{0,45}?(\d{2,3})\s*(?:퍼센트|%)",
        normalized,
    )
    income_two, income_two_match = _match_int(
        r"2\s*인(?:\s*가구|인\s*경우)?.{0,45}?(\d{2,3})\s*(?:퍼센트|%)",
        normalized,
    )
    dual_income, dual_income_match = _match_int(
        r"맞벌이.{0,100}?(\d{2,3})\s*(?:퍼센트|%)\s*이하",
        normalized,
    )

    asset_limit, asset_match = _match_int(
        r"총\s*자산(?:가액)?(?:\s*합산기준)?(?:이|가)?\s*([0-9,]+)\s*만원\s*이하",
        normalized,
    )
    if asset_limit is None:
        asset_limit, asset_match = _match_int(
            r"총\s*자산가액\s*합산기준이\s*([0-9,]+)\s*만원\s*이하",
            normalized,
        )

    car_limit, car_match = _match_int(
        r"자동차가액(?:이|이\s*)\s*([0-9,]+)\s*만원\s*이하",
        normalized,
    )

    income_exempt = bool(re.search(r"소득\s*요건\s*배제", normalized))
    asset_exempt = bool(re.search(
        r"(?:총\s*)?자산(?:가액)?\s*요건\s*배제|총\s*자산가액\s*배제",
        normalized,
    ))

    return {
        "income_percent_general": income_general,
        "income_percent_one_person": income_one,
        "income_percent_two_person": income_two,
        "income_percent_dual_income": dual_income,
        "asset_limit_manwon": asset_limit,
        "car_limit_manwon": car_limit,
        "income_requirement_exempt": income_exempt,
        "asset_requirement_exempt": asset_exempt,
        "evidence": {
            "income_general": _evidence_from_match(normalized, income_general_match),
            "income_one_person": _evidence_from_match(normalized, income_one_match),
            "income_two_person": _evidence_from_match(normalized, income_two_match),
            "dual_income": _evidence_from_match(normalized, dual_income_match),
            "asset_limit": _evidence_from_match(normalized, asset_match),
            "car_limit": _evidence_from_match(normalized, car_match),
        },
    }


def _extract_child_addition_summary(text):
    normalized = normalize_rule_value_text(text)
    one, one_match = _match_int(
        r"출산자녀\s*1\s*인\s*(\d{1,3})\s*%\s*가산",
        normalized,
    )
    two, two_match = _match_int(
        r"출산자녀\s*2\s*인\s*이상\s*(\d{1,3})\s*%\s*가산",
        normalized,
    )
    return {
        "exists": bool(one_match or two_match or "출생자녀 수에 따른 가산" in normalized),
        "one_child_add_percent": one,
        "two_or_more_children_add_percent": two,
        "evidence": {
            "one_child": _evidence_from_match(normalized, one_match),
            "two_or_more_children": _evidence_from_match(normalized, two_match),
        },
    }


def extract_student_rule_values(section_text, full_text=None, notice_title=None):
    text = normalize_rule_value_text(section_text)
    common = _extract_common_income_asset_car(text)

    graduate_years, graduate_match = _match_int(
        r"졸업(?:\s*또는\s*중퇴|\(또는\s*중퇴\))?.{0,45}?(\d+)\s*년\s*이내",
        text,
    )
    if graduate_years is None:
        graduate_years, graduate_match = _match_int(
            r"졸업.*?중퇴.*?(\d+)\s*년\s*이내",
            text,
        )

    car_no_ownership_pattern = (
        r"자동차(?:가액(?:\s*\([^)]*\))?\s*산출대상\s*)?자동차를\s*소유하고\s*있지\s*(?:않|아니)\s*을?\s*것"
        r"|자동차(?:가액(?:\s*\([^)]*\))?\s*산출대상\s*)?자동차의?\s*미소유"
        r"|자동차\s*미소유"
        r"|자동차가액(?:\s*\([^)]*\))?\s*산출대상\s*자동차를\s*소유하지\s*(?:않|아니)\s*을?\s*것"
        r"|자동차가액(?:\s*\([^)]*\))?\s*산출대상\s*자동차의\s*미소유를\s*입주자격으로"
    )
    car_no_ownership = bool(re.search(car_no_ownership_pattern, text))
    if not car_no_ownership and full_text:
        # 상세구간 경계 때문에 주석/설명문이 잘린 경우 원문 전체에서 대학생 자동차 미소유 문구를 확인한다.
        full_normalized = normalize_rule_value_text(full_text)
        student_windows = []
        for match in re.finditer(r"대학생", full_normalized):
            start = max(0, match.start() - 900)
            end = min(len(full_normalized), match.end() + 1800)
            student_windows.append(full_normalized[start:end])
        car_no_ownership = any(re.search(car_no_ownership_pattern, window) for window in student_windows)
    unmarried_required = bool(re.search(r"혼인\s*중이\s*아닐\s*것", text))
    homeless_required = "무주택자" in text
    enrollment_required = bool(re.search(r"대학.{0,30}재학|입학\s*또는\s*복학\s*예정", text))
    child_addition = _extract_child_addition_summary(text)

    warnings = []
    if common["income_percent_general"] is None and not common["income_requirement_exempt"]:
        warnings.append("소득기준 자동 추출 누락")
    if common["asset_limit_manwon"] is None and not common["asset_requirement_exempt"]:
        warnings.append("총자산 기준 자동 추출 누락")
    if not (car_no_ownership or common["car_limit_manwon"] is not None):
        warnings.append("자동차 조건 자동 추출 누락")

    relaxed = common["income_requirement_exempt"] or common["asset_requirement_exempt"] or "입주자격완화" in (notice_title or "")
    validation_status = "needs_validation" if warnings or relaxed else "parsed"

    return {
        "applicant_type": "student",
        "applicant_name": "대학생 계층",
        "validation_status": validation_status,
        "rules": {
            **{k: v for k, v in common.items() if k != "evidence"},
            "enrollment_or_next_semester_required": enrollment_required,
            "job_seeker_graduation_years_max": graduate_years,
            "unmarried_required": unmarried_required,
            "homeless_required": homeless_required,
            "car_no_ownership_required": car_no_ownership,
            "child_addition": child_addition,
        },
        "evidence": {
            **common["evidence"],
            "graduation_years": _evidence_from_match(text, graduate_match),
            "student_status": text[:1100],
        },
        "warnings": warnings,
    }


def extract_newlywed_rule_values(section_text, full_text=None, notice_title=None):
    text = normalize_rule_value_text(section_text)
    common = _extract_common_income_asset_car(text)

    marriage_years, marriage_match = _match_int(
        r"혼인기간(?:이)?\s*(\d+)\s*년\s*이내",
        text,
    )
    child_age, child_age_match = _match_int(
        r"(\d+)\s*세\s*이하(?:의)?\s*자녀",
        text,
    )
    if child_age is None:
        child_age, child_age_match = _match_int(
            r"자녀(?:의)?\s*연령\s*(\d+)\s*세\s*이하",
            text,
        )

    # 입주자격완화 공고는 일반조건(7년/6세)과 완화조건(10년/9세)을 동시에 보존한다.
    relaxed_marriage_years = None
    relaxed_child_age = None
    relaxed_marriage_match = None
    relaxed_child_age_match = None
    if full_text:
        full_normalized = normalize_rule_value_text(full_text)
        relaxed_marriage_years, relaxed_marriage_match = _match_int(
            r"(?:완화조건|완화내용).{0,450}?혼인기간(?:이)?\s*(\d+)\s*년\s*이내",
            full_normalized,
        )
        if relaxed_marriage_years is None:
            relaxed_marriage_years, relaxed_marriage_match = _match_int(
                r"신혼부부\s*혼인기간\s*(\d+)\s*년\s*이내",
                full_normalized,
            )
        relaxed_child_age, relaxed_child_age_match = _match_int(
            r"(?:완화조건|완화내용).{0,500}?자녀(?:의)?\s*연령\s*(\d+)\s*세\s*이하",
            full_normalized,
        )
        if relaxed_child_age is None:
            relaxed_child_age, relaxed_child_age_match = _match_int(
                r"자녀(?:의)?\s*연령\s*(\d+)\s*세\s*이하(?:면)?\s*가능",
                full_normalized,
            )

    homeless_household_required = bool(re.search(
        r"무주택세대구성원|세대구성원\s*모두\s*무주택자",
        text,
    ))
    subscription_required = bool(re.search(
        r"주택청약종합저축.*?(?:가입사실|가입).*?(?:증명|제출)",
        text,
    ))
    child_addition = _extract_child_addition_summary(text)

    warnings = []
    if marriage_years is None and child_age is None:
        warnings.append("혼인기간/자녀연령 조건 자동 추출 누락")
    if common["income_percent_general"] is None and not common["income_requirement_exempt"]:
        warnings.append("소득기준 자동 추출 누락")
    if common["asset_limit_manwon"] is None and not common["asset_requirement_exempt"]:
        warnings.append("총자산 기준 자동 추출 누락")
    if common["car_limit_manwon"] is None:
        warnings.append("자동차 기준 자동 추출 누락")

    relaxed = common["income_requirement_exempt"] or common["asset_requirement_exempt"] or "입주자격완화" in (notice_title or "")
    validation_status = "needs_validation" if warnings or relaxed else "parsed"

    return {
        "applicant_type": "newlywed_single_parent",
        "applicant_name": "신혼부부·한부모가족 계층",
        "validation_status": validation_status,
        "rules": {
            **{k: v for k, v in common.items() if k != "evidence"},
            "marriage_years_max": marriage_years,
            "child_age_max": child_age,
            "standard_marriage_years_max": marriage_years,
            "standard_child_age_max": child_age,
            "relaxed_marriage_years_max": relaxed_marriage_years,
            "relaxed_child_age_max": relaxed_child_age,
            "homeless_household_required": homeless_household_required,
            "subscription_required": subscription_required,
            "includes_expected_newlywed": "예비신혼부부" in text,
            "includes_single_parent": "한부모" in text,
            "child_addition": child_addition,
        },
        "evidence": {
            **common["evidence"],
            "marriage_years": _evidence_from_match(text, marriage_match),
            "child_age": _evidence_from_match(text, child_age_match),
            "relaxed_marriage_years": _evidence_from_match(
                normalize_rule_value_text(full_text or ""), relaxed_marriage_match
            ),
            "relaxed_child_age": _evidence_from_match(
                normalize_rule_value_text(full_text or ""), relaxed_child_age_match
            ),
        },
        "warnings": warnings,
    }


def extract_industrial_rule_values(section_text, full_text=None, notice_title=None):
    text = normalize_rule_value_text(section_text)
    common = _extract_common_income_asset_car(text)

    work_years, work_match = _match_int(
        r"(?:취업\s*합산\s*기간|소득이\s*있는\s*업무에\s*종사한\s*기간).{0,70}?(\d+)\s*년\s*이내",
        text,
    )
    subscription_required = bool(re.search(
        r"(?:본인\s*또는\s*배우자.*?|본인.*?)주택청약종합저축.*?(?:가입사실|가입).*?(?:증명|제출)",
        text,
    ))
    homeless_household_required = bool(re.search(r"무주택세대구성원|무주택자", text))
    industrial_employment_required = bool(re.search(
        r"산업단지.*?(?:입주기업|교육.?연구기관).*?(?:근무|재직)|산업단지근로자",
        text,
    ))

    warnings = []
    if common["income_percent_general"] is None and not common["income_requirement_exempt"]:
        warnings.append("소득기준 자동 추출 누락")
    if common["asset_limit_manwon"] is None and not common["asset_requirement_exempt"]:
        warnings.append("총자산 기준 자동 추출 누락")
    if common["car_limit_manwon"] is None:
        warnings.append("자동차 기준 자동 추출 누락")

    relaxed = common["income_requirement_exempt"] or common["asset_requirement_exempt"] or "입주자격완화" in (notice_title or "")
    validation_status = "needs_validation" if warnings or relaxed else "parsed"

    return {
        "applicant_type": "industrial",
        "applicant_name": "산업단지근로자 계층",
        "validation_status": validation_status,
        "rules": {
            **{k: v for k, v in common.items() if k != "evidence"},
            "employment_in_industrial_area_required": industrial_employment_required,
            "employment_years_max": work_years,
            "homeless_household_required": homeless_household_required,
            "subscription_required": subscription_required,
        },
        "evidence": {
            **common["evidence"],
            "employment_years": _evidence_from_match(text, work_match),
        },
        "warnings": warnings,
    }


def extract_senior_rule_values(section_text, full_text=None, notice_title=None, special=False):
    text = normalize_rule_value_text(section_text)
    full_normalized = normalize_rule_value_text(full_text or "")

    # 일반 고령자 구간은 section을 우선 사용한다.
    # 고령자복지주택 전용 공고는 전체 문서가 section이므로, 자격 관련 핵심 문구 주변을 함께 검색한다.
    search_text = text
    if special and full_normalized:
        search_text = full_normalized

    common = _extract_common_income_asset_car(search_text)

    # 고령자복지주택은 순위별 소득기준이 서로 다르므로 단일 소득값으로만
    # 축약하지 않고 rank_rules에 원문 구조를 함께 보존한다.
    special_rank_rules = None
    if special:
        rank2_general, rank2_general_match = _match_int(
            r"2\s*순\s*위.{0,260}?소득\s*(\d{2,3})\s*%",
            search_text,
        )
        rank2_one, rank2_one_match = _match_int(
            r"2\s*순\s*위.{0,320}?1\s*인\s*(\d{2,3})\s*%",
            search_text,
        )
        rank2_two, rank2_two_match = _match_int(
            r"2\s*순\s*위.{0,340}?2\s*인\s*(\d{2,3})\s*%",
            search_text,
        )
        rank3_general, rank3_general_match = _match_int(
            r"3\s*순\s*위.{0,220}?월평균소득\s*(\d{2,3})\s*%",
            search_text,
        )
        rank3_one, rank3_one_match = _match_int(
            r"3\s*순\s*위.{0,280}?1\s*인\s*(\d{2,3})\s*%",
            search_text,
        )
        rank3_two, rank3_two_match = _match_int(
            r"3\s*순\s*위.{0,300}?2\s*인\s*(\d{2,3})\s*%",
            search_text,
        )

        # 표 본문 형식 때문에 순위 제목과 퍼센트가 멀리 떨어진 경우의 보조 패턴.
        if rank2_general is None:
            rank2_general, rank2_general_match = _match_int(
                r"월평균\s*소득\s*(70)\s*%", search_text
            )
        if rank3_general is None:
            rank3_general, rank3_general_match = _match_int(
                r"월평균\s*소득\s*(50)\s*%", search_text
            )
        if rank2_one is None:
            rank2_one, rank2_one_match = _match_int(r"1\s*인\s*(90)\s*%", search_text)
        if rank2_two is None:
            rank2_two, rank2_two_match = _match_int(r"2\s*인\s*(80)\s*%", search_text)
        if rank3_one is None:
            rank3_one, rank3_one_match = _match_int(r"1\s*인\s*(70)\s*%", search_text)
        if rank3_two is None:
            rank3_two, rank3_two_match = _match_int(r"2\s*인\s*(60)\s*%", search_text)

        special_asset, special_asset_match = _match_int(
            r"가액\s*합산\s*기준\s*([0-9,]+)\s*만원\s*이하",
            search_text,
        )
        special_car, special_car_match = _match_int(
            r"개별\s*자동차가액\s*([0-9,]+)\s*만원\s*이하",
            search_text,
        )

        # 대표값은 일반입주자(3순위) 기준으로 두되, 실제 판정에는 rank_rules를 사용한다.
        if common.get("income_percent_general") is None:
            common["income_percent_general"] = rank3_general
        if common.get("income_percent_one_person") is None:
            common["income_percent_one_person"] = rank3_one
        if common.get("income_percent_two_person") is None:
            common["income_percent_two_person"] = rank3_two
        if common.get("asset_limit_manwon") is None:
            common["asset_limit_manwon"] = special_asset
        if common.get("car_limit_manwon") is None:
            common["car_limit_manwon"] = special_car

        special_rank_rules = {
            "rank1": {
                "description": "생계급여수급자 또는 의료급여수급자",
                "income_asset_verification_exempt": bool(re.search(
                    r"생계.?의료수급자.{0,180}?소득.?자산\s*검증이\s*생략", search_text
                )),
            },
            "rank2": {
                "income_percent_general": rank2_general,
                "income_percent_one_person": rank2_one,
                "income_percent_two_person": rank2_two,
            },
            "rank3": {
                "income_percent_general": rank3_general,
                "income_percent_one_person": rank3_one,
                "income_percent_two_person": rank3_two,
            },
            "asset_limit_manwon": special_asset,
            "car_limit_manwon": special_car,
        }

    age_patterns = [
        r"만\s*(\d{2})\s*세\s*이상",
        r"(\d{2})\s*세\s*이상",
        r"만\s*(\d{2})\s*세이상",
        r"(\d{2})\s*세이상",
    ]
    age_min = None
    age_match = None
    for pattern in age_patterns:
        age_min, age_match = _match_int(pattern, text)
        if age_min is not None:
            break
    if age_min is None and full_normalized:
        for pattern in age_patterns:
            age_min, age_match = _match_int(pattern, full_normalized)
            if age_min is not None:
                break

    # 일반 section에서 값이 누락되면 같은 공고 전체에서 '고령자' 주변 문구만 제한적으로 재검색한다.
    if not special and full_normalized and (
        common["income_percent_general"] is None
        or common["asset_limit_manwon"] is None
        or common["car_limit_manwon"] is None
    ):
        windows = []
        for match in re.finditer(r"고령자", full_normalized):
            start_pos = max(0, match.start() - 900)
            end_pos = min(len(full_normalized), match.end() + 2200)
            windows.append(full_normalized[start_pos:end_pos])
        for window in windows:
            candidate = _extract_common_income_asset_car(window)
            for key in (
                "income_percent_general", "income_percent_one_person", "income_percent_two_person",
                "income_percent_dual_income", "asset_limit_manwon", "car_limit_manwon"
            ):
                if common.get(key) is None and candidate.get(key) is not None:
                    common[key] = candidate[key]
            common["income_requirement_exempt"] = common["income_requirement_exempt"] or candidate["income_requirement_exempt"]
            common["asset_requirement_exempt"] = common["asset_requirement_exempt"] or candidate["asset_requirement_exempt"]

    homeless_household_required = bool(re.search(
        r"무주택세대구성원|무주택자",
        search_text,
    ))

    # 고령자복지주택의 경우 여러 유형/순위의 기준이 문서 전체에 존재할 수 있어
    # 값을 찾더라도 자동 확정하지 않고 special_notice + needs_validation로 보존한다.
    warnings = []
    if age_min is None:
        warnings.append("최소연령 자동 추출 누락")
    if common["income_percent_general"] is None and not common["income_requirement_exempt"]:
        warnings.append("소득기준 자동 추출 누락")
    if common["asset_limit_manwon"] is None and not common["asset_requirement_exempt"]:
        warnings.append("총자산 기준 자동 추출 누락")
    if common["car_limit_manwon"] is None:
        warnings.append("자동차 기준 자동 추출 누락")
    if special:
        warnings.append("고령자복지주택 전용 공고는 공급유형·순위별 조건을 원문과 함께 최종 검증해야 합니다.")

    relaxed = (
        common["income_requirement_exempt"]
        or common["asset_requirement_exempt"]
        or "입주자격완화" in (notice_title or "")
    )
    validation_status = "needs_validation" if warnings or relaxed or special else "parsed"

    return {
        "applicant_type": "senior",
        "applicant_name": "고령자 계층",
        "special_notice": bool(special),
        "validation_status": validation_status,
        "rules": {
            **{k: v for k, v in common.items() if k != "evidence"},
            "age_min": age_min,
            "homeless_household_required": homeless_household_required,
            "special_rank_rules": special_rank_rules,
        },
        "evidence": {
            **common.get("evidence", {}),
            "age_min": _evidence_from_match(
                text if age_match and age_match.re.pattern in text else (full_normalized or text),
                age_match,
            ) if age_match else None,
        },
        "warnings": warnings,
    }


def save_rule_value_json(pan_id, source_pdf_path, section_key, result):
    """계층별 구조화 JSON을 기존 rule_values 구조에 저장한다."""
    result = refine_validation_metadata(result)
    pdf_stem = sanitize_file_name(Path(source_pdf_path or "unknown").stem)
    output_dir = RULE_VALUE_DIR / (pan_id or "unknown") / pdf_stem
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / f"{section_key}.json"
    output_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return output_path


def _build_rule_value_diagnostic(full_text, section_key):
    """계층별 추출 누락 시 원문 후보문장을 저장한다."""
    if not full_text:
        return ""

    keyword_map = {
        "student": ("대학생", "취업준비생", "재학", "복학", "졸업", "소득요건", "총 자산", "자동차"),
        "newlywed_single_parent": ("신혼부부", "예비신혼부부", "한부모", "혼인기간", "자녀", "소득요건", "총 자산", "자동차"),
        "industrial": ("산업단지근로자", "취업 합산", "근무", "재직", "소득요건", "총 자산", "자동차", "청약"),
        "senior": ("고령자", "65세", "만65세", "무주택", "소득요건", "총 자산", "자동차"),
    }
    keywords = keyword_map.get(section_key, (section_key,))
    lines = full_text.splitlines()
    selected = set()
    for idx, line in enumerate(lines):
        if any(keyword in line for keyword in keywords):
            selected.update(range(max(0, idx - 3), min(len(lines), idx + 6)))

    out = []
    last = None
    for idx in sorted(selected):
        if last is not None and idx > last + 1:
            out.append("\n----- 생략 -----\n")
        out.append(f"[{idx + 1}] {lines[idx]}")
        last = idx
    return "\n".join(out).strip()


def save_rule_value_diagnostic(pan_id, source_pdf, section_key, diagnostic_text):
    if not diagnostic_text:
        return None
    pdf_stem = sanitize_file_name(Path(source_pdf or "unknown").stem)
    output_dir = DEBUG_DIR / f"{section_key}_value_candidates" / (pan_id or "unknown")
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / f"{pdf_stem}_{section_key}_value_candidates.txt"
    output_path.write_text(diagnostic_text, encoding="utf-8")
    return output_path


def _print_rule_value_result(notice, source_pdf, section_key, result, output_path, diagnostic_path=None):
    rules = result.get("rules", {})
    print()
    print("공고:", notice.get("title"))
    print("계층:", result.get("applicant_name"))
    print("원본 PDF:", source_pdf)

    if section_key == "student":
        print("취업준비생 졸업/중퇴 기간:", rules.get("job_seeker_graduation_years_max"), "년 이내")
        print("소득기준:", "배제" if rules.get("income_requirement_exempt") else rules.get("income_percent_general"))
        print("총자산:", "배제" if rules.get("asset_requirement_exempt") else rules.get("asset_limit_manwon"), "만원")
        print("자동차:", "미소유" if rules.get("car_no_ownership_required") else rules.get("car_limit_manwon"))
    elif section_key == "newlywed_single_parent":
        print("일반 혼인기간/자녀연령:", rules.get("standard_marriage_years_max"), "년 /", rules.get("standard_child_age_max"), "세")
        if rules.get("relaxed_marriage_years_max") is not None or rules.get("relaxed_child_age_max") is not None:
            print("완화 혼인기간/자녀연령:", rules.get("relaxed_marriage_years_max"), "년 /", rules.get("relaxed_child_age_max"), "세")
        print("소득기준:", "배제" if rules.get("income_requirement_exempt") else rules.get("income_percent_general"), "/ 맞벌이", rules.get("income_percent_dual_income"))
        print("총자산/자동차:", rules.get("asset_limit_manwon"), "/", rules.get("car_limit_manwon"), "만원")
    elif section_key == "industrial":
        print("산단 재직조건:", rules.get("employment_in_industrial_area_required"))
        print("소득기준:", "배제" if rules.get("income_requirement_exempt") else rules.get("income_percent_general"))
        print("총자산:", "배제" if rules.get("asset_requirement_exempt") else rules.get("asset_limit_manwon"), "/ 자동차:", rules.get("car_limit_manwon"), "만원")
    elif section_key == "senior":
        print("최소나이:", rules.get("age_min"), "세")
        if result.get("special_notice") and rules.get("special_rank_rules"):
            rank_rules = rules.get("special_rank_rules") or {}
            print("소득기준(2순위):", rank_rules.get("rank2"))
            print("소득기준(3순위):", rank_rules.get("rank3"))
            print("1순위 소득·자산 검증 생략:", (rank_rules.get("rank1") or {}).get("income_asset_verification_exempt"))
        else:
            print("소득기준:", "배제" if rules.get("income_requirement_exempt") else rules.get("income_percent_general"))
        print("총자산:", "배제" if rules.get("asset_requirement_exempt") else rules.get("asset_limit_manwon"), "/ 자동차:", rules.get("car_limit_manwon"), "만원")

    print("검증상태:", result.get("validation_status"))
    print("JSON:", output_path)
    for warning in result.get("warnings", []):
        print("주의:", warning)
    if diagnostic_path:
        print("누락값 진단파일:", diagnostic_path)


def extract_remaining_rule_values(raw_results):
    """청년을 제외한 주요 4개 공급계층을 한 번에 구조화한다."""
    print()
    print("====================================")
    print("대학생 + 신혼부부·한부모가족 + 산업단지근로자 + 고령자 조건값 구조화 시작")
    print("====================================")

    extractors = {
        "student": extract_student_rule_values,
        "newlywed_single_parent": extract_newlywed_rule_values,
        "industrial": extract_industrial_rule_values,
        "senior": extract_senior_rule_values,
    }
    summary = {
        key: {"extracted": 0, "parsed": 0, "needs_validation": 0, "missing": 0, "failed": 0}
        for key in extractors
    }

    for notice in raw_results:
        found_in_notice = {key: False for key in extractors}

        for rule_doc in notice.get("rule_sections", []):
            source_pdf = rule_doc.get("source_pdf")
            source_text_path = _find_source_text_path_for_pdf(notice, source_pdf)
            full_text = None
            if source_text_path and source_text_path.exists():
                try:
                    full_text = source_text_path.read_text(encoding="utf-8")
                except Exception:
                    full_text = None

            for section in rule_doc.get("sections", []):
                raw_key = section.get("section_key")
                special = raw_key == "senior_special"
                section_key = "senior" if special else raw_key
                if section_key not in extractors:
                    continue

                found_in_notice[section_key] = True
                text_path_text = section.get("text_path")
                if not text_path_text or not Path(text_path_text).exists():
                    summary[section_key]["failed"] += 1
                    continue

                try:
                    section_text = Path(text_path_text).read_text(encoding="utf-8")
                    if section_key == "senior":
                        result = extract_senior_rule_values(
                            section_text,
                            full_text=full_text,
                            notice_title=notice.get("title"),
                            special=special,
                        )
                    else:
                        result = extractors[section_key](
                            section_text,
                            full_text=full_text,
                            notice_title=notice.get("title"),
                        )

                    # 가구원수별 실제 월평균소득 상한 금액표를 함께 저장한다.
                    result.setdefault("parsed", {})["income_amount_table"] = extract_income_amount_table(section_text)

                    # 모든 계층 JSON 저장경로를 <pan_id>/<원본 PDF 이름>/<계층>.json 으로 통일한다.
                    output_path = save_rule_value_json(
                        notice.get("pan_id"),
                        source_pdf or section.get("text_path"),
                        section_key,
                        result,
                    )

                    diagnostic_path = None
                    if result.get("validation_status") == "needs_validation" and result.get("warnings"):
                        diagnostic_text = _build_rule_value_diagnostic(full_text or section_text, section_key)
                        diagnostic_path = save_rule_value_diagnostic(
                            notice.get("pan_id"),
                            source_pdf,
                            section_key,
                            diagnostic_text,
                        )

                    summary[section_key]["extracted"] += 1
                    summary[section_key][result.get("validation_status", "needs_validation")] += 1
                    _print_rule_value_result(
                        notice, source_pdf, section_key, result, output_path, diagnostic_path
                    )

                except Exception as error:
                    summary[section_key]["failed"] += 1
                    print()
                    print(f"[{section_key} 조건값 추출 실패]")
                    print("공고:", notice.get("title"))
                    print("이유:", safe_console_text(error))

        for key, found in found_in_notice.items():
            if not found:
                summary[key]["missing"] += 1

    print()
    print("전체 계층 조건값 구조화 완료")
    label_map = {
        "student": "대학생",
        "newlywed_single_parent": "신혼부부·한부모가족",
        "industrial": "산업단지근로자",
        "senior": "고령자",
    }
    for key, counts in summary.items():
        print(
            f"{label_map[key]}: 추출 {counts['extracted']} / parsed {counts['parsed']} / "
            f"needs_validation {counts['needs_validation']} / 계층없음 {counts['missing']} / 실패 {counts['failed']}"
        )

    return summary


# =========================================================
# MySQL 저장
# =========================================================


def _load_dotenv_candidates():
    """rpa/.env 또는 프로젝트 루트 .env가 있으면 읽는다."""
    if load_dotenv is None:
        return
    candidates = [
        BASE_DIR / ".env",
        BASE_DIR.parent / ".env",
    ]
    for path in candidates:
        if path.exists():
            load_dotenv(path, override=False)


def _read_spring_datasource_fallback():
    """환경변수가 없을 때 Spring application.yml/properties의 datasource 값을 보조적으로 읽는다."""
    result = {}
    resource_dir = BASE_DIR.parent / "src" / "main" / "resources"
    paths = [resource_dir / "application.yml", resource_dir / "application.yaml", resource_dir / "application.properties"]
    for path in paths:
        if not path.exists():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except Exception:
            continue

        def resolve(value):
            value = (value or "").strip().strip('"').strip("'")
            m = re.fullmatch(r"\$\{([^}:]+)(?::([^}]*))?\}", value)
            if m:
                return os.getenv(m.group(1), m.group(2) or "")
            return value

        if path.suffix == ".properties":
            pairs = {}
            for line in text.splitlines():
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                pairs[k.strip()] = resolve(v)
            url = pairs.get("spring.datasource.url")
            user = pairs.get("spring.datasource.username")
            pwd = pairs.get("spring.datasource.password")
        else:
            url_match = re.search(r"(?m)^\s*url\s*:\s*(.+?)\s*$", text)
            user_match = re.search(r"(?m)^\s*username\s*:\s*(.+?)\s*$", text)
            pwd_match = re.search(r"(?m)^\s*password\s*:\s*(.+?)\s*$", text)
            url = resolve(url_match.group(1)) if url_match else None
            user = resolve(user_match.group(1)) if user_match else None
            pwd = resolve(pwd_match.group(1)) if pwd_match else None

        if url and url.startswith("jdbc:mysql://"):
            m = re.match(r"jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", url)
            if m:
                result["host"] = m.group(1)
                result["port"] = int(m.group(2) or 3306)
                result["database"] = m.group(3)
        if user:
            result["user"] = user
        if pwd:
            result["password"] = pwd
        if result:
            break
    return result


def get_db_config():
    """DB 연결정보를 .env/환경변수 우선, Spring 설정 보조 순서로 읽는다."""
    _load_dotenv_candidates()
    spring = _read_spring_datasource_fallback()
    enabled = os.getenv("ZIPAI_DB_ENABLED", "true").strip().lower() not in {"0", "false", "no", "off"}
    return {
        "enabled": enabled,
        "host": os.getenv("ZIPAI_DB_HOST", spring.get("host", "localhost")),
        "port": int(os.getenv("ZIPAI_DB_PORT", spring.get("port", 3306))),
        "database": os.getenv("ZIPAI_DB_NAME", spring.get("database", "zipai")),
        "user": os.getenv("ZIPAI_DB_USER", spring.get("user", "zipai")),
        "password": os.getenv("ZIPAI_DB_PASSWORD", spring.get("password", "")),
    }


def ensure_crawler_tables(cursor):
    """Crawler 전용 3개 테이블을 없으면 자동 생성한다."""
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS housing_notice (
            notice_id BIGINT NOT NULL AUTO_INCREMENT,
            pan_id VARCHAR(40) NOT NULL,
            source VARCHAR(100) NULL,
            title VARCHAR(500) NOT NULL,
            region VARCHAR(100) NULL,
            notice_date DATE NULL,
            posting_date DATE NULL,
            closing_date DATE NULL,
            status VARCHAR(100) NULL,
            housing_type VARCHAR(100) NULL,
            ccr_cnnt_sys_ds_cd VARCHAR(30) NULL,
            upp_ais_tp_cd VARCHAR(30) NULL,
            ais_tp_cd VARCHAR(30) NULL,
            pdf_file_id VARCHAR(80) NULL,
            pdf_file_name VARCHAR(700) NULL,
            hwpx_file_id VARCHAR(80) NULL,
            hwpx_file_name VARCHAR(700) NULL,
            pdf_text_path VARCHAR(1200) NULL,
            detail_endpoint VARCHAR(1200) NULL,
            crawled_at DATETIME NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (notice_id),
            UNIQUE KEY uk_housing_notice_pan_id (pan_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS housing_notice_rule (
            rule_id BIGINT NOT NULL AUTO_INCREMENT,
            notice_id BIGINT NOT NULL,
            pan_id VARCHAR(40) NOT NULL,
            applicant_type VARCHAR(60) NOT NULL,
            source_pdf VARCHAR(700) NULL,
            source_pdf_hash CHAR(64) NOT NULL,
            validation_status VARCHAR(40) NULL,
            rule_json JSON NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (rule_id),
            UNIQUE KEY uk_notice_rule (notice_id, applicant_type, source_pdf_hash),
            KEY idx_notice_rule_pan_type (pan_id, applicant_type),
            CONSTRAINT fk_housing_notice_rule_notice
                FOREIGN KEY (notice_id) REFERENCES housing_notice(notice_id)
                ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS crawl_history (
            crawl_id BIGINT NOT NULL AUTO_INCREMENT,
            crawler_name VARCHAR(100) NOT NULL DEFAULT 'happy_housing_crawler',
            started_at DATETIME NOT NULL,
            finished_at DATETIME NULL,
            status VARCHAR(30) NOT NULL,
            notice_count INT NOT NULL DEFAULT 0,
            rule_count INT NOT NULL DEFAULT 0,
            parsed_rule_count INT NOT NULL DEFAULT 0,
            validation_rule_count INT NOT NULL DEFAULT 0,
            error_message TEXT NULL,
            raw_json_path VARCHAR(1200) NULL,
            processed_json_path VARCHAR(1200) NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (crawl_id),
            KEY idx_crawl_history_started_at (started_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """)


def _to_mysql_datetime(value):
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00")).replace(tzinfo=None)
    except Exception:
        return None


def _reset_current_rule_value_folders(raw_results):
    """현재 수집된 공고의 rule_values 폴더만 비워 이전 버전 JSON 중복을 제거한다."""
    for item in raw_results or []:
        pan_id = item.get("pan_id")
        if not pan_id:
            continue
        target = RULE_VALUE_DIR / str(pan_id)
        if target.exists():
            shutil.rmtree(target)


def _iter_rule_json_records(pan_id):
    """해당 pan_id 폴더 아래 모든 계층 JSON을 DB 저장용으로 순회한다."""
    root = RULE_VALUE_DIR / str(pan_id)
    if not root.exists():
        return
    for path in sorted(root.rglob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        applicant_type = data.get("applicant_type") or path.stem
        validation_status = data.get("validation_status")
        source_pdf = path.parent.name
        source_pdf_hash = hashlib.sha256(source_pdf.encode("utf-8")).hexdigest()
        yield {
            "applicant_type": applicant_type,
            "validation_status": validation_status,
            "source_pdf": source_pdf,
            "source_pdf_hash": source_pdf_hash,
            "rule_json": json.dumps(data, ensure_ascii=False),
        }


def save_crawl_to_mysql(processed_results, started_at):
    """공고와 구조화 규칙을 MySQL에 UPSERT하고 crawl_history를 기록한다."""
    config = get_db_config()
    if not config["enabled"]:
        print("MySQL 저장: ZIPAI_DB_ENABLED=false 이므로 건너뜀")
        return {"saved": False, "reason": "disabled"}

    if mysql is None:
        print("MySQL 저장 건너뜀: mysql-connector-python이 설치되어 있지 않습니다.")
        print("설치 명령: pip install mysql-connector-python")
        return {"saved": False, "reason": "driver_missing"}

    if not config["password"]:
        print("MySQL 저장 건너뜀: DB 비밀번호를 찾지 못했습니다.")
        print("rpa/.env에 ZIPAI_DB_PASSWORD=비밀번호 를 설정하거나 Spring datasource password를 확인하세요.")
        return {"saved": False, "reason": "password_missing"}

    conn = None
    cursor = None
    try:
        conn = mysql.connect(
            host=config["host"],
            port=config["port"],
            database=config["database"],
            user=config["user"],
            password=config["password"],
            charset="utf8mb4",
            autocommit=False,
        )
        cursor = conn.cursor()
        ensure_crawler_tables(cursor)

        notice_id_by_pan = {}
        for item in processed_results:
            cursor.execute("""
                INSERT INTO housing_notice (
                    pan_id, source, title, region, notice_date, posting_date, closing_date,
                    status, housing_type, ccr_cnnt_sys_ds_cd, upp_ais_tp_cd, ais_tp_cd,
                    pdf_file_id, pdf_file_name, hwpx_file_id, hwpx_file_name,
                    pdf_text_path, detail_endpoint, crawled_at
                ) VALUES (
                    %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s
                )
                ON DUPLICATE KEY UPDATE
                    source=VALUES(source), title=VALUES(title), region=VALUES(region),
                    notice_date=VALUES(notice_date), posting_date=VALUES(posting_date),
                    closing_date=VALUES(closing_date), status=VALUES(status),
                    housing_type=VALUES(housing_type), ccr_cnnt_sys_ds_cd=VALUES(ccr_cnnt_sys_ds_cd),
                    upp_ais_tp_cd=VALUES(upp_ais_tp_cd), ais_tp_cd=VALUES(ais_tp_cd),
                    pdf_file_id=VALUES(pdf_file_id), pdf_file_name=VALUES(pdf_file_name),
                    hwpx_file_id=VALUES(hwpx_file_id), hwpx_file_name=VALUES(hwpx_file_name),
                    pdf_text_path=VALUES(pdf_text_path), detail_endpoint=VALUES(detail_endpoint),
                    crawled_at=VALUES(crawled_at)
            """, (
                item.get("pan_id"), item.get("source"), item.get("title"), item.get("region"),
                item.get("notice_date"), item.get("posting_date"), item.get("closing_date"),
                item.get("status"), item.get("housing_type"), item.get("ccr_cnnt_sys_ds_cd"),
                item.get("upp_ais_tp_cd"), item.get("ais_tp_cd"), item.get("pdf_file_id"),
                item.get("pdf_file_name"), item.get("hwpx_file_id"), item.get("hwpx_file_name"),
                item.get("pdf_text_path"), item.get("detail_endpoint"), _to_mysql_datetime(item.get("crawled_at")),
            ))
            cursor.execute("SELECT notice_id FROM housing_notice WHERE pan_id=%s", (item.get("pan_id"),))
            notice_id_by_pan[item.get("pan_id")] = cursor.fetchone()[0]

        # 현재 수집 공고의 기존 규칙을 먼저 지워, 이전 버전/경로의 중복 규칙이 남지 않게 한다.
        for notice_id in notice_id_by_pan.values():
            cursor.execute("DELETE FROM housing_notice_rule WHERE notice_id=%s", (notice_id,))

        rule_count = parsed_count = validation_count = 0
        for item in processed_results:
            pan_id = item.get("pan_id")
            notice_id = notice_id_by_pan.get(pan_id)
            if not notice_id:
                continue
            for rule in _iter_rule_json_records(pan_id) or []:
                cursor.execute("""
                    INSERT INTO housing_notice_rule (
                        notice_id, pan_id, applicant_type, source_pdf, source_pdf_hash,
                        validation_status, rule_json
                    ) VALUES (%s,%s,%s,%s,%s,%s,%s)
                    ON DUPLICATE KEY UPDATE
                        pan_id=VALUES(pan_id), source_pdf=VALUES(source_pdf),
                        validation_status=VALUES(validation_status), rule_json=VALUES(rule_json)
                """, (
                    notice_id, pan_id, rule["applicant_type"], rule["source_pdf"],
                    rule["source_pdf_hash"], rule["validation_status"], rule["rule_json"],
                ))
                rule_count += 1
                if rule["validation_status"] == "parsed":
                    parsed_count += 1
                elif rule["validation_status"] == "needs_validation":
                    validation_count += 1

        cursor.execute("""
            INSERT INTO crawl_history (
                started_at, finished_at, status, notice_count, rule_count,
                parsed_rule_count, validation_rule_count, raw_json_path, processed_json_path
            ) VALUES (%s,%s,'SUCCESS',%s,%s,%s,%s,%s,%s)
        """, (
            started_at, datetime.now(), len(processed_results), rule_count,
            parsed_count, validation_count, str(RAW_JSON_PATH), str(PROCESSED_JSON_PATH),
        ))

        conn.commit()
        print()
        print("====================================")
        print("MySQL 저장 완료")
        print("====================================")
        print("DB:", f"{config['host']}:{config['port']}/{config['database']}")
        print("housing_notice:", len(processed_results), "건 UPSERT")
        print("housing_notice_rule:", rule_count, "건 UPSERT")
        print(" - parsed:", parsed_count)
        print(" - needs_validation:", validation_count)
        print("crawl_history: 1건 추가")
        return {
            "saved": True,
            "notice_count": len(processed_results),
            "rule_count": rule_count,
            "parsed_count": parsed_count,
            "validation_count": validation_count,
        }

    except Exception as error:
        if conn:
            conn.rollback()
        print()
        print("[MySQL 저장 실패]")
        print(safe_console_text(error))
        return {"saved": False, "reason": str(error)}
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()



def record_crawl_failure_to_mysql(started_at, error_message, notice_count=0):
    """전체 실행 실패를 crawl_history에 남긴다. DB 자체가 불가하면 로그만 남기고 종료한다."""
    config = get_db_config()
    if not config.get("enabled") or mysql is None or not config.get("password"):
        return False

    conn = None
    cursor = None
    try:
        conn = mysql.connect(
            host=config["host"],
            port=config["port"],
            database=config["database"],
            user=config["user"],
            password=config["password"],
            charset="utf8mb4",
            autocommit=False,
        )
        cursor = conn.cursor()
        ensure_crawler_tables(cursor)
        cursor.execute("""
            INSERT INTO crawl_history (
                started_at, finished_at, status, notice_count, rule_count,
                parsed_rule_count, validation_rule_count, error_message,
                raw_json_path, processed_json_path
            ) VALUES (%s,%s,'FAILED',%s,0,0,0,%s,%s,%s)
        """, (
            started_at,
            datetime.now(),
            notice_count,
            str(error_message)[:65000],
            str(RAW_JSON_PATH),
            str(PROCESSED_JSON_PATH),
        ))
        conn.commit()
        print("crawl_history: FAILED 1건 기록")
        return True
    except Exception as db_error:
        if conn:
            conn.rollback()
        print("[실패 이력 DB 기록 실패]", safe_console_text(db_error))
        return False
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()


def main():
    crawl_started_at = datetime.now()
    print()
    print("====================================")
    print("ZipAI 행복주택 크롤링 시작")
    print("====================================")

    session = create_session()
    raw_results = []

    try:
        list_notices = get_happy_housing_list(
            session
        )

        if not list_notices:
            raise RuntimeError("행복주택 공고를 찾지 못했습니다. LH 응답 또는 HTML 구조를 확인하세요.")

        total = len(list_notices)

        for index, notice in enumerate(
            list_notices,
            start=1
        ):
            try:
                detail_notice = get_detail(
                    session,
                    notice
                )

                raw_results.append(
                    detail_notice
                )

                print_notice_summary(
                    detail_notice,
                    index,
                    total
                )

            except requests.RequestException as error:
                print()
                print(
                    f"[상세 접속 오류] {notice['title']}"
                )
                print(error)

                failed_notice = dict(notice)

                failed_notice.update({
                    "status": None,
                    "housing_type": None,
                    "notice_date": None,
                    "attachments": [],
                    "detail_response_length": None,
                    "crawled_at": datetime.now().isoformat(
                        timespec="seconds"
                    ),
                    "crawl_error": str(error)
                })

                raw_results.append(
                    failed_notice
                )

            except Exception as error:
                print()
                print(
                    f"[상세 처리 오류] {notice['title']}"
                )
                print(error)

            time.sleep(
                REQUEST_DELAY
            )

        # 상세페이지 JavaScript에서 확인한 실제 다운로드 방식:
        # GET /lhapply/lhFile.do?fileid=...
        download_pdf_files(
            session,
            raw_results
        )

        extract_downloaded_pdf_texts(
            raw_results
        )

        extract_rule_sections_from_downloaded_pdfs(
            raw_results
        )

        # 현재 공고의 이전 rule_values JSON을 제거한 뒤 이번 실행 결과만 새로 생성한다.
        _reset_current_rule_value_folders(raw_results)

        extract_all_youth_rule_values(
            raw_results
        )

        extract_remaining_rule_values(
            raw_results
        )

        processed_results = [
            build_processed_notice(notice)
            for notice in raw_results
        ]

        save_json(
            RAW_JSON_PATH,
            raw_results
        )

        save_json(
            PROCESSED_JSON_PATH,
            processed_results
        )

        save_csv(
            PROCESSED_CSV_PATH,
            processed_results
        )

        db_result = save_crawl_to_mysql(
            processed_results,
            crawl_started_at
        )
        if not db_result.get("saved") and db_result.get("reason") != "disabled":
            raise RuntimeError("MySQL 저장 실패: " + str(db_result.get("reason")))

        print()
        print("====================================")
        print("크롤링 완료")
        print("====================================")
        print(
            "수집 공고:",
            len(raw_results)
        )
        print(
            "RAW JSON:",
            RAW_JSON_PATH
        )
        print(
            "정제 JSON:",
            PROCESSED_JSON_PATH
        )
        print(
            "정제 CSV:",
            PROCESSED_CSV_PATH
        )
        print(
            "PDF 폴더:",
            FILES_DIR
        )
        print(
            "PDF 텍스트 폴더:",
            TEXT_DIR
        )
        print(
            "자격조건 구간 폴더:",
            RULE_SECTION_DIR
        )
        print(
            "조건값 JSON 폴더:",
            RULE_VALUE_DIR
        )
        print(
            "구간 진단 폴더:",
            DEBUG_DIR / "rule_heading_candidates"
        )
        return 0

    except requests.RequestException as error:
        print()
        print("목록 접속 오류:", error)
        record_crawl_failure_to_mysql(crawl_started_at, error, len(raw_results))
        return 1

    except Exception as error:
        print()
        print("전체 처리 오류:", error)
        record_crawl_failure_to_mysql(crawl_started_at, error, len(raw_results))
        return 1

    finally:
        session.close()


if __name__ == "__main__":
    sys.exit(main())
