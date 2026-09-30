(function () {
  'use strict';
  const nav = document.getElementById('guideNav');
  if (!nav) return;
  let completed = safeStorage.get('zipdosoGuideProgress', []);
  if (!Array.isArray(completed)) completed = [];

  function showGuide(index) {
    document.querySelectorAll('[data-guide-panel]').forEach(function (panel) { panel.classList.toggle('active', Number(panel.dataset.guidePanel) === index); });
    nav.querySelectorAll('[data-guide]').forEach(function (button) { button.classList.toggle('active', Number(button.dataset.guide) === index); });
    if (window.innerWidth < 760) document.querySelector('[data-guide-panel="' + index + '"]').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function updateProgress() {
    const unique = Array.from(new Set(completed.map(Number))).filter(function (n) { return n >= 0 && n < 4; });
    completed = unique;
    document.getElementById('guideProgressText').textContent = completed.length + ' / 4';
    document.getElementById('guideProgressBar').style.width = (completed.length / 4 * 100) + '%';
    nav.querySelectorAll('[data-guide]').forEach(function (button) { button.classList.toggle('completed', completed.includes(Number(button.dataset.guide))); });
    document.querySelectorAll('.guide-done').forEach(function (button) {
      const done = completed.includes(Number(button.dataset.complete));
      button.classList.toggle('completed', done);
      button.textContent = done ? '✓ 확인 완료' : '이 단계 확인 완료';
    });
  }

  nav.addEventListener('click', function (event) {
    const button = event.target.closest('[data-guide]');
    if (button) showGuide(Number(button.dataset.guide));
  });
  document.querySelectorAll('.accordion > button').forEach(function (button) {
    button.addEventListener('click', function () {
      const item = this.closest('.accordion');
      const open = item.classList.toggle('open');
      this.setAttribute('aria-expanded', String(open));
    });
  });
  document.querySelectorAll('.guide-done').forEach(function (button) {
    button.addEventListener('click', function () {
      const index = Number(this.dataset.complete);
      if (!completed.includes(index)) completed.push(index);
      else completed = completed.filter(function (value) { return value !== index; });
      safeStorage.set('zipdosoGuideProgress', completed);
      updateProgress();
      if (completed.includes(index) && index < 3) setTimeout(function () { showGuide(index + 1); }, 350);
    });
  });

  function fallbackCopy(text) {
    const area = document.createElement('textarea');
    area.value = text; area.style.position = 'fixed'; area.style.opacity = '0';
    document.body.appendChild(area); area.select();
    try { document.execCommand('copy'); } catch (error) { /* no-op */ }
    area.remove();
  }
  document.querySelectorAll('.copy-button').forEach(function (button) {
    button.addEventListener('click', function () {
      const text = this.dataset.copy;
      if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(text).catch(function () { fallbackCopy(text); });
      else fallbackCopy(text);
      showToast('특약 문구를 복사했어요. 계약 상황에 맞게 수정하세요.');
      this.textContent = '복사 완료';
    });
  });
  updateProgress();
})();
