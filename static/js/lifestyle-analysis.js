(async function () {
  'use strict';

  let rooms = [];
  let activeRoomRegion = '';
  let activeRoomSido = '';

  const visitKey = 'zipaiRoomVisits';
  const offerKey = 'zipaiRoomOffers';
  let visitCache = [];
  let offerCache = [];
  const roomList = document.getElementById('availableRooms');
  const visitRegionNotice = document.getElementById('visitRegionNotice');
  const visitForm = document.getElementById('visitForm');
  const visitDate = visitForm.elements.date;
  const visitTime = document.getElementById('visitTime');
  const offerForm = document.getElementById('offerForm');
  const offerImages = document.getElementById('offerImages');
  const offerPhotoPreview = document.getElementById('offerPhotoPreview');
  const offerPhotoCount = document.getElementById('offerPhotoCount');
  let offerPreviewUrls = [];
  const toast = document.getElementById('roomToast');
  const lifestyleForm = document.getElementById('lifestyleRecommendForm');
  const lifestyleSido = document.getElementById('lifestyleSido');
  const lifestyleSigungu = document.getElementById('lifestyleSigungu');
  const lifestyleResults = document.getElementById('lifestyleRecommendationResults');
  const lifestyleResultHeading = document.getElementById('lifestyleResultHeading');
  const lifestyleResultScope = document.getElementById('lifestyleResultScope');
  const lifestyleSelectedRegionSummary = document.getElementById('lifestyleSelectedRegionSummary');
  const lifestyleDataNotice = document.getElementById('lifestyleDataNotice');
  let lifestyleAreas = [];
  const lifestyleScoreLabels = {
    transport: '대중교통',
    convenience: '생활편의',
    medical: '의료',
    education: '교육',
    park: '공원·녹지',
    safety: '안전',
    commercial: '상권',
    quiet: '조용함(추정)',
    cost: '비용'
  };
  const hero = document.getElementById('roomHero');
  const heroBackground = hero.querySelector('.room-hero-background');
  const stageControls = Array.from(document.querySelectorAll('.journey-row[data-stage]'));
  const stageImages = {
    select: '../../static/images/room-connect/room-select.png',
    reserve: '../../static/images/room-connect/visit-reservation.png',
    schedule: '../../static/images/room-connect/schedule-confirmation.png',
    contract: '../../static/images/room-connect/safe-contract.png'
  };
  let activeStage = 'select';
  let backgroundTimer = 0;

  Object.keys(stageImages).forEach(function (stage) {
    const image = new Image();
    image.src = stageImages[stage];
  });

  function showStage(stage, commit) {
    if (!stageImages[stage]) return;
    if (commit) activeStage = stage;
    window.clearTimeout(backgroundTimer);
    heroBackground.classList.add('is-switching');
    backgroundTimer = window.setTimeout(function () {
      heroBackground.style.backgroundImage = 'url("' + stageImages[stage] + '")';
      hero.dataset.stage = stage;
      heroBackground.classList.remove('is-switching');
    }, 120);
    stageControls.forEach(function (control) {
      const active = control.dataset.stage === stage;
      control.classList.toggle('is-active', active);
      if (active) control.setAttribute('aria-current', 'step');
      else control.removeAttribute('aria-current');
    });
  }

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>"']/g, function (char) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char];
    });
  }

  function read(key) {
    if (key === visitKey) return visitCache;
    if (key === offerKey) return offerCache;
    try {
      const value = JSON.parse(localStorage.getItem(key) || '[]');
      return Array.isArray(value) ? value : [];
    } catch (error) {
      return [];
    }
  }

  function save(key, value) {
    if (key === visitKey) visitCache = value;
    if (key === offerKey) offerCache = value;
  }

  async function api(path, options) {
    const requestOptions = { credentials: 'same-origin', ...(options || {}) };
    if (!(requestOptions.body instanceof FormData)) {
      requestOptions.headers = {
        'Content-Type': 'application/json',
        ...(requestOptions.headers || {})
      };
    }
    const response = await fetch(path, requestOptions);
    const payload = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(payload.message || '서버 요청을 처리하지 못했습니다.');
    return payload;
  }

  async function loadServerActivity() {
    const results = await Promise.all([api('/api/visits'), api('/api/room-offers')]);
    visitCache = results[0].items || [];
    offerCache = results[1].items || [];
  }


  function uniqueSorted(values) {
    return Array.from(new Set(values.filter(Boolean))).sort(function (a, b) {
      return a.localeCompare(b, 'ko');
    });
  }

  function populateSidoOptions() {
    if (!lifestyleSido) return;
    const previous = lifestyleSido.value;
    const values = uniqueSorted(lifestyleAreas.map(function (area) { return area.sido; }));
    lifestyleSido.innerHTML = '<option value="">전체 지역</option>' + values.map(function (value) {
      return '<option value="' + escapeHtml(value) + '">' + escapeHtml(value) + '</option>';
    }).join('');
    if (values.includes(previous)) lifestyleSido.value = previous;
    populateSigunguOptions();
  }

  function populateSigunguOptions() {
    if (!lifestyleSigungu) return;
    const selectedSido = lifestyleSido ? lifestyleSido.value : '';
    const previous = lifestyleSigungu.value;
    const filtered = lifestyleAreas.filter(function (area) {
      return !selectedSido || area.sido === selectedSido;
    });
    const values = uniqueSorted(filtered.map(function (area) { return area.sigungu; }));
    lifestyleSigungu.innerHTML = '<option value="">전체 시·군·구</option>' + values.map(function (value) {
      return '<option value="' + escapeHtml(value) + '">' + escapeHtml(value) + '</option>';
    }).join('');
    if (values.includes(previous)) lifestyleSigungu.value = previous;
  }

  async function loadLifestyleAreas() {
    if (!lifestyleForm) return;
    const payload = await api('/api/lifestyle/areas');
    lifestyleAreas = payload.items || [];
    populateSidoOptions();
    if (lifestyleSigungu && lifestyleSigungu.value) {
      await setActiveRoomRegion(lifestyleSigungu.value, { sido: lifestyleSido ? lifestyleSido.value : '' });
    } else {
      await setActiveRoomRegion('성남시', { sido: '경기도' });
    }
  }

  function scoreRows(scores) {
    return Object.keys(lifestyleScoreLabels).map(function (key) {
      const value = Number(scores && scores[key]);
      if (!Number.isFinite(value)) return '';
      return '<div class="lifestyle-score-row"><span>' + lifestyleScoreLabels[key] + '</span>' +
        '<span class="lifestyle-score-bar"><i style="width:' + Math.max(0, Math.min(100, value)) + '%"></i></span>' +
        '<strong>' + Math.round(value) + '</strong></div>';
    }).join('');
  }

  function algorithmDisplay(algorithm) {
    const display = {
      weighted: {
        tagKr: '기준 방식',
        tagEn: 'Baseline / Weighted',
        titleKr: '가중 점수 추천',
        titleEn: 'Weighted Score'
      },
      cosine: {
        tagKr: '패턴 비교',
        tagEn: 'Cosine',
        titleKr: '생활 특성 유사도 추천',
        titleEn: 'Weighted Cosine Similarity'
      },
      knn_euclidean: {
        tagKr: '거리 비교',
        tagEn: 'KNN',
        titleKr: '가까운 생활 특성 추천',
        titleEn: 'KNN Euclidean'
      }
    };
    return display[algorithm] || {
      tagKr: 'AI/ML 추천',
      tagEn: 'AI/ML',
      titleKr: 'AI/ML 추천 결과',
      titleEn: 'AI/ML Recommendation'
    };
  }

  function algorithmHelp(algorithm) {
    const help = {
      weighted: '사용자가 중요하게 선택한 항목의 지역 점수를 가중평균하여 비교하는 기준 방식입니다. (Weighted Score)',
      cosine: '사용자의 중요도를 반영해 지역의 생활 특성 패턴이 얼마나 비슷한지 비교하는 방식입니다. (Weighted Cosine Similarity)',
      knn_euclidean: '사용자가 원하는 생활 특성과 가장 가까운 지역을 거리 기준으로 찾는 방식입니다. (KNN Euclidean)'
    };
    return help[algorithm] || '같은 생활 지표를 이용해 AI/ML 방식으로 계산한 추천 결과입니다.';
  }


  function selectedRegionName() {
    return lifestyleSigungu ? String(lifestyleSigungu.value || '').trim() : '';
  }

  function algorithmShortName(algorithm) {
    const names = {
      weighted: '가중 점수',
      cosine: '생활 특성 유사도',
      knn_euclidean: '가까운 생활 특성'
    };
    return names[algorithm] || 'AI/ML';
  }

  function renderTop3ScoreBars(items, algorithm, selectedItem) {
    if (!Array.isArray(items) || !items.length) return '';

    const comparisonItems = items.slice(0, 3);
    const scoreValues = comparisonItems.map(function (item) {
      return Number(item.score);
    });
    if (selectedItem) scoreValues.push(Number(selectedItem.score));

    const maxScore = Math.max.apply(null, scoreValues.filter(Number.isFinite).concat([1]));

    function barRow(item, label, selected) {
      if (!item) return '';

      const score = Number(item.score);
      const width = Number.isFinite(score)
        ? Math.max(8, Math.min(100, (score / maxScore) * 100))
        : 8;
      const rankText = Number.isFinite(Number(item.rank))
        ? '전체 ' + Number(item.rank) + '위'
        : '';

      return '<div class="lifestyle-top3-bar-row' + (selected ? ' is-selected-region' : '') + '">' +
        '<span class="lifestyle-top3-bar-rank">' + escapeHtml(label) + '</span>' +
        '<strong>' + escapeHtml(item.sigungu || item.areaCode || '지역') + '</strong>' +
        '<span class="lifestyle-top3-bar-track"><i style="width:' + width.toFixed(1) + '%"></i></span>' +
        '<em>' + (Number.isFinite(score) ? score.toFixed(1) : '-') + '</em>' +
        (selected ? '<small>' + escapeHtml(rankText) + '</small>' : '') +
      '</div>';
    }

    const selectedRow = selectedItem
      ? barRow(selectedItem, '선택', true)
      : '';

    const metricLabel = algorithm === 'knn_euclidean' ? '가까움 점수' : '추천 점수';

    const topRows = comparisonItems.map(function (item) {
      return barRow(item, 'TOP ' + item.rank, false);
    }).join('');

    return '<div class="lifestyle-top3-visual" aria-label="' +
      escapeHtml(algorithmShortName(algorithm)) + ' 선택 지역과 TOP 3 점수 비교">' +
      '<div class="lifestyle-top3-visual-title"><div><strong>선택 지역 · TOP 3 비교</strong><small>Selected Area vs TOP 3</small></div><em>' + escapeHtml(metricLabel) + '</em></div>' +
      selectedRow +
      (selectedRow ? '<div class="lifestyle-top3-separator"></div>' : '') +
      topRows +
    '</div>';
  }

  function renderSelectedFeatureScores(featureScores) {
    if (!featureScores || typeof featureScores !== 'object') return '';

    const weightNames = {
      transport: 'transportWeight',
      convenience: 'convenienceWeight',
      medical: 'medicalWeight',
      education: 'educationWeight',
      park: 'parkWeight',
      safety: 'safetyWeight',
      commercial: 'commercialWeight',
      quiet: 'quietWeight',
      cost: 'costWeight'
    };
    const formData = lifestyleForm ? new FormData(lifestyleForm) : null;

    const rows = Object.keys(lifestyleScoreLabels).map(function (key) {
      const value = Number(featureScores[key]);
      if (!Number.isFinite(value)) return '';

      const weight = formData ? Number(formData.get(weightNames[key])) : 0;
      const safeWeight = Number.isFinite(weight) ? Math.max(0, Math.min(5, weight)) : 0;
      const scoreWidth = Math.max(0, Math.min(100, value));
      const weightWidth = (safeWeight / 5) * 100;

      return '<div class="lifestyle-selected-feature-item" data-weight-level="' + safeWeight + '">' +
        '<div class="lifestyle-selected-feature-label"><span>' + escapeHtml(lifestyleScoreLabels[key]) + '</span></div>' +
        '<div class="lifestyle-selected-feature-metric is-preference-metric">' +
          '<div class="lifestyle-selected-feature-metric-head"><span>내 중요도</span><strong>' + safeWeight.toFixed(0) + ' / 5</strong></div>' +
          '<span class="lifestyle-selected-feature-bar is-preference"><i style="width:' + weightWidth.toFixed(1) + '%"></i></span>' +
        '</div>' +
        '<div class="lifestyle-selected-feature-metric is-region-metric">' +
          '<div class="lifestyle-selected-feature-metric-head"><span>지역 수준</span><strong>' + value.toFixed(0) + ' / 100</strong></div>' +
          '<span class="lifestyle-selected-feature-bar is-region"><i style="width:' + scoreWidth.toFixed(1) + '%"></i></span>' +
        '</div>' +
      '</div>';
    }).join('');

    if (!rows) return '';

    return '<div class="lifestyle-selected-feature-visual">' +
      '<div class="lifestyle-selected-feature-title"><div><strong>선택지역 9개 생활지표</strong><small>내 중요도 vs 지역 수준 · My Priority vs Region Score</small></div><span>지역 0~100 · 중요도 0~5</span></div>' +
      '<div class="lifestyle-selected-feature-legend"><span><i class="is-preference"></i>내 중요도</span><span><i class="is-region"></i>지역 수준</span></div>' +
      '<div class="lifestyle-selected-feature-grid">' + rows + '</div>' +
      '<p class="lifestyle-selected-feature-note"><i class="fa-solid fa-circle-info"></i> 지역 수준은 경기도 31개 시·군 상대점수(0~100), 내 중요도는 사용자가 선택한 값(0~5)입니다. 두 값은 척도가 다르므로 색상과 수치를 함께 비교해 주세요.</p>' +
    '</div>';
  }

  function renderSelectedRegionSummary(selectedArea) {
    if (!lifestyleSelectedRegionSummary) return;

    const region = selectedRegionName();
    const sido = lifestyleSido ? String(lifestyleSido.value || '').trim() : '';

    if (!region) {
      lifestyleSelectedRegionSummary.hidden = true;
      lifestyleSelectedRegionSummary.innerHTML = '';
      return;
    }

    const hasSelectedResult = selectedArea &&
      selectedArea.results &&
      Object.keys(selectedArea.results).length;

    lifestyleSelectedRegionSummary.hidden = false;
    lifestyleSelectedRegionSummary.innerHTML =
      '<div class="lifestyle-selected-region-head">' +
        '<div><span class="eyebrow">선택 지역 분석</span><h4>' + escapeHtml(region) +
        '<small>' + escapeHtml((sido ? sido + ' · ' : '') + '이사 관심 지역') + '</small></h4></div>' +
        '<p><strong>' + escapeHtml(region) + '</strong>을 이사 관심 지역으로 보고 분석합니다. ' +
        '먼저 <strong>9개 생활지표 수준</strong>을 확인하고, 아래 각 추천 방식에서 선택 지역 점수와 전체 순위, ' +
        escapeHtml(sido || '선택 시·도') + ' 전체 TOP 3를 비교합니다.' +
        (hasSelectedResult ? '' : ' 선택 지역 계산 결과가 없는 경우에는 TOP 3만 표시됩니다.') +
        '</p>' +
      '</div>' +
      renderSelectedFeatureScores(selectedArea && selectedArea.featureScores);
  }

  function renderMlItem(item, algorithm) {
    const sigungu = item.sigungu || '';
    const score = Number(item.score);
    const scoreText = Number.isFinite(score) ? score.toFixed(1) : '-';
    const distance = item.distance == null ? '' :
      '<span class="lifestyle-distance">거리 (Distance) ' + Number(item.distance).toFixed(4) + '</span>';
    const reason = item.reason ?
      '<div class="lifestyle-ml-reason"><strong><i class="fa-solid fa-lightbulb"></i> 추천 이유</strong><p>' + escapeHtml(item.reason) + '</p></div>' : '';
    const scoreLabel = algorithm === 'knn_euclidean' ? '가까움 점수' : '추천 점수';

    return '<article class="lifestyle-result-card lifestyle-ml-card' + (item.rank === 1 ? ' is-first' : '') + '">' +
      '<span class="lifestyle-rank">TOP ' + item.rank + '</span>' +
      '<p class="eyebrow">' + escapeHtml(algorithm === 'weighted' ? '기준 추천 (Baseline)' : 'AI 추천 (AI/ML)') + '</p>' +
      '<h4>' + escapeHtml(sigungu || item.areaCode || '추천 지역') + '</h4>' +
      '<p class="area-sub">' + escapeHtml(item.sido || '') + ' · ' + escapeHtml(item.areaCode || '') + '</p>' +
      '<div class="lifestyle-total"><strong>' + scoreText + '</strong><span>' + scoreLabel + '</span>' + distance + '</div>' +
      reason +
      '<div class="lifestyle-result-actions"><button class="button button-secondary" type="button" data-related-room data-sido="' + escapeHtml(item.sido || '') + '" data-sigungu="' + escapeHtml(sigungu) + '"><i class="fa-solid fa-house"></i> 관련 방 보기</button></div>' +
    '</article>';
  }

  function renderLifestyleRecommendations(payload) {
    const algorithms = Array.isArray(payload.algorithms) ? payload.algorithms : [];
    if (lifestyleResultHeading) lifestyleResultHeading.hidden = false;
    if (lifestyleResultScope) {
      const areaCount = Number(payload.areaCount || 0);
      const selected = selectedRegionName();
      const sido = lifestyleSido ? String(lifestyleSido.value || '').trim() : '';
      lifestyleResultScope.textContent =
        (selected ? '관심 지역 ' + selected + ' · ' : '') +
        (sido || '전체 지역') + ' ' + areaCount + '개 지역 · ' +
        Number(payload.featureCount || 0) + '개 생활 지표 비교';
    }
    const selectedArea = payload.selectedArea || null;
    renderSelectedRegionSummary(selectedArea);
    if (lifestyleDataNotice) {
      const overlap = payload.overlapAtK || {};
      const common = Number(overlap.cosine_knn || 0);
      lifestyleDataNotice.textContent = (payload.notice || 'Python AI/ML 추천 결과입니다.') +
        ' 생활 특성 유사도 추천(Cosine)과 가까운 생활 특성 추천(KNN)의 상위 3개 공통 지역은 ' + common + '개입니다.';
    }

    if (!algorithms.length) {
      lifestyleResults.innerHTML = '<div class="lifestyle-result-empty">현재 조건으로 AI/ML 추천 결과를 만들 수 없습니다.</div>';
      return;
    }

    lifestyleResults.innerHTML = algorithms.map(function (group) {
      const items = Array.isArray(group.items) ? group.items : [];
      const display = algorithmDisplay(group.algorithm);
      const cards = items.length ? items.map(function (item) {
        return renderMlItem(item, group.algorithm);
      }).join('') : '<div class="lifestyle-result-empty">추천 결과가 없습니다.</div>';
      return '<section class="lifestyle-algorithm-block" data-algorithm="' + escapeHtml(group.algorithm || '') + '">' +
        '<div class="lifestyle-algorithm-heading"><div><span class="lifestyle-algorithm-tag"><span class="lifestyle-label-kr">' + escapeHtml(display.tagKr) + '</span><span class="lifestyle-label-en">' + escapeHtml(display.tagEn) + '</span></span>' +
        '<h4><span class="lifestyle-title-kr">' + escapeHtml(display.titleKr) + '</span><span class="lifestyle-title-en">' + escapeHtml(display.titleEn) + '</span></h4></div>' +
        '<p>' + escapeHtml(algorithmHelp(group.algorithm)) + '</p></div>' +
        renderTop3ScoreBars(items, group.algorithm,
          selectedArea && selectedArea.results ? selectedArea.results[group.algorithm] : null) +
        '<div class="lifestyle-result-grid lifestyle-ml-grid">' + cards + '</div>' +
      '</section>';
    }).join('');
  }

  function lifestylePayload() {
    const data = new FormData(lifestyleForm);
    return {
      sido: data.get('sido') || null,
      // sigungu는 추천 후보를 제한하지 않고 사용자의 이사 관심 지역 비교 대상으로 전달한다.
      sigungu: data.get('sigungu') || null,
      transportWeight: Number(data.get('transportWeight')),
      convenienceWeight: Number(data.get('convenienceWeight')),
      medicalWeight: Number(data.get('medicalWeight')),
      educationWeight: Number(data.get('educationWeight')),
      parkWeight: Number(data.get('parkWeight')),
      safetyWeight: Number(data.get('safetyWeight')),
      commercialWeight: Number(data.get('commercialWeight')),
      quietWeight: Number(data.get('quietWeight')),
      costWeight: Number(data.get('costWeight'))
    };
  }


  function formatPhone(value) {
    const digits = String(value || '').replace(/\D/g, '').slice(0, 11);

    if (digits.length <= 3) return digits;

    if (digits.startsWith('02')) {
      if (digits.length <= 5) {
        return digits.replace(/(\d{2})(\d+)/, '$1-$2');
      }
      if (digits.length <= 9) {
        return digits.replace(/(\d{2})(\d{3})(\d+)/, '$1-$2-$3');
      }
      return digits.replace(/(\d{2})(\d{4})(\d+)/, '$1-$2-$3');
    }

    if (digits.length <= 7) {
      return digits.replace(/(\d{3})(\d+)/, '$1-$2');
    }

    if (digits.length <= 10) {
      return digits.replace(/(\d{3})(\d{3})(\d+)/, '$1-$2-$3');
    }

    return digits.replace(/(\d{3})(\d{4})(\d+)/, '$1-$2-$3');
  }

  function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    window.setTimeout(function () { toast.classList.remove('is-visible'); }, 2400);
  }

  function formatPropertyPrice(room) {
    return Number(room.deposit || 0).toLocaleString('ko-KR') + ' / ' +
      Number(room.monthlyRent || 0).toLocaleString('ko-KR') + '만 원';
  }

  function renderRooms() {
    if (visitRegionNotice) {
      visitRegionNotice.innerHTML = activeRoomRegion
        ? '<strong>' + escapeHtml(activeRoomRegion) + '</strong> 기준 DB 등록 매물입니다.'
        : 'Lifestyle Match에서 지역을 선택하면 해당 지역의 방문 가능 매물을 보여드립니다.';
    }
    if (!rooms.length) {
      roomList.innerHTML = '<div class="visit-room-empty">현재 이 지역에 방문 가능한 DB 매물이 없습니다.</div>';
      return;
    }
    roomList.innerHTML = rooms.map(function (room) {
      const area = [room.sido, room.sigungu, room.dong].filter(Boolean).join(' ');
      const sampleText = room.sampleData ? ' · 개발용 샘플 매물' : '';
      const badge = room.sampleData ? '<span class="visit-room-photo-badge">샘플 사진</span>' : '';
      return '<button class="visit-room-card" type="button" data-room="' + escapeHtml(room.id) + '">' +
        '<span class="visit-room-photo">' +
          '<i class="fa-solid fa-house visit-room-photo-fallback" aria-hidden="true"></i>' +
          '<img src="' + escapeHtml(room.thumbnailUrl || '') + '" alt="' + escapeHtml(room.title) + ' 대표 사진" loading="lazy" referrerpolicy="no-referrer" onerror="this.style.display=\'none\'">' +
          badge +
          '<small class="visit-room-photo-credit">' + escapeHtml(room.photoCredit || '') + '</small>' +
        '</span>' +
        '<span class="visit-room-copy"><span>방문 가능' + sampleText + '</span><h3>' + escapeHtml(room.title) + '</h3><p>' + escapeHtml(area) + '</p>' +
        '<span class="room-meta"><strong>' + escapeHtml(formatPropertyPrice(room)) + '</strong><small>' + escapeHtml(room.availableTime || '') + '</small></span></span></button>';
    }).join('');
  }

  async function loadProperties(sido, sigungu) {
    const query = new URLSearchParams({ sido: sido || '경기도', sigungu: sigungu });
    const payload = await api('/api/lifestyle/properties?' + query.toString());
    return payload.items || [];
  }

  async function setActiveRoomRegion(sigungu, options) {
    const region = String(sigungu || '').trim();
    if (!region) return;
    const sido = String(
      (options && options.sido) ||
      (lifestyleSido && lifestyleSido.value) ||
      activeRoomSido || '경기도'
    ).trim();

    activeRoomRegion = region;
    activeRoomSido = sido;
    if (visitRegionNotice) {
      visitRegionNotice.innerHTML = '<strong>' + escapeHtml(region) + '</strong> DB 매물을 불러오는 중입니다.';
    }

    try {
      rooms = await loadProperties(sido, region);
    } catch (error) {
      rooms = [];
      renderRooms();
      showToast(error.message);
      return;
    }

    document.getElementById('visitRoom').value = '';
    document.getElementById('selectedRoomName').value = '방을 먼저 선택해 주세요';
    renderRooms();
    updateTimeOptions();

    if (options && options.scroll) {
      document.getElementById('visitSection').scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    if (options && options.toast) {
      showToast(region + ' DB 매물을 표시했습니다.');
    }
  }

  function approvedSlot(roomId, date, time, ignoredId) {
    return read(visitKey).some(function (visit) {
      return visit.status === 'approved' && visit.roomId === roomId && visit.date === date && visit.time === time && visit.id !== ignoredId;
    });
  }

  function updateTimeOptions() {
    const roomId = document.getElementById('visitRoom').value;
    const date = visitDate.value;
    const previous = visitTime.value;
    if (!roomId || !date) {
      visitTime.innerHTML = '<option value="">날짜와 방을 먼저 선택해 주세요</option>';
      visitTime.disabled = true;
      return;
    }
    visitTime.disabled = false;
    visitTime.innerHTML = '<option value="">시간 선택</option>' + Array.from({ length: 24 }, function (_, hour) {
      const time = String(hour).padStart(2, '0') + ':00';
      const closed = approvedSlot(roomId, date, time);
      return '<option value="' + time + '"' + (closed ? ' disabled' : '') + '>' + time + (closed ? ' (마감)' : '') + '</option>';
    }).join('');
    if (previous && !approvedSlot(roomId, date, previous)) visitTime.value = previous;
  }

  function selectRoom(id) {
    const room = rooms.find(function (item) { return item.id === id; });
    if (!room) return;
    document.getElementById('visitRoom').value = room.id;
    document.getElementById('selectedRoomName').value = room.title;
    document.querySelectorAll('[data-room]').forEach(function (button) {
      button.classList.toggle('is-selected', button.dataset.room === room.id);
    });
    updateTimeOptions();
  }

  function renderActivity() {
    const visits = read(visitKey);
    const offers = read(offerKey);
    document.getElementById('visitCount').textContent = visits.length + '건';
    document.getElementById('offerCount').textContent = offers.length + '건';
    document.getElementById('visitList').innerHTML = visits.length ? visits.map(function (item) {
      const status = item.status || 'pending';
      const statusText = status === 'approved' ? '예약 확정' : status === 'rejected' ? '요청 거절' : '승인 대기';
      const actions = status === 'pending'
        ? '<div class="approval-actions"><button type="button" data-action="approve" data-visit-id="' + item.id + '">승인</button><button type="button" data-action="reject" data-visit-id="' + item.id + '">거절</button></div>'
        : '';
      return '<div class="activity-item"><div><strong>' + escapeHtml(item.title) + '</strong><small>' + escapeHtml(item.date) + ' · ' + escapeHtml(item.time) + ' · ' + escapeHtml(formatPhone(item.phone)) + '</small>' + actions + '</div><span class="activity-status is-' + status + '">' + statusText + '</span></div>';
    }).join('') : '<div class="activity-empty">신청한 방문 일정이 없습니다.</div>';
    document.getElementById('offerList').innerHTML = offers.length ? offers.map(function (item) {
      const image = item.imageUrls && item.imageUrls.length
        ? '<img class="activity-offer-thumb" src="' + escapeHtml(item.imageUrls[0]) + '" alt="' + escapeHtml(item.title) + ' 대표 사진">'
        : '';
      return '<div class="activity-item activity-item-with-thumb">' + image + '<div><strong>' + escapeHtml(item.title) + '</strong><small>입주 가능 ' + escapeHtml(item.moveIn) + ' · ' + escapeHtml(item.agreement) + '</small></div><span class="activity-status">연결 준비</span></div>';
    }).join('') : '<div class="activity-empty">내놓은 방이 없습니다.</div>';
  }


  if (lifestyleForm) {
    lifestyleForm.querySelectorAll('input[type="range"]').forEach(function (input) {
      const output = lifestyleForm.querySelector('[data-weight-output="' + input.name + '"]');
      const sync = function () { if (output) output.value = input.value; };
      input.addEventListener('input', sync);
      sync();
    });

    if (lifestyleSido) {
      lifestyleSido.addEventListener('change', function () {
        populateSigunguOptions();
        if (lifestyleSigungu && lifestyleSigungu.value) {
          setActiveRoomRegion(lifestyleSigungu.value, { sido: lifestyleSido ? lifestyleSido.value : '' });
        }
      });
    }

    if (lifestyleSigungu) {
      lifestyleSigungu.addEventListener('change', function () {
        if (lifestyleSigungu.value) setActiveRoomRegion(lifestyleSigungu.value, { sido: lifestyleSido ? lifestyleSido.value : '' });
      });
    }

    lifestyleForm.addEventListener('submit', async function (event) {
      event.preventDefault();
      const submitButton = lifestyleForm.querySelector('button[type="submit"]');
      const originalText = submitButton ? submitButton.innerHTML : '';
      if (submitButton) {
        submitButton.disabled = true;
        submitButton.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> 분석 중...';
      }
      try {
        const payload = await api('/api/lifestyle/recommend/ml', {
          method: 'POST',
          body: JSON.stringify(lifestylePayload())
        });
        renderLifestyleRecommendations(payload);
        if (lifestyleSigungu && lifestyleSigungu.value) {
          setActiveRoomRegion(lifestyleSigungu.value, { sido: lifestyleSido ? lifestyleSido.value : '' });
        }
        lifestyleResultHeading.scrollIntoView({ behavior: 'smooth', block: 'start' });
      } catch (error) {
        showToast(error.message);
      } finally {
        if (submitButton) {
          submitButton.disabled = false;
          submitButton.innerHTML = originalText;
        }
      }
    });
  }

  if (lifestyleResults) {
    lifestyleResults.addEventListener('click', function (event) {
      const button = event.target.closest('[data-related-room]');
      if (!button) return;
      const sigungu = button.dataset.sigungu;
      if (!sigungu) return;
      setActiveRoomRegion(sigungu, {
        sido: button.dataset.sido || '',
        scroll: true,
        toast: true
      });
    });
  }

  roomList.addEventListener('click', function (event) {
    const card = event.target.closest('[data-room]');
    if (!card) return;
    selectRoom(card.dataset.room);
    visitForm.scrollIntoView({ behavior: 'smooth', block: 'center' });
  });
  visitDate.addEventListener('change', updateTimeOptions);

  const phoneInput = visitForm.querySelector('input[name="phone"]');
  if (phoneInput) {
    phoneInput.addEventListener('input', function () {
      this.value = formatPhone(this.value);
    });
  }

  visitForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    const data = new FormData(visitForm);
    const room = rooms.find(function (item) { return item.id === data.get('room'); });
    if (!room) {
      showToast('방문할 방을 먼저 선택해 주세요.');
      return;
    }
    if (!data.get('time') || approvedSlot(room.id, data.get('date'), data.get('time'))) {
      showToast('이미 마감된 시간이므로 다른 시간을 선택해 주세요.');
      updateTimeOptions();
      return;
    }
    try {
      const payload = await api('/api/visits', { method: 'POST', body: JSON.stringify({ roomId: room.id, title: room.title, date: data.get('date'), time: data.get('time'), phone: formatPhone(data.get('phone')), question: data.get('question') }) });
      visitCache.unshift(payload.item);
    } catch (error) {
      showToast(error.message);
      return;
    }
    visitForm.reset();
    document.getElementById('selectedRoomName').value = '방을 먼저 선택해 주세요';
    document.querySelectorAll('[data-room]').forEach(function (button) { button.classList.remove('is-selected'); });
    updateTimeOptions();
    renderActivity();
    showToast('방문 승인 요청이 접수되었습니다.');
  });

  document.getElementById('visitList').addEventListener('click', async function (event) {
    const button = event.target.closest('[data-visit-id]');
    if (!button) return;
    const id = Number(button.dataset.visitId);
    const visits = read(visitKey);
    const visit = visits.find(function (item) { return item.id === id; });
    if (!visit || (visit.status && visit.status !== 'pending')) return;
    try {
      const action = button.dataset.action === 'approve' ? 'approve' : 'reject';
      const payload = await api('/api/visits/' + visit.id + '/' + action, { method: 'PATCH', body: '{}' });
      Object.assign(visit, payload.item);
      showToast(action === 'approve' ? '방문 요청을 승인했습니다. 예약이 확정되었습니다.' : '방문 요청을 거절했습니다.');
    } catch (error) {
      showToast(error.message);
      return;
    }
    renderActivity();
    updateTimeOptions();
  });

  function clearOfferPhotoPreviews() {
    offerPreviewUrls.forEach(function (url) { URL.revokeObjectURL(url); });
    offerPreviewUrls = [];
    if (offerPhotoPreview) offerPhotoPreview.innerHTML = '';
    if (offerPhotoCount) offerPhotoCount.textContent = '0 / 5장';
  }

  function renderOfferPhotoPreviews(files) {
    clearOfferPhotoPreviews();

    const selected = Array.from(files || []);
    if (selected.length > 5) {
      showToast('방 사진은 최대 5장까지 선택할 수 있습니다.');
      if (offerImages) offerImages.value = '';
      return;
    }

    const invalid = selected.find(function (file) {
      return !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
        file.size > 5 * 1024 * 1024;
    });
    if (invalid) {
      showToast('사진은 JPG·PNG·WEBP 형식, 한 장당 5MB 이하만 가능합니다.');
      if (offerImages) offerImages.value = '';
      return;
    }

    if (offerPhotoCount) offerPhotoCount.textContent = selected.length + ' / 5장';

    if (offerPhotoPreview) {
      offerPhotoPreview.innerHTML = selected.map(function (file, index) {
        const url = URL.createObjectURL(file);
        offerPreviewUrls.push(url);
        return '<figure class="offer-photo-preview-item">' +
          '<img src="' + url + '" alt="업로드 사진 미리보기 ' + (index + 1) + '">' +
          '<figcaption>' + (index === 0 ? '대표사진' : (index + 1) + '번째 사진') + '</figcaption>' +
          '</figure>';
      }).join('');
    }
  }

  if (offerImages) {
    offerImages.addEventListener('change', function () {
      renderOfferPhotoPreviews(offerImages.files);
    });
  }

  offerForm.addEventListener('submit', async function (event) {
    event.preventDefault();

    const images = offerImages ? Array.from(offerImages.files || []) : [];
    if (!images.length) {
      showToast('방 사진을 최소 1장 선택해 주세요.');
      return;
    }
    if (images.length > 5) {
      showToast('방 사진은 최대 5장까지 업로드할 수 있습니다.');
      return;
    }

    const data = new FormData(offerForm);

    try {
      const payload = await api('/api/room-offers', { method: 'POST', body: data });
      offerCache.unshift(payload.item);

      const district = String(data.get('district') || '');
      const matchedArea = lifestyleAreas.find(function (area) {
        return district.includes(area.sigungu);
      });
      if (matchedArea) {
        await setActiveRoomRegion(matchedArea.sigungu, { sido: matchedArea.sido });
      }
    } catch (error) {
      showToast(error.message);
      return;
    }

    offerForm.reset();
    clearOfferPhotoPreviews();
    renderActivity();
    showToast('사진과 함께 퇴실 예정 방이 등록되었습니다.');
    document.getElementById('activitySection').scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  document.querySelectorAll('[data-scroll]').forEach(function (button) {
    button.addEventListener('click', function () {
      document.getElementById(button.dataset.scroll).scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });

  stageControls.forEach(function (control) {
    control.addEventListener('mouseenter', function () { showStage(control.dataset.stage, false); });
    control.addEventListener('focus', function () { showStage(control.dataset.stage, false); });
    control.addEventListener('mouseleave', function () { showStage(activeStage, false); });
    control.addEventListener('blur', function () { showStage(activeStage, false); });
    if (control.tagName === 'BUTTON') {
      control.addEventListener('click', function () {
        showStage(control.dataset.stage, true);
        const target = document.getElementById(control.dataset.target);
        if (target) target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      });
    }
  });

  if ('IntersectionObserver' in window) {
    const sectionStages = [
      { element: document.getElementById('availableRooms'), stage: 'select' },
      { element: document.getElementById('visitForm'), stage: 'reserve' },
      { element: document.getElementById('activitySection'), stage: 'schedule' }
    ];
    const stageObserver = new IntersectionObserver(function (entries) {
      const visible = entries.filter(function (entry) { return entry.isIntersecting; }).sort(function (a, b) { return b.intersectionRatio - a.intersectionRatio; })[0];
      if (!visible) return;
      const match = sectionStages.find(function (item) { return item.element === visible.target; });
      if (match) {
        activeStage = match.stage;
        stageControls.forEach(function (control) {
          const active = control.dataset.stage === activeStage;
          control.classList.toggle('is-active', active);
          if (active) control.setAttribute('aria-current', 'step');
          else control.removeAttribute('aria-current');
        });
      }
    }, { threshold: [0.35, 0.6] });
    sectionStages.forEach(function (item) { if (item.element) stageObserver.observe(item.element); });
  }

  renderRooms();
  if (window.ZipaiAuth) await window.ZipaiAuth.ready;
  try {
    await loadServerActivity();
  } catch (error) {
    showToast(error.message);
  }
  try {
    await loadLifestyleAreas();
  } catch (error) {
    if (lifestyleDataNotice) {
      lifestyleDataNotice.textContent = '지역 추천 데이터를 불러오지 못했습니다. ' + error.message;
    }
  }
  renderActivity();
  updateTimeOptions();
})();
