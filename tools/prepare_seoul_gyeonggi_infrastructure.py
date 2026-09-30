import argparse
import csv
import hashlib
import io
import json
import math
import os
import re
import sys
import time
import zipfile
from pathlib import Path
from urllib.parse import unquote

import pandas as pd
import requests

RAW_DIR = Path("data/raw")
PROCESSED_DIR = Path("data/processed")
CCTV_RAW = RAW_DIR / "CCTV정보.csv"
BELL_RAW = RAW_DIR / "안전비상벨위치정보.csv"
LIGHT_RAW = RAW_DIR / "전국보안등정보표준데이터_api.csv"

CCTV_URL = "https://file.localdata.go.kr/file/cctv_info/info"
BELL_URL = "https://file.localdata.go.kr/file/emergency_call_box_info/info"
LIGHT_API = "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api"

SEOUL_GU = [
    "종로구","중구","용산구","성동구","광진구","동대문구","중랑구","성북구","강북구","도봉구",
    "노원구","은평구","서대문구","마포구","양천구","강서구","구로구","금천구","영등포구","동작구",
    "관악구","서초구","강남구","송파구","강동구",
]
GYEONGGI_SIGUN = [
    "수원시","용인시","고양시","화성시","성남시","부천시","남양주시","안산시","평택시","안양시",
    "시흥시","파주시","김포시","의정부시","광주시","하남시","광명시","군포시","양주시","오산시",
    "이천시","안성시","구리시","의왕시","포천시","양평군","여주시","동두천시","과천시","가평군","연천군",
]

TIMEOUT = 60
UA = "Mozilla/5.0 ZipAI-Safety-Data/1.0"


def clean(v):
    if pd.isna(v): return ""
    return str(v).strip().replace("\r"," ").replace("\n"," ").replace("\u3000"," ")


def stable(*parts):
    s = "|".join(clean(p) for p in parts)
    return hashlib.sha256(s.encode("utf-8")).hexdigest()[:32]


def korea_coord(lat, lon):
    return lat.between(33,39) & lon.between(124,132)


def download_localdata(url, out):
    print(f"Downloading {url}")
    r = requests.get(url, headers={"User-Agent":UA, "Referer":"https://www.data.go.kr/"}, timeout=TIMEOUT, allow_redirects=True)
    r.raise_for_status()
    content = r.content
    ctype = r.headers.get("content-type", "").lower()
    if content[:2] == b"PK" or "zip" in ctype:
        with zipfile.ZipFile(io.BytesIO(content)) as z:
            names = [n for n in z.namelist() if n.lower().endswith('.csv')]
            if not names:
                raise RuntimeError("ZIP 안에 CSV가 없습니다")
            data = z.read(names[0])
    else:
        data = content
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(data)
    print(f"Saved {out} ({out.stat().st_size:,} bytes)")


def download_streetlight(api_key, out):
    if not api_key:
        raise RuntimeError("보안등 다운로드에는 DATA_GO_KR_API_KEY가 필요합니다.")
    key = unquote(api_key)
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists(): out.unlink()
    page, rows_written, total = 1, 0, None
    while True:
        params={"serviceKey":key,"pageNo":page,"numOfRows":1000,"type":"json"}
        last=None
        for attempt in range(3):
            try:
                r=requests.get(LIGHT_API, params=params, timeout=30)
                r.raise_for_status(); payload=r.json(); break
            except Exception as e:
                last=e; time.sleep(2)
        else: raise last
        header=payload.get("header",{})
        if str(header.get("resultCode","")) != "00":
            raise RuntimeError(f"보안등 API 오류: {header}")
        body=payload.get("body",{})
        total=int(body.get("totalCount",0))
        items=body.get("items",[])
        if isinstance(items,dict): items=items.get("item",items)
        if isinstance(items,dict): items=[items]
        if not items: break
        df=pd.DataFrame(items)
        df.to_csv(out, mode="w" if page==1 else "a", header=page==1, index=False, encoding="utf-8-sig")
        rows_written += len(df)
        if page % 20 == 0: print(f"Street light: {rows_written:,}/{total:,}")
        if rows_written >= total: break
        page += 1; time.sleep(0.15)
    print(f"Saved {out} ({rows_written:,} rows)")


