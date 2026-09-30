(function () {
  const form = document.querySelector('.safety-search-form');
  if (!form) return;

  const keyword = document.getElementById('safety-keyword');
  const radius = document.getElementById('safety-radius');
  const result = document.querySelector('.safety-result');
  const regionalCheckbox = form.querySelector('input[name="regional-safety"]');
  const regionalResult = document.querySelector('.regional-safety-result');
  const crimeCheckbox = form.querySelector('input[name="crime-statistics"]');
  const crimeResult = document.querySelector('.crime-statistics-result');
  const womenCheckbox = form.querySelector('input[name="women-safe-house"]');
  const womenResult = document.querySelector('.women-safe-house-result');
  const mapTitle = document.querySelector('[data-map-title-text]') || document.querySelector('.safety-map-title h2');
  const sectionHeading = document.querySelector('.safe-section-heading h2');
  const mapBottom = document.querySelector('.map-bottom-bar');
  const previewName = document.querySelector('.safety-preview-panel strong');
  const previewText = document.querySelector('.safety-preview-panel span');
  const previewScore = document.querySelector('.safety-preview-score');
  const miniResult = document.querySelector('.safe-mini-result');
  const summaryNumbers = document.querySelectorAll('.safety-summary-item strong');
  const safetyMap = document.querySelector('.safety-map');
  const mapSection = document.getElementById('safety-map-section');
  const mapToggle = document.querySelector('[data-map-toggle]');
  const submitButton = form.querySelector('button[type="submit"]');
  const submitButtonIdleHtml = submitButton ? submitButton.innerHTML : '';
  const detailMapLinks = document.querySelectorAll('a[href^="/safe/safety-map"]');
  const sidoSelect = document.getElementById('safety-sido');
  const regionSelect = document.getElementById('safety-region');
  const safetyRegions = {
    '서울특별시': ['종로구','중구','용산구','성동구','광진구','동대문구','중랑구','성북구','강북구','도봉구','노원구','은평구','서대문구','마포구','양천구','강서구','구로구','금천구','영등포구','동작구','관악구','서초구','강남구','송파구','강동구'],
    '경기도': ['수원시 장안구','수원시 권선구','수원시 팔달구','수원시 영통구','성남시 수정구','성남시 중원구','성남시 분당구','의정부시','안양시 만안구','안양시 동안구','부천시 원미구','부천시 소사구','부천시 오정구','광명시','평택시','동두천시','안산시 상록구','안산시 단원구','고양시 덕양구','고양시 일산동구','고양시 일산서구','과천시','구리시','남양주시','오산시','시흥시','군포시','의왕시','하남시','용인시 처인구','용인시 기흥구','용인시 수지구','파주시','이천시','안성시','김포시','화성시 만세구','화성시 효행구','화성시 병점구','화성시 동탄구','광주시','양주시','포천시','여주시','연천군','가평군','양평군']
  };
  let analysisRequestId = 0;
  let isLoading = false;

  function radiusMeters() {
    const value = radius ? radius.value : '500m';
    if (value.includes('2')) return 2000;
    if (value.includes('1')) return 1000;
    return 500;
  }

  function selectedFeatures() {
    return Array.from(form.querySelectorAll('input[name="safety-feature"]:checked'))
      .map(function (input) { return input.value; })
      .filter(Boolean);
  }

  async function loadSafety(event) {
    if (event) event.preventDefault();
    if (isLoading) return;
    const query = keyword ? keyword.value.trim() : '';
    const meters = radiusMeters();
    const features = selectedFeatures();
    const regionalSelected = Boolean(regionalCheckbox && regionalCheckbox.checked);
    const crimeSelected = Boolean(crimeCheckbox && crimeCheckbox.checked);
    const womenSelected = Boolean(womenCheckbox && !womenCheckbox.disabled && womenCheckbox.checked);
    if (!query) {
      clearSafetyState('검색어를 입력해 주세요.');
      renderError('검색어를 입력해 주세요.');
      return;
    }
    if (!features.length && !regionalSelected && !crimeSelected && !womenSelected) {
      clearSafetyState('분석 항목을 하나 이상 선택해 주세요.');
      renderError('분석 항목을 하나 이상 선택해 주세요.');
      return;
    }
    const requestId = ++analysisRequestId;
    setBusy(true);
    clearSafetyState('분석 중입니다.');
    renderIdle('안전도 분석 결과를 불러오는 중입니다.');
    renderRegionalIdle(regionalSelected ? '지역안전지수를 불러오는 중입니다.' : '지역안전 항목을 선택하면 등급을 확인할 수 있습니다.');
    renderCrimeIdle(crimeSelected ? '경찰 관할 통계를 불러오는 중입니다.' : '5대범죄 현황 항목을 선택하면 통계를 확인할 수 있습니다.');
    renderWomenIdle(womenSelected ? '주변 여성안전시설을 불러오는 중입니다.' : '여성안전시설 항목을 선택하면 주변 시설을 확인할 수 있습니다.');
    try {
      const location = await resolveLocation(query);
      if (requestId !== analysisRequestId) return;
      updateDetailMapLinks(query, meters, features, regionalSelected, crimeSelected, womenSelected);
      const primaryRequest = features.length
        ? loadPrimarySafety(location, meters, features).then(function (data) { return { data: data }; })
            .catch(function (error) { return { error: error }; })
        : Promise.resolve({ data: null });
      const regionalRequest = regionalSelected
        ? loadRegionalSafety(location).then(function (data) { return { data: data }; })
            .catch(function () { return { data: null }; })
        : Promise.resolve({ data: null });
      const crimeRequest = crimeSelected
        ? loadCrimeStatistics(location).then(function (data) { return { data: data }; })
            .catch(function () { return { data: null }; })
        : Promise.resolve({ data: null });
      const womenRequest = womenSelected
        ? loadWomenSafeHouses(location, meters).then(function (data) { return { data: data }; })
            .catch(function () { return { data: null }; })
        : Promise.resolve({ data: null });
      const responses = await Promise.all([primaryRequest, regionalRequest, crimeRequest, womenRequest]);
      if (requestId !== analysisRequestId) return;
      if (responses[0].error) {
        clearSafetyState('안전도 분석 결과를 불러오지 못했습니다.');
        renderError(responses[0].error.message || '안전도 분석 결과를 불러오지 못했습니다.');
      } else if (responses[0].data) {
        renderScore(responses[0].data);
      } else {
        renderIdle('시설 기반 안전점수 항목이 선택되지 않았습니다.');
      }
      if (regionalSelected) renderRegionalSafety(responses[1].data);
      if (crimeSelected) renderCrimeStatistics(responses[2].data);
      if (womenSelected) renderWomenSafeHouses(responses[3].data, location, meters);
    } catch (error) {
      if (requestId !== analysisRequestId) return;
      clearSafetyState('안전도 분석 결과를 불러오지 못했습니다.');
      renderError(error.message || '안전도 분석 결과를 불러오지 못했습니다.');
      if (regionalSelected) renderRegionalIdle('지역안전지수 데이터 없음');
      if (crimeSelected) renderCrimeIdle('5대범죄 통계 데이터 없음');
      if (womenSelected) renderWomenIdle('여성안전시설 데이터 없음');
    } finally {
      if (requestId === analysisRequestId) setBusy(false);
    }
  }

  async function loadPrimarySafety(location, meters, features) {
    const params = new URLSearchParams({
      lat: String(location.latitude),
      lng: String(location.longitude),
      radius: String(meters),
      features: features.join(',')
    });
    const response = await fetch('/api/safety/score?' + params.toString());
    if (!response.ok) throw new Error(await message(response));
    const data = await response.json();
    data.location = location;
    data.infrastructureCount = Array.isArray(data.facilities) ? data.facilities.length : 0;
    return data;
  }

  async function loadRegionalSafety(location) {
    if (!location.sidoName) return null;
    const params = new URLSearchParams({ sido: location.sidoName });
    if (location.sigunguName) params.set('sigungu', location.sigunguName);
    const response = await fetch('/api/safety/regional?' + params.toString());
    if (!response.ok) return null;
    return response.json();
  }

  async function loadCrimeStatistics(location) {
    if (!location.sidoName) return null;
    const params = new URLSearchParams({ sido: location.sidoName });
    if (location.sigunguName) params.set('sigungu', location.sigunguName);
    const response = await fetch('/api/safety/crime-statistics?' + params.toString());
    if (!response.ok) throw new Error(await message(response));
    return response.json();
  }

  async function loadWomenSafeHouses(location, meters) {
    const params = new URLSearchParams({
      lat: String(location.latitude), lng: String(location.longitude), radius: String(meters)
    });
    if (location.sidoName) params.set('sido', location.sidoName);
    if (location.sigunguName) params.set('sigungu', location.sigunguName);
    const response = await fetch('/api/safety/women-safety-facilities?' + params.toString());
    if (!response.ok) throw new Error(await message(response));
    return response.json();
  }

  async function enableWomenFeatureIfVerified() {
    if (!womenCheckbox) return;
    try {
      const data = await loadWomenSafeHouses({ latitude: 37.5665, longitude: 126.9780, sidoName: '서울특별시', sigunguName: '중구' }, 100);
      if (!data.available || data.coordinateValidation !== 'VERIFIED_ONLY') return;
      womenCheckbox.disabled = false;
      const label = womenCheckbox.closest('label');
      if (label) {
        label.classList.remove('is-disabled');
        const note = label.querySelector('.safety-check-note');
        if (note) note.remove();
        label.removeAttribute('title');
      }
    } catch (error) {
      // 검증 DB가 아직 배포되지 않은 서버에서는 준비중 상태를 유지한다.
    }
  }

  async function resolveLocation(query) {
    const searchResponse = await fetch('/api/safety/search?q=' + encodeURIComponent(query));
    if (!searchResponse.ok) throw new Error(await message(searchResponse));
    const data = await searchResponse.json();
    if (!data.location) throw new Error('검색 결과가 없습니다.');
    return data.location;
  }

  function updateDetailMapLinks(query, meters, features, regionalSelected, crimeSelected, womenSelected) {
    const params = new URLSearchParams({
      q: query,
      radius: String(meters),
      features: features.join(','),
      regional: String(regionalSelected),
      crime: String(crimeSelected),
      women: String(womenSelected)
    });
    detailMapLinks.forEach(function (link) {
      link.href = '/safe/safety-map?' + params.toString();
    });
  }

  function restoreQueryState() {
    const params = new URLSearchParams(window.location.search);
    const query = params.get('q');
    if (!query) return false;
    if (keyword) keyword.value = query;
    const meters = params.get('radius');
    if (radius && ['500', '1000', '2000'].includes(meters)) {
      radius.value = meters === '500' ? '500m' : (meters === '1000' ? '1km' : '2km');
    }
    if (params.has('features')) {
      const selected = params.get('features').split(',');
      form.querySelectorAll('input[name="safety-feature"]').forEach(function (input) {
        input.checked = selected.includes(input.value);
      });
    }
    if (regionalCheckbox && params.has('regional')) {
      regionalCheckbox.checked = params.get('regional') === 'true';
    }
    if (crimeCheckbox && params.has('crime')) {
      crimeCheckbox.checked = params.get('crime') === 'true';
    }
    if (womenCheckbox && params.has('women')) {
      womenCheckbox.checked = params.get('women') === 'true';
    }
    return true;
  }

  function clearSafetyState(message) {
    if (window.ZipaiSafetyMap) window.ZipaiSafetyMap.reset();
    if (mapTitle) mapTitle.textContent = '검색 지역 안전시설 분포';
    if (sectionHeading) sectionHeading.textContent = '검색 지역 안전도 분석';
    if (mapBottom) {
      mapBottom.innerHTML = '<div><strong>분석 대기</strong><span>' + escapeHtml(message) + '</span></div><span>데이터 기준 -</span>';
    }
    if (previewName) previewName.textContent = '검색 지역';
    if (previewText) previewText.textContent = message;
    if (previewScore) previewScore.textContent = '-';
    if (miniResult) {
      miniResult.innerHTML = '<div><h3>분석 대기</h3><p>' + escapeHtml(message) + '</p></div>';
    }
    summaryNumbers.forEach(function (item) { item.textContent = '-'; });
    document.querySelectorAll('[data-safety-summary="women"]').forEach(function (item) { item.textContent = '-'; });
    renderRegionalIdle('지역을 검색하면 안전정보가 표시됩니다.');
    renderCrimeIdle('지역을 검색하면 안전정보가 표시됩니다.');
    renderWomenIdle('지역을 검색하면 안전정보가 표시됩니다.');
  }

  function renderRegionalSafety(data) {
    if (!regionalResult) return;
    const index = data && data.available ? data.regionalSafety : null;
    if (!index) {
      renderRegionalIdle((data && data.message) || '지역안전지수 데이터 없음');
      return;
    }
    const area = [index.sidoName, index.sigunguName].filter(Boolean).join(' ');
    const grades = [
      ['교통사고', index.trafficGrade, 'fa-solid fa-car-burst'],
      ['화재', index.fireGrade, 'fa-solid fa-fire-flame-curved'],
      ['범죄', index.crimeGrade, 'fa-solid fa-shield-halved'],
      ['생활안전', index.lifeSafetyGrade, 'fa-solid fa-house-medical'],
      ['자살', index.suicideGrade, 'fa-solid fa-heart-pulse'],
      ['감염병', index.infectiousDiseaseGrade, 'fa-solid fa-virus-covid']
    ];
    regionalResult.innerHTML = renderTitle('regional', '지역안전지수') + '<p class="safety-card-subtitle">행정안전부 '
      + escapeHtml(String(index.year)) + '년 공식 지표</p><p class="regional-safety-area">'
      + escapeHtml(area) + '</p><div class="regional-safety-grades">'
      + grades.map(function (grade) {
        return '<div class="regional-safety-grade"><span class="regional-grade-label"><i class="' + grade[2]
          + '" aria-hidden="true"></i>' + escapeHtml(grade[0])
          + '</span><strong>' + escapeHtml(String(grade[1])) + '등급</strong></div>';
      }).join('')
      + '</div><div class="public-card-guidance"><strong>안내 기준</strong><p>행정안전부 공식 지역안전지수 등급입니다.<br>1등급에 가까울수록 상대적으로 안전합니다.</p></div>';
  }

  function renderRegionalIdle(text) {
    if (!regionalResult) return;
    regionalResult.innerHTML = renderTitle('regional', '지역안전지수') + '<p class="safe-api-message">' + escapeHtml(text) + '</p>';
  }

  function renderCrimeStatistics(data) {
    if (!crimeResult) return;
    if (!data || !data.available) {
      renderCrimeIdle((data && data.message) || '5대범죄 통계 데이터 없음');
      return;
    }
    const area = [data.sidoName, data.sigunguName].filter(Boolean).join(' ');
    const totals = Array.isArray(data.totals) ? data.totals : [];
    const stationList = (data.stations || []).map(function (station) { return station.policeStationName; });
    const stationNames = stationList.join(', ');
    const stationSummary = stationList.length > 1 ? stationList[0] + ' 외 ' + (stationList.length - 1) + '곳' : stationNames;
    const agencyCoverage = data.coverageType === 'POLICE_AGENCY_STATISTICS';
    const statusLabel = data.provisional ? '잠정' : '확정';
    const coverageLabel = agencyCoverage ? '경찰청 관내 전체' : '경찰서 관할';
    crimeResult.innerHTML = renderTitle('crime', '5대범죄 현황') + '<p class="safety-card-subtitle">'
      + escapeHtml(String(data.year)) + '년 ' + statusLabel + ' · ' + coverageLabel + ' 기준</p><p class="regional-safety-area">'
      + escapeHtml(area) + (stationSummary ? ' <span class="safety-station-badge" title="' + escapeHtml(stationNames) + '">' + escapeHtml(stationSummary) + '</span>' : '')
      + '</p><div class="regional-safety-grades crime-statistics-grid">'
      + totals.map(function (item) {
        return '<div class="regional-safety-grade"><span>' + escapeHtml(item.crimeType)
          + '</span><strong>발생 ' + escapeHtml(String(item.occurrenceCount)) + '건</strong>'
          + '<small>검거 ' + escapeHtml(String(item.arrestCount)) + '건</small></div>';
      }).join('')
      + '<div class="public-card-guidance"><strong>안내 기준</strong><p>경찰서 관할 기준 통계이며, 검색 지점 주변의 실제 범죄 건수가 아닙니다.</p></div>';
  }

  function renderCrimeIdle(text) {
    if (!crimeResult) return;
    crimeResult.innerHTML = renderTitle('crime', '5대범죄 현황') + '<p class="safe-api-message">' + escapeHtml(text) + '</p>';
  }

  function renderWomenSafeHouses(data, center, radiusMeters) {
    if (!data || !data.available) {
      if (womenResult) renderWomenIdle((data && data.message) || '여성안전시설 데이터 없음');
      document.querySelectorAll('[data-safety-summary="women"]').forEach(function (item) { item.textContent = '0'; });
      return;
    }
    const houses = Array.isArray(data.data) ? data.data : [];
    document.querySelectorAll('[data-safety-summary="women"]').forEach(function (item) {
      item.textContent = String(data.count);
    });
    appendWomenMarkers(houses, center, radiusMeters);
    if (!womenResult) return;
    const closest = houses.slice(0, 1);
    const remaining = houses.slice(1);
    const sourceDates = Array.from(new Set(houses.map(function (house) {
      return [house.sourceName, house.sourceUpdatedAt].filter(Boolean).join(' · ');
    }).filter(Boolean)));
    const types = Array.from(new Set(houses.map(function (house) { return facilityTypeLabel(house.facilityType); })));
    const hasSafeHouse = houses.some(function (house) {
  return house.facilityType === 'SAFE_HOUSE';
});

const hasParcelLocker = houses.some(function (house) {
  return house.facilityType === 'SAFE_PARCEL_LOCKER';
});

let facilityIntro = '공식 여성안전시설 정보를 제공합니다.';

if (hasParcelLocker && !hasSafeHouse) {
  facilityIntro = '택배 기사를 직접 만나지 않고 무인 보관함을 통해 안전하게 택배를 수령할 수 있는 시설입니다.';
} else if (hasSafeHouse && !hasParcelLocker) {
  facilityIntro = '위급 상황 시 대피·도움 요청 및 경찰 신고 지원이 가능한 지정 편의점입니다.';
} else if (hasSafeHouse && hasParcelLocker) {
  facilityIntro = '위급 상황 시 도움을 요청할 수 있는 안심지킴이집과 비대면으로 택배를 수령할 수 있는 안심택배함 정보를 제공합니다.';
}
    womenResult.innerHTML = renderTitle('women', '여성안전시설') + '<p class="safety-card-subtitle">'
      + escapeHtml(types.join(' · ') || '공식 여성안전시설') + '</p>'
      + '<p class="women-safe-intro">' + escapeHtml(facilityIntro) + '</p>'
      + '<p class="regional-safety-area">반경 ' + escapeHtml(String(data.radiusMeters)) + 'm · 가까운 순 '
      + escapeHtml(String(data.count)) + '곳</p>'
      + (houses.length ? '<p class="women-safe-closest-label">가장 가까운 곳</p><div class="women-safe-house-list">' + closest.map(renderWomenHouse).join('') + '</div>'
        + (remaining.length ? '<details class="safety-more women-safe-more"><summary>' + escapeHtml(String(remaining.length))
          + '곳 더 보기</summary><div class="women-safe-house-list">' + remaining.map(renderWomenHouse).join('') + '</div></details>' : '')
        : '<p class="safe-api-message">' + escapeHtml(data.message || '검색 반경 내 데이터가 없습니다.') + '</p>')
      + '<p class="regional-safety-note regional-safety-note-primary">'
      + escapeHtml(sourceDates.length ? sourceDates.join(' / ') : (data.message || '공식 검증 좌표 기준')) + '</p>'
      + '<div class="public-card-guidance"><strong>안내 기준</strong><p>공식 데이터 중 위치가 확인되거나 검증된 좌표만 표시합니다.<br>실제 운영 현황과 다를 수 있습니다.</p></div>';
  }

  function renderWomenHouse(house) {
        const name = house.name || [house.brandName, house.storeName].filter(Boolean).join(' ');
        return '<article class="regional-safety-grade"><span><em class="women-facility-type">'
          + escapeHtml(facilityTypeLabel(house.facilityType)) + '</em>' + escapeHtml(name || facilityTypeLabel(house.facilityType))
          + '</span><strong>' + escapeHtml(String(house.distanceMeters)) + 'm</strong><small>'
          + escapeHtml(house.address || '') + '</small></article>';
  }

  function facilityTypeLabel(type) {
    if (type === 'SAFE_PARCEL_LOCKER') return '안심택배함';
    if (type === 'SAFE_STORE') return '여성안심점포';
    if (type === 'SAFE_ROUTE') return '여성안심길';
    if (type === 'SAFE_HOUSE') return '여성안심지킴이집';
    return '여성안전시설';
  }

  function renderWomenIdle(text) {
    if (!womenResult) return;
    womenResult.innerHTML = renderTitle('women', '여성안전시설') + '<p class="safe-api-message">' + escapeHtml(text) + '</p>';
  }

  function appendWomenMarkers(houses, center, radiusMeters) {
    if (!window.ZipaiSafetyMap) return;
    window.ZipaiSafetyMap.appendWomenFacilities({
      facilities: houses,
      center: center,
      radiusMeters: radiusMeters
    });
  }

  function renderScore(data) {
    const center = data.location || data.center || {};
    const metrics = Array.isArray(data.metrics) ? data.metrics : [];
    if (previewName) previewName.textContent = center.name || '검색 지역';
    if (previewText) previewText.textContent = data.description || data.message || '';
    if (previewScore) previewScore.textContent = data.score;
    if (mapTitle) mapTitle.textContent = (center.name || '검색 지역') + ' 안전시설 분포';
    if (sectionHeading) sectionHeading.textContent = (center.name || '검색 지역') + ' 안전도 분석';
    if (summaryNumbers[0]) summaryNumbers[0].textContent = data.score;
    if (summaryNumbers[1]) summaryNumbers[1].textContent = data.infrastructureCount;
    if (summaryNumbers[2]) {
      const counts = data.summary || {};
      summaryNumbers[2].textContent = (counts.CCTV || 0) + (counts.SAFETY_BELL || 0);
    }
    if (miniResult) {
      miniResult.innerHTML = '<span class="safe-mini-score">' + escapeHtml(String(data.score)) + '</span><div><h3>'
        + escapeHtml(center.name || '검색 지역') + ' ' + escapeHtml(data.grade || '분석 완료')
        + '</h3><p>' + escapeHtml(data.description || data.message || '') + '</p></div>';
    }
    if (mapBottom) {
      mapBottom.innerHTML = '<div><strong>안전 인프라 점수 ' + escapeHtml(String(data.score)) + '점</strong><span>'
        + escapeHtml(center.name || '검색 지역') + ' 반경 ' + escapeHtml(String(data.radiusMeters)) + 'm 분석</span></div><span>데이터 기준 '
        + escapeHtml(String(data.dataUpdatedAt || '선택 시설 데이터 없음')) + '</span>';
    }
    renderMarkers(data.facilities || [], center, data.radiusMeters);
    if (!result) return;
    const counts = data.summary || {};
    const policeCount = (counts.POLICE_STATION || 0) + (counts.POLICE_BOX || 0);
    const safetyCount = (counts.SAFETY_BELL || 0) + (counts.STREET_LIGHT || 0);
    result.innerHTML = renderTitle('score', '안전 인프라 점수', true) + '<div class="safety-score-content"><div class="safety-score-row"><div class="safety-score-ring" aria-label="안전점수 '
      + escapeHtml(String(data.score)) + '점" style="--score:' + Number(data.score || 0) + '%"><div><strong>'
      + escapeHtml(String(data.score)) + '</strong><span>점</span></div></div><div class="safety-score-copy"><strong>'
      + escapeHtml(data.grade || '분석 완료') + '</strong><span class="safety-score-location">'
      + escapeHtml(center.name || '검색 지역') + '</span><p>CCTV·경찰·치안시설·비상벨·보안등을 기준으로 계산한 주변 안전 인프라 점수입니다.</p></div></div><div class="safety-score-details"><div class="safety-metrics">'
      + metrics.map(renderMetric).join('') + '</div><div class="safety-result-summary" aria-label="반경 내 시설 개수">'
      + renderSummaryCount('CCTV', counts.CCTV || 0)
      + renderSummaryCount('치안시설', policeCount)
      + renderSummaryCount('안전시설', safetyCount)
  }

  function renderSummaryCount(label, count) {
    return '<div><span>' + escapeHtml(label) + '</span><strong>' + escapeHtml(String(count)) + '</strong><small>개</small></div>';
  }

  function renderMetric(metric) {
    const value = Number(metric.value || 0);
    const tone = value >= 85 ? 'var(--zipai-safe)' : value >= 70 ? 'var(--zipai-primary)' : 'var(--zipai-warning)';
    return '<div class="safety-meter"><span>' + escapeHtml(metric.name || '') + '</span><span class="safety-meter-bar"><span class="safety-meter-fill" style="--value: '
      + value + '%; --tone: ' + tone + ';"></span></span><strong>' + value + '</strong></div>';
  }

  function renderMarkers(facilities, center, radiusMeters) {
    if (!window.ZipaiSafetyMap) return;
    window.ZipaiSafetyMap.render({
      facilities: facilities,
      center: center,
      radiusMeters: radiusMeters
    });
  }

  function renderIdle(text) {
    if (!result) return;
    const title = result.dataset.idleTitle || '분석 결과';
    result.innerHTML = renderTitle('score', title, true) + '<p class="safe-api-message">' + escapeHtml(text) + '</p>';
  }

  function renderTitle(type, text, emphasized) {
    const icons = {
      score: 'fa-solid fa-shield-halved',
      regional: 'fa-solid fa-shield-halved',
      crime: 'fa-solid fa-chart-column',
      women: 'fa-solid fa-house-circle-check'
    };
    return '<h2 class="safety-title-with-icon' + (emphasized ? ' safety-title-primary' : '') + '"><span class="safety-title-icon"><i class="'
      + icons[type] + '" aria-hidden="true"></i></span>' + escapeHtml(text) + '</h2>';
  }

  function renderError(text) {
    renderIdle(text);
  }

  function setBusy(isBusy) {
    isLoading = isBusy;
    const button = submitButton;
    if (!button) return;
    button.disabled = isBusy;
    button.innerHTML = isBusy
      ? '<i class="fa-solid fa-spinner fa-spin" aria-hidden="true"></i> 분석 중'
      : submitButtonIdleHtml;
  }

  async function message(response) {
    try {
      const body = await response.json();
      return body.message || response.statusText;
    } catch (error) {
      return response.statusText;
    }
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (char) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char];
    });
  }

  function resetSafety(event) {
    if (event) event.preventDefault();
    analysisRequestId++;
    if (keyword) keyword.value = '';
    if (radius) radius.value = '500m';
    if (sidoSelect) sidoSelect.value = '';
    populateSafetyRegions();
    form.querySelectorAll('input[name="safety-feature"]').forEach(function (input) { input.checked = true; });
    if (regionalCheckbox) regionalCheckbox.checked = true;
    if (crimeCheckbox) crimeCheckbox.checked = true;
    if (womenCheckbox) womenCheckbox.checked = true;
    detailMapLinks.forEach(function (link) { link.href = '/safe/safety-map'; });
    setBusy(false);
    clearSafetyState('지역을 검색하고 분석하기를 눌러 주세요.');
    renderIdle('지역을 검색하고 분석하기를 눌러 주세요.');
  }

  function populateSafetyRegions() {
    if (!regionSelect) return;
    const selected = regionSelect.value;
    const regions = safetyRegions[sidoSelect ? sidoSelect.value : ''] || [];
    regionSelect.innerHTML = '<option value="">전체 시·군·구</option>'
      + regions.map(function (name) {
        return '<option value="' + escapeHtml(name) + '">' + escapeHtml(name) + '</option>';
      }).join('');
    if (regions.includes(selected)) regionSelect.value = selected;
  }

  function applySelectedRegion(shouldSearch) {
    if (!keyword) return;
    const sido = sidoSelect ? sidoSelect.value : '';
    const region = regionSelect ? regionSelect.value : '';
    const query = [sido, region].filter(Boolean).join(' ');
    if (query) keyword.value = query;
    if (shouldSearch && region && typeof form.requestSubmit === 'function') form.requestSubmit();
  }

  form.addEventListener('submit', loadSafety);
  form.addEventListener('reset', resetSafety);
  if (sidoSelect) {
    sidoSelect.addEventListener('change', function () {
      populateSafetyRegions();
      applySelectedRegion(false);
    });
  }
  if (regionSelect) {
    regionSelect.addEventListener('change', function () {
      applySelectedRegion(true);
    });
  }
  if (mapToggle && mapSection) {
    mapToggle.addEventListener('click', function () {
      const collapsed = mapSection.classList.toggle('is-collapsed');
      mapToggle.setAttribute('aria-expanded', String(!collapsed));
      mapToggle.textContent = collapsed ? '안전시설 지도 펼치기' : '안전시설 지도 접기';
      if (!collapsed) {
        window.requestAnimationFrame(function () {
          if (window.ZipaiSafetyMap) window.ZipaiSafetyMap.relayout();
        });
      }
    });
  }
  populateSafetyRegions();
  const restoredQuery = restoreQueryState();
  clearSafetyState('지역을 검색하고 분석하기를 눌러 주세요.');
  renderIdle('지역을 검색하고 분석하기를 눌러 주세요.');
  enableWomenFeatureIfVerified().then(function () {
    if (restoredQuery || (form.dataset.autoAnalyze === 'true' && keyword && keyword.value.trim())) loadSafety();
  });
})();
