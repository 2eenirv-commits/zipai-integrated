(function () {
  'use strict';
  const form = document.getElementById('calculatorForm');
  if (!form) return;

  const fields = ['salePrice', 'deposit', 'seniorDebt'];
  function numberValue(input) {
    return Number(String(input.value).replace(/[^0-9]/g, '')) || 0;
  }

  function formatNumber(value) {
    return Number(value).toLocaleString('ko-KR');
  }

  function amountToKorean(value) {
    if (!value) return '금액을 입력해 주세요';
    const eok = Math.floor(value / 10000);
    const man = value % 10000;
    if (eok && man) return eok.toLocaleString() + '억 ' + man.toLocaleString() + '만원';
    if (eok) return eok.toLocaleString() + '억원';
    return man.toLocaleString() + '만원';
  }

  fields.forEach(function (id) {
    const input = document.getElementById(id);
    input.addEventListener('input', function () {
      const cursorAtEnd = this.selectionStart === this.value.length;
      const value = numberValue(this);
      this.value = value ? formatNumber(value) : '';
      if (cursorAtEnd) this.setSelectionRange(this.value.length, this.value.length);
      const helper = document.querySelector('[data-for="' + id + '"]');
      if (id === 'seniorDebt' && !value) helper.textContent = '없으면 0원으로 계산해요';
      else helper.textContent = amountToKorean(value);
    });
  });

  function calculate() {
    const sale = numberValue(document.getElementById('salePrice'));
    const deposit = numberValue(document.getElementById('deposit'));
    const senior = numberValue(document.getElementById('seniorDebt'));
    if (!sale || !deposit) {
      showToast('매매 시세와 전세 보증금을 모두 입력해 주세요.');
      (!sale ? document.getElementById('salePrice') : document.getElementById('deposit')).focus();
      return;
    }
    if (deposit > sale * 2) {
      showToast('입력한 금액의 단위를 다시 확인해 주세요.');
    }
    const ratio = (deposit + senior) / sale * 100;
    const rounded = Math.round(ratio * 10) / 10;
    const type = ratio < 70 ? 'safe' : ratio < 80 ? 'caution' : 'danger';
    const messages = {
      safe: ['비교적 안전한 수준이에요', '일반적인 기준에서는 보증금 회수 위험이 낮은 편이에요.'],
      caution: ['조금 더 확인이 필요해요', '선순위 권리와 보증보험 가입 가능 여부를 꼭 확인하세요.'],
      danger: ['깡통전세 위험이 높아요', '집값이 하락하거나 경매가 진행되면 보증금을 돌려받지 못할 수 있어요.']
    };
    document.getElementById('emptyCalculator').hidden = true;
    document.getElementById('calculatedContent').hidden = false;
    document.getElementById('ratioResult').classList.remove('empty');
    document.getElementById('ratioNumber').textContent = rounded.toFixed(1).replace('.0', '');
    const badge = document.getElementById('ratioBadge');
    badge.className = 'status-badge ' + type;
    badge.textContent = type === 'safe' ? '안전 구간' : type === 'caution' ? '주의 구간' : '위험 구간';
    document.getElementById('ratioTitle').textContent = messages[type][0];
    document.getElementById('ratioDescription').textContent = messages[type][1];
    document.getElementById('salePriceResult').textContent = formatNumber(sale) + '만원';
    document.getElementById('totalDebtResult').textContent = formatNumber(deposit + senior) + '만원';
    document.getElementById('scaleMarker').style.left = Math.min(100, Math.max(0, ratio / 120 * 100)) + '%';
    form.dataset.result = JSON.stringify({ salePrice: sale, deposit: deposit, seniorDebt: senior, ratio: rounded, houseType: document.getElementById('houseType').value, savedAt: new Date().toISOString() });
    if (window.innerWidth < 760) document.getElementById('ratioResult').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  form.addEventListener('submit', function (event) { event.preventDefault(); calculate(); });
  form.addEventListener('reset', function () {
    setTimeout(function () {
      fields.forEach(function (id) { document.querySelector('[data-for="' + id + '"]').textContent = id === 'seniorDebt' ? '없으면 0원으로 계산해요' : '금액을 입력해 주세요'; });
      document.getElementById('emptyCalculator').hidden = false;
      document.getElementById('calculatedContent').hidden = true;
      delete form.dataset.result;
    }, 0);
  });
  document.getElementById('saveRatio').addEventListener('click', function () {
    if (!form.dataset.result) return;
    safeStorage.set('zipdosoRatioResult', JSON.parse(form.dataset.result));
    showToast('전세가율 결과를 안전 진단에 저장했어요.');
    this.textContent = '✓ 안전 진단에 저장됨';
  });

  const previous = safeStorage.get('zipdosoRatioResult', null);
  if (previous) {
    document.getElementById('salePrice').value = formatNumber(previous.salePrice);
    document.getElementById('deposit').value = formatNumber(previous.deposit);
    document.getElementById('seniorDebt').value = previous.seniorDebt ? formatNumber(previous.seniorDebt) : '';
    document.getElementById('houseType').value = previous.houseType || 'apartment';
    fields.forEach(function (id) {
      const input = document.getElementById(id);
      const event = new Event('input', { bubbles: true }); input.dispatchEvent(event);
    });
    calculate();
    document.getElementById('saveRatio').textContent = '✓ 안전 진단에 저장됨';
  }
})();