def read_cp949(path):
    for enc in ("cp949","euc-kr","utf-8-sig","utf-8"):
        try: return pd.read_csv(path, encoding=enc, dtype=str, low_memory=False)
        except UnicodeDecodeError: pass
    raise RuntimeError(f"인코딩을 판별하지 못했습니다: {path}")


def first_col(df, *names):
    for n in names:
        if n in df.columns: return df[n]
    return pd.Series([""]*len(df), index=df.index)


def normalize_cctv(path):
    f=read_cp949(path)
    addr=first_col(f,"소재지도로명주소").where(first_col(f,"소재지도로명주소").fillna("").str.strip()!="", first_col(f,"소재지지번주소"))
    lat=pd.to_numeric(first_col(f,"WGS84위도","위도"), errors="coerce")
    lon=pd.to_numeric(first_col(f,"WGS84경도","경도"), errors="coerce")
    mgmt=first_col(f,"관리기관명").map(clean); purpose=first_col(f,"설치목적구분","설치목적").map(clean)
    mid=first_col(f,"관리번호").map(clean)
    ids=["CCTV:"+stable(m if m else i, a, la, lo) for i,(m,a,la,lo) in enumerate(zip(mid,addr,lat,lon))]
    out=pd.DataFrame({
        "source_id":ids,"name":(mgmt+" "+purpose+" CCTV").str.strip(),"facility_type":"CCTV",
        "address":addr.map(clean),"latitude":lat,"longitude":lon,"source":"행정안전부 CCTV정보",
        "source_updated_at":first_col(f,"데이터기준일자").map(clean),"purpose":purpose,
        "camera_count":pd.to_numeric(first_col(f,"카메라대수"), errors="coerce").fillna(1).astype(int)
    })
    return out[korea_coord(out.latitude,out.longitude) & (out.address!="")].drop_duplicates("source_id")


def normalize_bell(path):
    f=read_cp949(path)
    addr=first_col(f,"소재지도로명주소").where(first_col(f,"소재지도로명주소").fillna("").str.strip()!="", first_col(f,"소재지지번주소"))
    lat=pd.to_numeric(first_col(f,"WGS84위도","위도"), errors="coerce")
    lon=pd.to_numeric(first_col(f,"WGS84경도","경도"), errors="coerce")
    no=first_col(f,"안전비상벨관리번호","관리번호").map(clean); loc=first_col(f,"설치위치").map(clean)
    purpose=(first_col(f,"설치목적").map(clean)+" / "+first_col(f,"설치장소유형").map(clean)).str.strip(" /")
    ids=["SAFETY_BELL:"+stable(n if n else i, a, la, lo) for i,(n,a,la,lo) in enumerate(zip(no,addr,lat,lon))]
    out=pd.DataFrame({
        "source_id":ids,"name":no.where(no!="",loc).replace("","안전비상벨"),"facility_type":"SAFETY_BELL",
        "address":addr.map(clean),"latitude":lat,"longitude":lon,"source":"행정안전부 안전비상벨위치정보",
        "source_updated_at":first_col(f,"데이터기준일자").map(clean),"purpose":purpose.str[:50],"camera_count":1
    })
    return out[korea_coord(out.latitude,out.longitude) & (out.address!="")].drop_duplicates("source_id")


def normalize_light(path):
    f=pd.read_csv(path, encoding="utf-8-sig", dtype=str, low_memory=False)
    addr=first_col(f,"rdnmadr").where(first_col(f,"rdnmadr").fillna("").str.strip()!="", first_col(f,"lnmadr"))
    lat=pd.to_numeric(first_col(f,"latitude"), errors="coerce"); lon=pd.to_numeric(first_col(f,"longitude"), errors="coerce")
    loc=first_col(f,"lmpLcNm").map(clean).replace("","보안등"); inst=first_col(f,"insttCode").map(clean)
    ids=["STREET_LIGHT:"+stable(i,n,a,la,lo) for i,n,a,la,lo in zip(inst,loc,addr,lat,lon)]
    out=pd.DataFrame({
        "source_id":ids,"name":loc,"facility_type":"STREET_LIGHT","address":addr.map(clean),"latitude":lat,"longitude":lon,
        "source":"공공데이터포털 전국보안등정보표준데이터","source_updated_at":first_col(f,"referenceDate").map(clean),
        "purpose":"야간 보행·방범 조명","camera_count":1
    })
    return out[korea_coord(out.latitude,out.longitude) & (out.address!="")].drop_duplicates("source_id")


