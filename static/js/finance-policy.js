(function () {
  'use strict';

  const list = document.getElementById('financePolicyList');
  const category = document.getElementById('financeCategory');
  const target = document.getElementById('financeTarget');
  if (!list || !category || !target) return;

  const query = new URLSearchParams(window.location.search);
  const requestedCategory = query.get('category');
  const requestedTarget = query.get('targetType');
  if (requestedCategory && Array.from(category.options).some(function (option) { return option.value === requestedCategory; })) {
    category.value = requestedCategory;
  }
  if (requestedTarget && Array.from(target.options).some(function (option) { return option.value === requestedTarget; })) {
    target.value = requestedTarget;
  }

  const categoryLabels = {
    purchase: '주택 구입',
    jeonse: '전세자금',
    monthly: '월세지원'
  };
  const targetLabels = {
    general: '일반',
    youth: '청년',
    newlywed: '신혼부부'
  };
  const categoryIcons = {
    purchase: 'fa-house-circle-check',
    jeonse: 'fa-vault',
    monthly: 'fa-receipt'
  };

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function render(policies) {
    if (!policies.length) {
      list.innerHTML = '<div class="finance-policy-empty panel"><i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i><strong>조건에 맞는 정책이 없어요</strong><p>지원 유형이나 신청 대상을 변경해 보세요.</p></div>';
      return;
    }
    list.innerHTML = policies.map(function (policy) {
      const icon = categoryIcons[policy.category] || 'fa-landmark';
      const categoryLabel = categoryLabels[policy.category] || policy.category || '주거지원';
      const targetLabel = targetLabels[policy.targetType] || policy.targetType || '전체';
      const rate = policy.rateInfo || '공식 공고 확인';
      const limit = policy.limitInfo || '공식 공고 확인';
      const description = policy.description || '세부 신청 조건은 공식 기관에서 확인하세요.';
      const checkedAt = policy.sourceCheckedAt ? new Date(policy.sourceCheckedAt).toLocaleDateString('ko-KR') : '';
      const sourceLink = policy.sourceUrl
        ? '<a class="finance-policy-source" href="' + escapeHtml(policy.sourceUrl) + '" target="_blank" rel="noopener noreferrer">' + escapeHtml(policy.sourceName || '공식 출처') + ' 원문 확인</a>'
        : '<span class="finance-policy-source is-muted">공식 출처 등록 전</span>';
      const review = policy.updateStatus === 'review' ? '<span class="finance-policy-review">변경 검토 중</span>' : '';
      return '<article class="finance-policy-card panel">' +
        '<div class="finance-policy-icon"><i class="fa-solid ' + icon + '" aria-hidden="true"></i></div>' +
        '<div><p class="eyebrow">' + escapeHtml(categoryLabel) + ' · ' + escapeHtml(targetLabel) + '</p>' +
        '<h3>' + escapeHtml(policy.name) + '</h3><p>' + escapeHtml(description) + '</p>' +
        '<ul><li><strong>금리</strong> ' + escapeHtml(rate) + '</li><li><strong>최대한도</strong> ' + escapeHtml(limit) + '</li></ul>' +
        '<div class="finance-policy-source-row">' + sourceLink + (checkedAt ? '<small>최종 확인 ' + escapeHtml(checkedAt) + '</small>' : '') + review + '</div></div></article>';
    }).join('');
  }

  async function loadPolicies() {
    const params = new URLSearchParams();
    if (category.value) params.set('category', category.value);
    if (target.value) params.set('targetType', target.value);
    list.setAttribute('aria-busy', 'true');
    try {
      const response = await fetch('/api/finance/policies?' + params.toString(), { credentials: 'same-origin' });
      if (!response.ok) throw new Error('정책을 불러오지 못했습니다.');
      const data = await response.json();
      render(Array.isArray(data) ? data : (data.items || []));
    } catch (error) {
      list.innerHTML = '<div class="finance-policy-empty is-error panel"><i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i><strong>정책 정보를 불러오지 못했어요</strong><p>잠시 후 다시 시도해 주세요.</p></div>';
    } finally {
      list.removeAttribute('aria-busy');
    }
  }

  category.addEventListener('change', loadPolicies);
  target.addEventListener('change', loadPolicies);
  loadPolicies();
})();
