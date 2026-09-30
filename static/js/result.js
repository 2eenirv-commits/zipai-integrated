(function () {
  'use strict';
  let result = safeStorage.get('zipdosoChecklistResult', null);
  let ratio = safeStorage.get('zipdosoRatioResult', null);

  function renderDiagnosis(result, ratio) {
  let score = result.score;
  if (ratio && typeof ratio.ratio === 'number') {
    const ratioScore = ratio.ratio < 70 ? 100 : ratio.ratio < 80 ? 60 : 20;
    score = Math.round(result.score * .8 + ratioScore * .2);
  }

  const level = score >= 80 ? 'safe' : score >= 55 ? 'caution' : 'danger';
  const copy = {
    safe: ['비교적 안전해요', '현재 답변에서는 큰 위험 신호가 적어요', '계약 직전 등기부등본 변동 사항까지 다시 확인하면 더 안전해요.'],
    caution: ['주의가 필요해요', '확인이 필요한 항목이 남아 있어요', '주의 항목을 계약 전에 확인하고 관련 내용은 반드시 문서로 남기세요.'],
    danger: ['위험 신호가 있어요', '계약을 서두르지 마세요', '위험 항목을 해결하기 전에는 계약금이나 보증금을 송금하지 않는 것이 좋아요.']
  };
  const data = copy[level];
  const ring = document.getElementById('scoreRing');
  ring.className = 'score-ring ' + level;
  ring.style.setProperty('--score', score);
  document.getElementById('scoreValue').textContent = score;
  const badge = document.getElementById('statusBadge');
  badge.className = 'status-badge ' + level;
  badge.textContent = data[0];
  document.getElementById('resultTitle').textContent = data[1];
  document.getElementById('resultDescription').textContent = data[2];

  const answered = 12 - (result.counts.unanswered || 0);
  document.getElementById('answerCounter').textContent = answered + ' / 12';
  ['safe', 'caution', 'danger'].forEach(function (type) {
    const count = result.counts[type] || 0;
    document.getElementById(type + 'Count').textContent = count;
    setTimeout(function () { document.getElementById(type + 'Bar').style.width = (count / 12 * 100) + '%'; }, 100);
  });
  const savedDate = new Date(result.savedAt);
  if (!isNaN(savedDate.getTime())) {
    document.getElementById('savedAt').textContent = savedDate.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }) + ' 진단';
  }

  const riskList = document.getElementById('riskList');
  let risks = (result.risks || []).slice(0, 5);
  if (result.serverStored && Number(result.counts.danger || 0) > 0) {
    risks.push({
      title: '위험 응답이 ' + Number(result.counts.danger) + '개 있어요',
      action: '체크리스트를 다시 열어 위험으로 표시된 계약 조건을 재확인하세요.',
      link: '/defense/checklist', level: 'danger'
    });
  }
  if (result.serverStored && Number(result.counts.caution || 0) > 0) {
    risks.push({
      title: '주의 응답이 ' + Number(result.counts.caution) + '개 있어요',
      action: '확인하지 못한 서류와 계약 조건을 계약 전에 보완하세요.',
      link: '/defense/checklist', level: 'caution'
    });
  }
  if (ratio && ratio.ratio >= 70) {
    risks.unshift({ title: '전세가율이 ' + ratio.ratio.toFixed(1) + '%예요', action: ratio.ratio >= 80 ? '깡통전세 위험이 높습니다. 보증금 조정이나 다른 매물을 검토하세요.' : '선순위 채권과 보증보험 가입 가능 여부를 추가로 확인하세요.', link: '/defense/calculator', level: ratio.ratio >= 80 ? 'danger' : 'caution' });
  }
  if (!risks.length) {
    riskList.innerHTML = '<div class="empty-state"><div class="empty-icon">✓</div><strong>현재 발견된 주의 항목이 없어요</strong><p>계약 당일에도 권리관계가 바뀌지 않았는지 다시 확인하세요.</p></div>';
  } else {
    riskList.innerHTML = risks.map(function (risk) {
      const label = risk.level === 'danger' ? '!' : '?';
      const title = risk.unanswered ? '[미응답] ' + risk.title : risk.title;
      const link = window.ZipaiAuth && window.ZipaiAuth.resolvePage ? window.ZipaiAuth.resolvePage(risk.link) : risk.link;
      return '<article class="risk-item ' + risk.level + '"><span class="risk-level">' + label + '</span><div><h3>' + title + '</h3><p>' + risk.action + '</p></div><a href="' + link + '">확인 방법 →</a></article>';
    }).join('');
  }
  }

  async function saveMemberDiagnosis(result, ratio) {
    if (!window.ZipaiAuth) return;
    await window.ZipaiAuth.ready;
    if (!window.ZipaiAuth.getUser()) return;
    const counts = result.counts || {};
    const payload = {
      clientRef: result.savedAt,
      safeCount: Number(counts.safe || 0),
      cautionCount: Number(counts.caution || 0),
      dangerCount: Number(counts.danger || 0),
      unansweredCount: Number(counts.unanswered || 0),
      jeonseRatio: ratio && typeof ratio.ratio === 'number' ? ratio.ratio : null
    };
    try {
      await fetch('/api/fraud-diagnoses', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
    } catch (error) {
      /* 로컬 진단 결과는 유지하고 회원 저장만 다음 조회 때 다시 시도한다. */
    }
  }

  async function loadMemberDiagnosis() {
    try {
      const response = await fetch('/api/fraud-diagnoses/latest', { credentials: 'same-origin' });
      if (!response.ok) return null;
      const payload = await response.json();
      if (!payload.available || !payload.diagnosis) return null;
      const item = payload.diagnosis;
      return {
        result: {
          counts: {
            safe: Number(item.safeCount || 0),
            caution: Number(item.cautionCount || 0),
            danger: Number(item.dangerCount || 0),
            unanswered: Number(item.unansweredCount || 0)
          },
          score: Number(item.checklistScore || 0),
          risks: [],
          savedAt: item.updatedAt,
          clientRef: item.clientRef,
          serverStored: true
        },
        ratio: item.jeonseRatio == null ? null : { ratio: Number(item.jeonseRatio) }
      };
    } catch (error) {
      return null;
    }
  }

  async function initializeDiagnosis() {
    const stored = await loadMemberDiagnosis();
    const localTime = result ? new Date(result.savedAt).getTime() : 0;
    const serverTime = stored ? new Date(stored.result.savedAt).getTime() : 0;
    const sameDiagnosis = Boolean(result && stored && stored.result.clientRef === result.savedAt);
    if (stored && (!result || (!sameDiagnosis && serverTime > localTime))) {
      result = stored.result;
      ratio = stored.ratio;
    }
    if (!result) return;
    renderDiagnosis(result, ratio);
    if (!result.serverStored) await saveMemberDiagnosis(result, ratio);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeDiagnosis, { once: true });
  } else {
    initializeDiagnosis();
  }
})();