def region_of(address):
    a=clean(address)
    if a.startswith("서울특별시") or a.startswith("서울 ") or a.startswith("서울시"):
        return "서울특별시"
    if a.startswith("경기도") or a.startswith("경기 "):
        return "경기도"
    return ""


def sigungu_of(address, region):
    a=clean(address)
    tokens=a.split()
    expected=SEOUL_GU if region=="서울특별시" else GYEONGGI_SIGUN
    for t in tokens[:4]:
        if t in expected: return t
    # 수원시 장안구처럼 시 + 구가 이어져도 시 단위로 커버리지 집계
    for s in expected:
        if s in a: return s
    return ""


def write_region_outputs(frames):
    allf=pd.concat(frames, ignore_index=True)
    allf["region"]=allf.address.map(region_of)
    target=allf[allf.region.isin(["서울특별시","경기도"])].copy()
    target["sigungu"]=target.apply(lambda r:sigungu_of(r.address,r.region), axis=1)
    target=target.drop_duplicates("source_id")
    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    data_cols=["source_id","name","facility_type","address","latitude","longitude","source","source_updated_at","purpose","camera_count"]
    for region, fn in [("서울특별시","safe_infrastructure_seoul.csv"),("경기도","safe_infrastructure_gyeonggi.csv")]:
        target[target.region==region][data_cols].to_csv(PROCESSED_DIR/fn,index=False,encoding="utf-8-sig")
    target[data_cols].to_csv(PROCESSED_DIR/"safe_infrastructure_all.csv",index=False,encoding="utf-8-sig")
    rows=[]
    for region, expected in [("서울특별시",SEOUL_GU),("경기도",GYEONGGI_SIGUN)]:
        rf=target[target.region==region]
        for s in expected:
            sf=rf[rf.sigungu==s]
            rows.append({"region":region,"sigungu":s,"total":len(sf),"CCTV":int((sf.facility_type=="CCTV").sum()),"SAFETY_BELL":int((sf.facility_type=="SAFETY_BELL").sum()),"STREET_LIGHT":int((sf.facility_type=="STREET_LIGHT").sum())})
    cov=pd.DataFrame(rows)
    cov.to_csv(PROCESSED_DIR/"coverage_report.csv",index=False,encoding="utf-8-sig")
    missing=[]
    for col in ["CCTV","SAFETY_BELL","STREET_LIGHT"]:
        for _,r in cov[cov[col]==0].iterrows(): missing.append({"facility_type":col,"region":r.region,"sigungu":r.sigungu})
    (PROCESSED_DIR/"coverage_missing.json").write_text(json.dumps(missing,ensure_ascii=False,indent=2),encoding="utf-8")
    print("\n=== 서울·경기 안전인프라 결과 ===")
    print(target.groupby(["region","facility_type"]).size().to_string())
    print("\nCoverage zero-count entries:",len(missing))
    print("Outputs:")
    for p in ["safe_infrastructure_seoul.csv","safe_infrastructure_gyeonggi.csv","safe_infrastructure_all.csv","coverage_report.csv","coverage_missing.json"]:
        print(" -",PROCESSED_DIR/p)


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--skip-download",action="store_true")
    ap.add_argument("--skip-street-light",action="store_true")
    args=ap.parse_args()
    RAW_DIR.mkdir(parents=True,exist_ok=True)
    if not args.skip_download:
        if not CCTV_RAW.exists(): download_localdata(CCTV_URL,CCTV_RAW)
        if not BELL_RAW.exists(): download_localdata(BELL_URL,BELL_RAW)
        if not args.skip_street_light and not LIGHT_RAW.exists(): download_streetlight(os.getenv("DATA_GO_KR_API_KEY"),LIGHT_RAW)
    missing=[p for p in [CCTV_RAW,BELL_RAW] if not p.exists()]
    if missing: raise FileNotFoundError("필수 raw 파일 없음: "+", ".join(map(str,missing)))
    frames=[normalize_cctv(CCTV_RAW),normalize_bell(BELL_RAW)]
    if LIGHT_RAW.exists(): frames.append(normalize_light(LIGHT_RAW))
    else: print("WARNING: 보안등 raw 파일이 없어 STREET_LIGHT는 제외됩니다.")
    write_region_outputs(frames)

if __name__=="__main__": main()
