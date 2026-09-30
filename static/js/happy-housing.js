(function () {
  'use strict';
  const form = document.getElementById('happyEligibilityForm');
  const result = document.getElementById('eligibilityResult');
  if (!form || !result) return;

  const $ = function (id) { return document.getElementById(id); };
  const typeSelect = $('applicantType');
  const typeInfo = {
    student: { label: '대학생·취업준비생', guide: '재학·입학·복학 예정 또는 취업준비생 인정기간과 혼인 여부를 확인하세요.', question: '재학·입학·복학 예정 또는 공고에서 정한 취업준비생이며 혼인 중이 아닌가요?' },
    youth: { label: '청년', guide: '공고일 기준 연령, 혼인 여부와 소득활동 요건을 확인하세요.', question: '공고에서 정한 청년 연령 또는 소득활동 요건과 혼인 여부를 충족하나요?' },
    newlywed: { label: '신혼부부·예비신혼부부', guide: '혼인기간, 예비혼인 증빙, 자녀 여부와 세대구성을 확인하세요.', question: '혼인기간·예비혼인·자녀 등 공고에서 정한 신혼부부 요건을 충족하나요?' },
    singleParent: { label: '한부모가족', guide: '자녀 연령과 한부모가족 인정 범위를 확인하세요.', question: '공고에서 정한 자녀 연령 및 한부모가족 요건을 충족하나요?' },
    senior: { label: '고령자', guide: '공고일 기준 연령과 세대구성원 요건을 확인하세요.', question: '공고일 기준 고령자 연령과 세대구성 요건을 충족하나요?' },
    benefit: { label: '주거급여수급자', guide: '공고일 현재 주거급여 수급 여부를 확인하세요.', question: '공고일 현재 주거급여 수급자 요건을 충족하나요?' },
    industrial: { label: '산업단지 근로자', guide: '대상 산업단지 및 입주기업 재직·예정 여부를 확인하세요.', question: '공고가 지정한 산업단지 입주기업의 근로자 또는 입주예정자인가요?' }
  };


  // 화면의 신청 계층 값과 크롤러가 저장한 applicant_type을 연결한다.
  const noticeApplicantTypeMap = {
    student: 'student',
    youth: 'youth',
    newlywed: 'newlywed_single_parent',
    singleParent: 'newlywed_single_parent',
    senior: 'senior',
    benefit: 'benefit',
    industrial: 'industrial'
  };

  let selectedNoticeId = null;
  let selectedNoticeTitle = null;
  let selectedNoticeRule = null;
  let selectedNoticeRuleResponse = null;

  // 기존 HTML/CSS 구조를 최대한 유지하기 위해 공고 영역은 JavaScript에서 동적으로 추가한다.
  const noticeUiStyle = document.createElement('style');
  noticeUiStyle.textContent =
    '#happyHousingNoticeList article{position:relative;transition:transform .18s ease,box-shadow .18s ease,border-color .18s ease}' +
    '#happyHousingNoticeList article:hover{transform:translateY(-2px);box-shadow:0 8px 24px rgba(15,23,42,.08)}' +
    '.hh-match-badge{display:inline-flex;align-items:center;gap:.35rem;padding:.28rem .58rem;border-radius:999px;font-size:.78rem;font-weight:700;margin:.38rem 0}' +
    '.hh-match-candidate{background:#eaf8f2;color:#0f7a55}' +
    '.hh-match-review{background:#fff5dc;color:#a86400}' +
    '.hh-match-fail{background:#fff0f0;color:#b42318}' +
    '.hh-match-reason{display:block;margin-top:.2rem;line-height:1.55;color:#52606d}' +
    '.hh-match-counts{display:flex;flex-wrap:wrap;gap:.45rem;margin-top:.5rem}' +
    '.hh-match-count{font-size:.78rem;padding:.18rem .45rem;border-radius:999px;background:#f4f7fb;color:#52606d}' +
    '.hh-selected-notice{outline:2px solid #2563eb;outline-offset:-2px}' +
    '.hh-search{display:grid;grid-template-columns:minmax(220px,2fr) minmax(130px,1fr) minmax(130px,1fr) auto;gap:.6rem;margin:1rem 0 1.2rem}' +
    '.hh-search input,.hh-search select{min-width:0;padding:.78rem .85rem;border:1px solid #d9e1ec;border-radius:10px;background:#fff;color:#172033}' +
    '.hh-search button{padding:.78rem 1.1rem;border:0;border-radius:10px;background:#2563eb;color:#fff;font-weight:700;cursor:pointer}' +
    '@media(max-width:760px){.hh-search{grid-template-columns:1fr 1fr}.hh-search input{grid-column:1/-1}.hh-search button{grid-column:1/-1}}';
  document.head.appendChild(noticeUiStyle);

  const noticePanel = document.createElement('div');
  noticePanel.className = 'panel';
  noticePanel.id = 'happyHousingNoticePanel';
  noticePanel.setAttribute('aria-live', 'polite');
  noticePanel.innerHTML =
    '<div class="section-heading">' +
      '<p class="eyebrow">LIVE NOTICE</p>' +
      '<h2>현재 행복주택 공고</h2>' +
      '<p id="happyHousingNoticeSummary">LH에서 수집한 최신 공고를 불러오고 있습니다.</p>' +
    '</div>' +
    '<form class="hh-search" id="happyHousingNoticeSearch">' +
      '<input id="happyHousingKeyword" type="search" placeholder="공고명, 지역, 주택유형 검색" aria-label="LH 공고 검색어">' +
      '<input id="happyHousingRegion" type="search" placeholder="지역 예: 수원, 경기" aria-label="LH 공고 지역">' +
      '<select id="happyHousingStatus" aria-label="LH 공고 상태"><option value="">전체 상태</option><option value="공고중">공고중</option><option value="정정공고중">정정공고중</option></select>' +
      '<button type="submit">공고 검색</button>' +
    '</form>' +
    '<div class="eligibility-list" id="happyHousingNoticeList"></div>' +
    '<div class="notice-box" id="happyHousingCrawlStatus" hidden></div>';

  const diagnosisSection = form.closest('.diagnosis-section');
  if (diagnosisSection && diagnosisSection.parentNode) {
    diagnosisSection.parentNode.insertBefore(noticePanel, diagnosisSection.nextSibling);
  } else if (result.parentNode) {
    result.parentNode.appendChild(noticePanel);
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function noticeTypeForForm() {
    return noticeApplicantTypeMap[typeSelect.value] || null;
  }

  function formatDate(value) {
    if (!value) return '-';
    return escapeHtml(value);
  }

  function ruleValue(root, key) {
    if (!root) return null;
    if (root[key] !== undefined && root[key] !== null) return root[key];
    if (root.rules && root.rules[key] !== undefined && root.rules[key] !== null) return root.rules[key];
    if (root.parsed && root.parsed[key] !== undefined && root.parsed[key] !== null) return root.parsed[key];
    return null;
  }

  const OFFICIAL_INCOME_LIMIT_WON = {
    1: { 120: 4576036 },
    2: { 110: 6452897, 120: 7039524, 130: 7626151 },
    3: { 100: 8168429, 110: 8985272, 120: 9802115 },
    4: { 100: 8802202, 110: 9682422, 120: 10562642 },
    5: { 100: 9326985, 110: 10259684, 120: 11192382 },
    6: { 100: 9906263, 110: 10896889, 120: 11887516 }
  };

  function incomeLimitFromPercent(rules) {
    const householdEl = $('householdSize');
    const householdSize = householdEl ? Number(householdEl.value) : 1;
    if (!Number.isFinite(householdSize) || householdSize < 1 || householdSize > 6) return null;

    let percent = null;
    if (householdSize === 1) percent = ruleValue(rules, 'income_percent_one_person');
    else if (householdSize === 2) percent = ruleValue(rules, 'income_percent_two_person');
    else percent = ruleValue(rules, 'income_percent_general');

    const row = OFFICIAL_INCOME_LIMIT_WON[householdSize];
    const won = row && percent != null ? row[Number(percent)] : null;
    return won == null ? null : Number(won) / 10000;
  }

  function incomeLimitFromTable(rules) {
    const table = ruleValue(rules, 'income_amount_table');
    if (!table || !Array.isArray(table.rows) || table.rows.length === 0) {
      return incomeLimitFromPercent(rules);
    }

    const householdEl = $('householdSize');
    const householdSize = householdEl ? Number(householdEl.value) : 1;
    if (!Number.isFinite(householdSize) || householdSize < 1) return null;

    let row = table.rows.find(function (item) {
      return Number(item.household_size) === householdSize;
    });

    if (!row && householdSize > 6) {
      const six = table.rows.find(function (item) { return Number(item.household_size) === 6; });
      const extraWon = Number(table.extra_per_person_won);
      if (six && Number.isFinite(Number(six.general_limit_won)) && Number.isFinite(extraWon)) {
        const won = Number(six.general_limit_won) + (householdSize - 6) * extraWon;
        return won / 10000;
      }
    }

    if (!row) return null;
    if (row.general_limit_manwon !== undefined && row.general_limit_manwon !== null) {
      return Number(row.general_limit_manwon);
    }
    if (row.general_limit_won !== undefined && row.general_limit_won !== null) {
      return Number(row.general_limit_won) / 10000;
    }
    return null;
  }

  function applyIncomeLimitFromSelectedRule() {
    const incomeLimitEl = $('incomeLimit');
    if (!incomeLimitEl) return;

    incomeLimitEl.placeholder = '공고 기준 자동반영';
    if (!selectedNoticeRule) {
      incomeLimitEl.value = '';
      updateCompare();
      updateProgress();
      return;
    }

    if (ruleValue(selectedNoticeRule, 'income_requirement_exempt') === true) {
      incomeLimitEl.value = '';
      incomeLimitEl.placeholder = '소득요건 배제';
      updateCompare();
      updateProgress();
      return;
    }

    let incomeAmount = incomeLimitFromTable(selectedNoticeRule);
    if (incomeAmount == null) incomeAmount = ruleValue(selectedNoticeRule, 'income_limit_manwon');
    incomeLimitEl.value = incomeAmount == null ? '' : String(incomeAmount);
    if (incomeAmount == null) incomeLimitEl.placeholder = '공고문 확인 필요';
    updateCompare();
    updateProgress();
  }

  function clearLiveRuleFields() {
    selectedNoticeId = null;
    selectedNoticeTitle = null;
    selectedNoticeRule = null;
    selectedNoticeRuleResponse = null;
    if ($('noticeDate')) $('noticeDate').value = '';
    if ($('incomeLimit')) $('incomeLimit').value = '';
    if ($('assetLimit')) $('assetLimit').value = '';
    if ($('carLimit')) $('carLimit').value = '';
  }

  function applyRuleToForm(notice, ruleResponse) {
    let parsed = null;
    try {
      parsed = JSON.parse(ruleResponse.ruleJson || '{}');
    } catch (ignore) {
      parsed = {};
    }
    const rules = parsed;
    selectedNoticeId = Number(notice.noticeId);
    selectedNoticeTitle = notice.title || '';
    selectedNoticeRule = rules;
    selectedNoticeRuleResponse = ruleResponse;

    if ($('noticeDate') && notice.noticeDate) $('noticeDate').value = notice.noticeDate;

    // 크롤러가 저장한 가구원수별 실제 월평균소득 금액표를 적용한다.
    applyIncomeLimitFromSelectedRule();

    let assetLimit = ruleValue(rules, 'asset_limit_manwon');
    const assetLimitEl = $('assetLimit');
    if (assetLimitEl) {
      if (ruleValue(rules, 'asset_requirement_exempt') === true) {
        assetLimitEl.value = '';
        assetLimitEl.placeholder = '총자산 요건 배제';
      } else {
        if (assetLimit == null) assetLimit = ruleValue(rules, 'standard_asset_limit_manwon');
        assetLimitEl.value = assetLimit == null ? '' : assetLimit;
        assetLimitEl.placeholder = assetLimit == null ? '공고문 확인 필요' : '공고 기준 자동반영';
      }
    }

    let carLimit = ruleValue(rules, 'car_limit_manwon');
    if (ruleValue(rules, 'car_no_ownership_required') === true) carLimit = 0;
    if ($('carLimit')) $('carLimit').value = carLimit == null ? '' : carLimit;

    updateAge();
    updateCompare();
    updateProgress();

    const summary = $('happyHousingNoticeSummary');
    if (summary) {
      const validation = ruleResponse.validationStatus === 'parsed' ? '자동 추출 완료' : '원문 추가 확인 필요';
      summary.textContent = '적용 공고: ' + selectedNoticeTitle + ' · ' + validation +
        ' · 소득/자산/자동차 기준을 입력란에 반영했습니다.';
    }
  }

  async function selectNotice(notice) {
    const applicantType = noticeTypeForForm();
    if (!applicantType || !notice || !notice.noticeId) return;

    const response = await fetch('/api/happy-housing/notices/' + encodeURIComponent(notice.noticeId) +
      '/rules?applicantType=' + encodeURIComponent(applicantType));
    if (!response.ok) throw new Error('공고 자격기준을 불러오지 못했습니다.');
    const ruleList = await response.json();
    if (!Array.isArray(ruleList) || ruleList.length === 0) {
      throw new Error('선택한 공고에 해당 계층의 구조화 규칙이 없습니다.');
    }
    const rule = ruleList.find(function (item) { return item.validationStatus === 'parsed'; }) || ruleList[0];
    applyRuleToForm(notice, rule);

    const articles = document.querySelectorAll('#happyHousingNoticeList article[data-notice-id]');
    articles.forEach(function (article) {
      article.style.outline = String(article.dataset.noticeId) === String(notice.noticeId)
        ? '2px solid currentColor' : '';
    });
  }

  function renderNoticeList(notices, applicantType) {
    const list = $('happyHousingNoticeList');
    const summary = $('happyHousingNoticeSummary');
    if (!list || !summary) return;

    const label = typeInfo[typeSelect.value] ? typeInfo[typeSelect.value].label : null;
    if (!Array.isArray(notices) || notices.length === 0) {
      summary.textContent = label
        ? label + ' 계층이 포함된 현재 수집 공고가 없습니다.'
        : '현재 수집된 행복주택 공고가 없습니다.';
      list.innerHTML = '<article><span>!</span><div><strong>확인할 공고가 없습니다.</strong><p>다음 크롤링 실행 후 다시 확인해 주세요.</p></div></article>';
      return;
    }

    summary.textContent = label
      ? label + ' 계층이 포함된 공고 ' + notices.length + '건입니다. 최종 자격은 각 공고 원문을 확인해야 합니다.'
      : '현재 수집된 행복주택 공고 ' + notices.length + '건입니다.';

    list.innerHTML = notices.map(function (notice, index) {
      const numberText = String(index + 1).padStart(2, '0');
      return '<article data-notice-id="' + escapeHtml(notice.noticeId) + '" style="cursor:pointer" title="이 공고 기준 적용">' +
        '<span>' + numberText + '</span>' +
        '<div><strong>' + escapeHtml(notice.title) + '</strong>' +
        '<p>' + escapeHtml(notice.region || '지역 미확인') +
        ' · 공고일 ' + formatDate(notice.noticeDate) +
        ' · 마감일 ' + formatDate(notice.closingDate) +
        ' · ' + escapeHtml(notice.status || '상태 미확인') + '</p>' +
        (applicantType ? '<small>수집 규칙 계층: ' + escapeHtml(applicantType) + ' · 클릭하면 이 공고 기준 적용</small>' : '') +
        '</div></article>';
    }).join('');

    if (applicantType) {
      list.querySelectorAll('article[data-notice-id]').forEach(function (article) {
        article.addEventListener('click', function () {
          const notice = notices.find(function (item) {
            return String(item.noticeId) === String(article.dataset.noticeId);
          });
          if (!notice) return;
          selectNotice(notice).catch(function (error) {
            const summary = $('happyHousingNoticeSummary');
            if (summary) summary.textContent = error.message;
          });
        });
      });

      // 입주자격완화 공고는 소득/자산 요건이 배제될 수 있으므로,
      // 기본 화면에는 일반 공고를 우선 적용한다. 완화 공고도 사용자가 직접 클릭해 확인할 수 있다.
      const defaultNotice = notices.find(function (notice) {
        const title = String(notice.title || '');
        return !title.includes('입주자격완화') && !title.includes('자격완화');
      }) || notices[0];
      selectNotice(defaultNotice).catch(function () {});
    }
  }

  function renderNoticeMatches(matches) {
    const list = $('happyHousingNoticeList');
    const summary = $('happyHousingNoticeSummary');
    if (!list || !summary) return;

    if (!Array.isArray(matches) || matches.length === 0) {
      summary.textContent = '입력한 계층과 연결된 현재 공고가 없습니다.';
      list.innerHTML = '<article><span>!</span><div><strong>매칭 공고가 없습니다.</strong><p>다음 크롤링 결과를 다시 확인해 주세요.</p></div></article>';
      return;
    }

    const priority = { CANDIDATE: 0, REVIEW: 1, NOT_ELIGIBLE: 2 };
    const sorted = matches.slice().sort(function (a, b) {
      const pa = priority[a.recommendationStatus] === undefined ? 1 : priority[a.recommendationStatus];
      const pb = priority[b.recommendationStatus] === undefined ? 1 : priority[b.recommendationStatus];
      if (pa !== pb) return pa - pb;
      return String(b.noticeDate || '').localeCompare(String(a.noticeDate || ''));
    });

    const counts = sorted.reduce(function (acc, item) {
      const key = item.recommendationStatus || 'REVIEW';
      acc[key] = (acc[key] || 0) + 1;
      return acc;
    }, {});

    summary.textContent =
      '실제 공고 기준 사전매칭: 추천 가능 ' + (counts.CANDIDATE || 0) +
      '건 · 추가 확인 ' + (counts.REVIEW || 0) +
      '건 · 조건 불충족 ' + (counts.NOT_ELIGIBLE || 0) +
      '건입니다. 추천 가능 순으로 정렬했습니다.';

    function badgeInfo(state) {
      if (state === 'CANDIDATE') return { cls: 'hh-match-candidate', icon: '✓', text: '추천 가능' };
      if (state === 'NOT_ELIGIBLE') return { cls: 'hh-match-fail', icon: '×', text: '조건 불충족' };
      return { cls: 'hh-match-review', icon: '?', text: '추가 확인 필요' };
    }

    list.innerHTML = sorted.map(function (item, index) {
      const numberText = String(index + 1).padStart(2, '0');
      const state = item.recommendationStatus || 'REVIEW';
      const badge = badgeInfo(state);
      const detailChecks = Array.isArray(item.checks) ? item.checks : [];
      const failChecks = detailChecks.filter(function (check) { return check.state === 'FAIL'; });
      const reviewChecks = detailChecks.filter(function (check) { return check.state !== 'PASS' && check.state !== 'FAIL'; });
      const reasons = (failChecks.length ? failChecks : reviewChecks)
        .slice(0, 3)
        .map(function (check) { return check.message; });

      let reasonText = '';
      if (reasons.length) reasonText = reasons.join(' / ');
      else if (state === 'CANDIDATE') reasonText = '현재 입력값으로 자동 비교 가능한 구조화 조건을 통과했습니다.';
      else reasonText = '공고 원문의 세부조건을 추가로 확인해 주세요.';

      const selectedClass = String(item.noticeId) === String(selectedNoticeId) ? ' hh-selected-notice' : '';
      return '<article class="' + selectedClass.trim() + '" data-notice-id="' + escapeHtml(item.noticeId) + '" style="cursor:pointer" title="이 공고 기준을 입력란에 적용">' +
        '<span>' + numberText + '</span>' +
        '<div><strong>' + escapeHtml(item.title) + '</strong>' +
        '<div class="hh-match-badge ' + badge.cls + '">' + badge.icon + ' ' + badge.text + '</div>' +
        '<p>' + escapeHtml(item.region || '지역 미확인') +
        ' · 공고일 ' + formatDate(item.noticeDate) +
        ' · 마감일 ' + formatDate(item.closingDate) +
        ' · ' + escapeHtml(item.noticeStatus || '상태 미확인') + '</p>' +
        '<small class="hh-match-reason"><b>판정 이유:</b> ' + escapeHtml(reasonText) + '</small>' +
        '<div class="hh-match-counts">' +
        '<span class="hh-match-count">통과 ' + escapeHtml(item.passedCount) + '</span>' +
        '<span class="hh-match-count">확인 ' + escapeHtml(item.checkCount) + '</span>' +
        '<span class="hh-match-count">미충족 ' + escapeHtml(item.failedCount) + '</span>' +
        '</div></div></article>';
    }).join('');

    list.querySelectorAll('article[data-notice-id]').forEach(function (article) {
      article.addEventListener('click', async function () {
        const match = sorted.find(function (item) {
          return String(item.noticeId) === String(article.dataset.noticeId);
        });
        if (!match) return;
        try {
          const noticeResponse = await fetch('/api/happy-housing/notices/' + encodeURIComponent(match.noticeId));
          if (!noticeResponse.ok) throw new Error('공고 상세를 불러오지 못했습니다.');
          const detail = await noticeResponse.json();
          const notice = detail.notice || detail;
          await selectNotice(notice);
          renderSelectedNoticeDiagnosis(match);
          list.querySelectorAll('article[data-notice-id]').forEach(function (card) {
            card.classList.toggle('hh-selected-notice', String(card.dataset.noticeId) === String(match.noticeId));
          });
        } catch (error) {
          summary.textContent = '공고 적용 중 오류: ' + error.message;
        }
      });
    });
  }

  async function loadNoticeMatches(payload) {
    const list = $('happyHousingNoticeList');
    const summary = $('happyHousingNoticeSummary');
    if (!list || !summary) return;

    summary.textContent = '입력값과 실제 크롤링 공고의 구조화 규칙을 비교하고 있습니다.';
    list.innerHTML = '<article><span>…</span><div><strong>공고별 매칭 중</strong><p>연령·무주택·계층·자산·자동차 등 자동 비교 가능한 조건을 확인하고 있습니다.</p></div></article>';

    const response = await fetch('/api/happy-housing/matches', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      let message = '공고별 매칭 요청을 처리하지 못했습니다.';
      try {
        const errorBody = await response.json();
        if (errorBody && errorBody.message) message = errorBody.message;
      } catch (ignore) {}
      throw new Error(message);
    }

    const matches = await response.json();
    renderNoticeMatches(matches);
    return matches;
  }

  async function loadCrawlStatus() {
    const box = $('happyHousingCrawlStatus');
    if (!box) return;
    try {
      const response = await fetch('/api/happy-housing/crawl-history/latest');
      if (!response.ok) throw new Error('crawl history');
      const data = await response.json();
      box.hidden = false;
      box.innerHTML = '<i class="fa-solid fa-database"></i><span>최근 수집: ' +
        escapeHtml(data.finishedAt || data.startedAt || '-') +
        ' · 공고 ' + escapeHtml(data.noticeCount) + '건' +
        ' · 규칙 ' + escapeHtml(data.ruleCount) + '건' +
        ' · 상태 ' + escapeHtml(data.status || '-') + '</span>';
    } catch (ignore) {
      box.hidden = true;
    }
  }

  async function loadCurrentNotices() {
    const list = $('happyHousingNoticeList');
    const summary = $('happyHousingNoticeSummary');
    if (!list || !summary) return;

    const applicantType = noticeTypeForForm();
    summary.textContent = applicantType
      ? '선택한 계층이 포함된 실제 공고를 찾고 있습니다.'
      : 'LH에서 수집한 최신 공고를 불러오고 있습니다.';
    list.innerHTML = '<article><span>…</span><div><strong>공고 조회 중</strong><p>MySQL에 저장된 크롤링 공고를 확인하고 있습니다.</p></div></article>';

    try {
      const params = new URLSearchParams();
      if (applicantType) params.set('applicantType', applicantType);
      const keyword = String(($('happyHousingKeyword') || {}).value || '').trim();
      const region = String(($('happyHousingRegion') || {}).value || '').trim();
      const status = String(($('happyHousingStatus') || {}).value || '').trim();
      if (keyword) params.set('query', keyword);
      if (region) params.set('region', region);
      if (status) params.set('status', status);
      const url = '/api/happy-housing/notices' + (params.toString() ? '?' + params.toString() : '');
      const response = await fetch(url);
      if (!response.ok) throw new Error('공고 조회 실패');
      const notices = await response.json();
      renderNoticeList(notices, applicantType);
    } catch (error) {
      summary.textContent = '현재 공고를 불러오지 못했습니다.';
      list.innerHTML = '<article><span>!</span><div><strong>공고 API 연결을 확인해 주세요.</strong><p>' + escapeHtml(error.message) + '</p></div></article>';
    }
  }

  function radio(name) { const el = form.querySelector('input[name="' + name + '"]:checked'); return el ? el.value : 'unknown'; }
  function number(id) { const value = $(id).value; return value === '' ? null : Number(value); }
  function format(value) { return Number(value).toLocaleString('ko-KR') + '만원'; }
  function compare(actualId, limitId) { const actual = number(actualId); const limit = number(limitId); if (actual === null || limit === null) return { state: 'unknown' }; return { state: actual <= limit ? 'yes' : 'no', actual: actual, limit: limit }; }
  function ageOnDate() { const birth = $('birthDate').value; const notice = $('noticeDate').value; if (!birth || !notice) return null; const b = new Date(birth + 'T00:00:00'); const n = new Date(notice + 'T00:00:00'); if (b > n) return null; let age = n.getFullYear() - b.getFullYear(); if (n.getMonth() < b.getMonth() || (n.getMonth() === b.getMonth() && n.getDate() < b.getDate())) age -= 1; return age; }

  function updateAge() { const age = ageOnDate(); $('ageOutput').textContent = age === null ? '' : '모집공고일 기준 만 ' + age + '세입니다.' + (typeSelect.value === 'youth' ? (age >= 19 && age <= 39 ? ' 일반적인 청년 연령 범위(만 19~39세)에 해당합니다.' : ' 일반적인 청년 연령 범위 밖이므로 공고의 다른 청년 인정요건을 확인하세요.') : ''); }
  function updateCompare() {
    const income = compare('monthlyIncome', 'incomeLimit');
    $('incomeCompare').className = 'compare-output ' + (income.state === 'yes' ? 'compare-pass' : income.state === 'no' ? 'compare-fail' : '');
    $('incomeCompare').textContent = income.state === 'unknown' ? '두 금액을 입력하면 자동으로 비교해 드려요.' : income.state === 'yes' ? '소득 상한보다 ' + format(income.limit - income.actual) + ' 낮습니다.' : '소득 상한을 ' + format(income.actual - income.limit) + ' 초과합니다.';
    const assets = compare('totalAssets', 'assetLimit'); const car = compare('carValue', 'carLimit');
    const states = [assets.state, car.state]; $('assetCompare').className = 'compare-output ' + (states.includes('no') ? 'compare-fail' : states.every(function (s) { return s === 'yes'; }) ? 'compare-pass' : '');
    $('assetCompare').textContent = states.includes('no') ? '입력한 금액 중 공고 상한을 초과하는 항목이 있습니다.' : states.every(function (s) { return s === 'yes'; }) ? '총자산과 자동차가액 모두 입력한 공고 상한 이내입니다.' : '보유액과 공고 상한을 입력하면 자동으로 비교해 드려요.';
  }
  function updateProgress() { const groups = [typeSelect.value, radio('homeless') !== 'unknown' ? 'x' : '', radio('category') !== 'unknown' ? 'x' : '', number('monthlyIncome') !== null && number('incomeLimit') !== null ? 'x' : '', number('totalAssets') !== null && number('assetLimit') !== null && number('carValue') !== null && number('carLimit') !== null ? 'x' : '', radio('connection') !== 'unknown' ? 'x' : '']; const percent = Math.round(groups.filter(Boolean).length / groups.length * 100); $('diagnosisProgressBar').style.width = percent + '%'; $('diagnosisProgressText').textContent = percent + '% 입력'; }

  typeSelect.addEventListener('change', function () { const info = typeInfo[this.value]; $('applicantTypeGuide').textContent = info ? info.guide : '계층을 선택하면 확인할 기본요건을 알려드려요.'; $('categoryQuestion').textContent = info ? info.question : '선택한 계층의 기본요건을 충족하나요?'; clearLiveRuleFields(); updateAge(); updateCompare(); updateProgress(); loadCurrentNotices(); });
  form.addEventListener('input', function () { updateAge(); updateCompare(); updateProgress(); });

  function row(label, state, detail) {
    const safeState = state === 'yes' || state === 'no' ? state : 'unknown';
    const icon = safeState === 'yes' ? 'fa-check' : safeState === 'no' ? 'fa-xmark' : 'fa-question';
    const text = safeState === 'yes' ? '통과' : safeState === 'no' ? '미충족' : '확인 필요';
    return '<li class="check-' + safeState + '"><i class="fa-solid ' + icon + '"></i><div><strong>' +
      escapeHtml(label) + '<em>' + text + '</em></strong><span>' + escapeHtml(detail) + '</span></div></li>';
  }
  function renderSelectedNoticeDiagnosis(match) {
    const state = match.recommendationStatus === 'CANDIDATE'
      ? 'pass' : match.recommendationStatus === 'NOT_ELIGIBLE' ? 'fail' : 'check';
    const labels = {
      AGE: '연령 계산', HOMELESS: '무주택 요건', CATEGORY: '계층 기본요건',
      INCOME: '월평균소득', ASSET: '총자산', CAR: '자동차가액',
      CONNECTION: '지역·직장 연계', RULE_VALIDATION: '공고 규칙 검증',
      SUBSCRIPTION: '청약저축', SPECIAL_RULE: '공고별 특수조건', RULE_JSON: '공고 규칙'
    };
    const checks = Array.isArray(match.checks) ? match.checks : [];
    result.className = 'diagnosis-result result-' + state;
    result.innerHTML =
      '<div class="result-status"><i class="fa-solid ' +
      (state === 'pass' ? 'fa-circle-check' : state === 'fail' ? 'fa-circle-xmark' : 'fa-magnifying-glass') +
      '"></i><small>선택한 실제 공고 기준</small><h3>' + escapeHtml(match.recommendationTitle) +
      '</h3><p>' + escapeHtml(match.title) + '</p><p>통과 ' + escapeHtml(match.passedCount) +
      ' · 확인 필요 ' + escapeHtml(match.checkCount) + ' · 미충족 ' + escapeHtml(match.failedCount) + '</p></div>' +
      '<ul class="detailed-checks">' + checks.map(function (check) {
        const uiState = check.state === 'PASS' ? 'yes' : check.state === 'FAIL' ? 'no' : 'unknown';
        return row(labels[check.type] || check.type, uiState, check.message);
      }).join('') + '</ul>' +
      '<a href="https://apply.lh.or.kr/lhapply/apply/wt/wrtanc/selectWrtancList.do?mi=1026" target="_blank" rel="noopener noreferrer">모집공고에서 최종 확인 <i class="fa-solid fa-arrow-up-right-from-square"></i></a>';
  }

  const noticeSearchForm = $('happyHousingNoticeSearch');
  if (noticeSearchForm) {
    noticeSearchForm.addEventListener('submit', function (event) {
      event.preventDefault();
      loadCurrentNotices();
    });
  }

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (!typeInfo[typeSelect.value]) { typeSelect.focus(); return; }

    const payload = {
      applicantType: typeSelect.value,
      birthDate: $('birthDate').value || null,
      noticeDate: $('noticeDate').value || null,
      homeless: radio('homeless'),
      category: radio('category'),
      monthlyIncome: number('monthlyIncome'),
      householdSize: number('householdSize'),
      totalAssets: number('totalAssets'),
      carValue: number('carValue'),
      connection: radio('connection')
    };

    result.className = 'diagnosis-result';
    result.innerHTML = '<div class="result-placeholder"><i class="fa-solid fa-spinner fa-spin"></i><h3>자격 기준을 확인하고 있어요</h3><p>등록된 Happy Housing 기준과 입력 내용을 비교하고 있습니다.</p></div>';

    try {
      const response = await fetch('/api/happy-housing/diagnose', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        let message = '진단 요청을 처리하지 못했습니다.';
        try {
          const errorBody = await response.json();
          if (errorBody && errorBody.message) message = errorBody.message;
        } catch (ignore) {}
        throw new Error(message);
      }

      const data = await response.json();
      const state = data.result === 'PASS' ? 'pass' : data.result === 'FAIL' ? 'fail' : 'check';
      const labels = {
        AGE: '연령 계산',
        HOMELESS: '무주택 요건',
        CATEGORY: '계층 기본요건',
        INCOME: '월평균소득',
        ASSET: '총자산',
        CAR: '자동차가액',
        CONNECTION: '지역·직장 연계'
      };
      const checks = Array.isArray(data.checks) ? data.checks : [];

      result.className = 'diagnosis-result result-' + state;
      result.innerHTML =
        '<div class="result-status"><i class="fa-solid ' +
        (state === 'pass' ? 'fa-circle-check' : state === 'fail' ? 'fa-circle-xmark' : 'fa-magnifying-glass') +
        '"></i><small>' + typeInfo[typeSelect.value].label + ' 간편진단</small><h3>' +
        escapeHtml(data.title) + '</h3><p>통과 ' + escapeHtml(data.passedCount) + ' · 확인 필요 ' +
        escapeHtml(data.unknownCount) + ' · 미충족 ' + escapeHtml(data.failedCount) + '</p></div>' +
        '<ul class="detailed-checks">' +
        checks.map(function (check) {
          return row(labels[check.type] || check.type, check.state, check.message);
        }).join('') +
        '</ul><a href="https://apply.lh.or.kr/lhapply/apply/wt/wrtanc/selectWrtancList.do?mi=1026" target="_blank" rel="noopener noreferrer">모집공고에서 최종 확인 <i class="fa-solid fa-arrow-up-right-from-square"></i></a>';

      try {
        const matches = await loadNoticeMatches(payload);
        if (selectedNoticeId && Array.isArray(matches)) {
          const selectedMatch = matches.find(function (item) {
            return String(item.noticeId) === String(selectedNoticeId);
          });
          if (selectedMatch) renderSelectedNoticeDiagnosis(selectedMatch);
        }
      } catch (matchError) {
        await loadCurrentNotices();
        const summary = $('happyHousingNoticeSummary');
        if (summary) summary.textContent += ' 공고별 자동 매칭은 실패하여 계층별 공고만 표시합니다.';
      }
      result.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } catch (error) {
      result.className = 'diagnosis-result result-check';
      result.innerHTML = '<div class="result-status"><i class="fa-solid fa-triangle-exclamation"></i><small>Happy Housing 간편진단</small><h3>진단을 완료하지 못했어요</h3><p>' + escapeHtml(error.message) + '</p></div>';
      result.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  });

  form.addEventListener('reset', function () { setTimeout(function () { $('applicantTypeGuide').textContent = '계층을 선택하면 확인할 기본요건을 알려드려요.'; $('categoryQuestion').textContent = '선택한 계층의 기본요건을 충족하나요?'; $('ageOutput').textContent = ''; clearLiveRuleFields(); updateCompare(); updateProgress(); loadCurrentNotices(); result.className = 'diagnosis-result'; result.innerHTML = '<div class="result-placeholder"><i class="fa-solid fa-clipboard-check"></i><h3>항목을 선택해 주세요</h3><p>입력을 마치면 신청 가능성 및 추가로 확인할 내용을 알려드려요.</p></div>'; }, 0); });
  if ($('householdSize')) {
    $('householdSize').addEventListener('input', applyIncomeLimitFromSelectedRule);
    $('householdSize').addEventListener('change', applyIncomeLimitFromSelectedRule);
  }
  updateProgress();
  loadCurrentNotices();
  loadCrawlStatus();
})();
