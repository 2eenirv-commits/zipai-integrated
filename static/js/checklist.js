(function () {
  'use strict';

  const questions = [
    { step: 0, title: '등기부등본을 직접 발급해 확인했나요?', help: '인터넷등기소 또는 등기소에서 계약 직전 발급본을 확인하세요.', action: '최신 등기부등본을 발급해 소유권과 근저당을 확인하세요.', link: '/defense/guide', options: [['확인했어요','safe'],['아직이에요','danger'],['잘 모르겠어요','caution']] },
    { step: 0, title: '계약 상대방이 등기부상 소유자와 일치하나요?', help: '대리인이라면 위임장과 인감증명서가 필요해요.', action: '계약 상대방의 신분과 적법한 대리권을 다시 확인하세요.', link: '/defense/guide', options: [['일치해요','safe'],['일치하지 않아요','danger'],['확인 전이에요','caution']] },
    { step: 0, title: '주변 매매 시세와 전세가율을 확인했나요?', help: '같은 면적의 최근 실거래가를 2곳 이상에서 비교하세요.', action: '실거래가를 바탕으로 전세가율을 계산해 보세요.', link: '/defense/calculator', options: [['확인했어요','safe'],['확인 안 했어요','danger'],['시세가 불분명해요','caution']] },
    { step: 0, title: '임대인의 국세·지방세 체납 여부를 확인했나요?', help: '체납 세금은 보증금보다 먼저 변제될 수 있어요.', action: '임대인에게 국세·지방세 납세증명서를 요청하세요.', link: '/defense/guide', options: [['확인했어요','safe'],['체납이 있어요','danger'],['확인 전이에요','caution']] },
    { step: 1, title: '계약 당일 등기부등본을 다시 확인했나요?', help: '그 사이 새 근저당이나 압류가 생겼는지 살펴보세요.', action: '계약서 작성 직전 등기부등본을 한 번 더 발급하세요.', link: '/defense/guide', options: [['확인했어요','safe'],['변동이 있어요','danger'],['확인 전이에요','caution']] },
    { step: 1, title: '전세대출·보증보험 불가 시 반환 특약이 있나요?', help: '목적물 문제로 가입이 거절되면 계약금을 돌려받는 내용이에요.', action: '대출·보증보험 가입 불가 시 계약금 반환 특약을 넣으세요.', link: '/defense/guide', options: [['있어요','safe'],['없어요','danger'],['협의 중이에요','caution']] },
    { step: 1, title: '잔금 다음 날까지 권리관계 유지 특약이 있나요?', help: '임대인이 잔금 직후 근저당을 설정하는 위험을 줄여요.', action: '잔금일 다음 날까지 권리관계를 유지하는 특약을 넣으세요.', link: '/defense/guide', options: [['있어요','safe'],['없어요','danger'],['잘 모르겠어요','caution']] },
    { step: 1, title: '중개업소 등록과 공제증서를 확인했나요?', help: '등록된 공인중개사인지, 보증 한도는 얼마인지 확인하세요.', action: '중개사무소 등록증과 공제증서를 확인하세요.', link: '/defense/guide', options: [['확인했어요','safe'],['미등록이에요','danger'],['확인 전이에요','caution']] },
    { step: 2, title: '잔금 직전 등기부등본에 변동이 없나요?', help: '잔금을 보내기 전 마지막으로 권리변동을 확인하세요.', action: '잔금 송금 전 등기부등본 변동 여부를 확인하세요.', link: '/defense/guide', options: [['변동 없어요','safe'],['변동이 있어요','danger'],['확인 전이에요','caution']] },
    { step: 2, title: '보증금을 소유자 명의 계좌로 송금하나요?', help: '제3자 계좌는 정당한 사유와 증빙을 꼭 확인해야 해요.', action: '등기부상 소유자 명의 계좌인지 확인한 뒤 송금하세요.', link: '/defense/guide', options: [['소유자 계좌예요','safe'],['제3자 계좌예요','danger'],['아직 몰라요','caution']] },
    { step: 2, title: '입주 당일 전입신고와 확정일자가 가능한가요?', help: '대항력과 우선변제권을 갖추기 위한 핵심 절차예요.', action: '입주 즉시 전입신고를 하고 확정일자를 받으세요.', link: '/defense/guide', options: [['가능해요','safe'],['불가능해요','danger'],['확인 전이에요','caution']] },
    { step: 2, title: '전세보증금 반환보증 가입 가능 여부를 확인했나요?', help: '가입 기관마다 주택 가격과 보증금 요건이 달라요.', action: '계약 초기에 반환보증 가입 가능 여부를 확인하세요.', link: '/defense/guide', options: [['가입 가능해요','safe'],['가입 불가해요','danger'],['확인 전이에요','caution']] }
  ];

  const form = document.getElementById('checklistForm');
  if (!form) return;

  let currentStep = 0;
  let answers = safeStorage.get('zipdosoChecklistDraft', {});
  const previousResult = safeStorage.get('zipdosoChecklistResult', null);
  if (!Object.keys(answers).length && previousResult && previousResult.answers) answers = previousResult.answers;

  function createQuestions() {
    [0, 1, 2].forEach(function (step) {
      const container = document.getElementById('questionsStep' + step);
      questions.forEach(function (question, index) {
        if (question.step !== step) return;
        const card = document.createElement('article');
        card.className = 'question-card' + (answers[index] ? ' answered' : '');
        card.dataset.question = index;
        const options = question.options.map(function (option) {
          const checked = answers[index] === option[1] ? ' checked' : '';
          return '<label><input type="radio" name="question' + index + '" value="' + option[1] + '"' + checked + '><span>' + option[0] + '</span></label>';
        }).join('');
        card.innerHTML = '<div class="question-card-header"><span class="question-number">Q' + String(index + 1).padStart(2, '0') + '</span><div><h3>' + question.title + '</h3><p>' + question.help + '</p></div></div><div class="answer-options">' + options + '</div>';
        container.appendChild(card);
      });
    });
  }

  function updateProgress() {
    const count = Object.keys(answers).length;
    const percent = Math.round(count / questions.length * 100);
    document.getElementById('progressLabel').textContent = '진행률 ' + percent + '%';
    document.getElementById('progressCount').textContent = count + ' / ' + questions.length + ' 완료';
    document.getElementById('progressBar').style.width = percent + '%';
  }

  function showStep(step) {
    currentStep = Math.max(0, Math.min(2, step));
    document.querySelectorAll('[data-step-panel]').forEach(function (panel, index) { panel.classList.toggle('active', index === currentStep); });
    document.querySelectorAll('.step-tabs button').forEach(function (button, index) { button.classList.toggle('active', index === currentStep); });
    document.getElementById('prevStep').disabled = currentStep === 0;
    document.getElementById('nextStep').classList.toggle('hidden', currentStep === 2);
    document.getElementById('submitChecklist').classList.toggle('hidden', currentStep !== 2);
    if (window.innerWidth < 760) document.querySelector('.sticky-progress').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function saveAndGo() {
    const counts = { safe: 0, caution: 0, danger: 0, unanswered: 0 };
    questions.forEach(function (_, index) {
      const answer = answers[index];
      if (answer) counts[answer] += 1;
      else counts.unanswered += 1;
    });
    const score = Math.round(((counts.safe * 1) + (counts.caution * .5) + (counts.unanswered * .25)) / questions.length * 100);
    const risks = questions.map(function (question, index) {
      const level = answers[index] || 'caution';
      if (level !== 'danger' && level !== 'caution') return null;
      return { title: question.title, action: question.action, link: question.link, level: level, unanswered: !answers[index] };
    }).filter(Boolean).sort(function (a, b) { return a.level === b.level ? 0 : a.level === 'danger' ? -1 : 1; });
    safeStorage.set('zipdosoChecklistResult', { answers: answers, counts: counts, score: score, risks: risks, savedAt: new Date().toISOString() });
    safeStorage.set('zipdosoChecklistDraft', answers);
    window.location.href = '/defense/result';
  }

  createQuestions();
  updateProgress();

  form.addEventListener('change', function (event) {
    if (!event.target.matches('input[type="radio"]')) return;
    const index = event.target.name.replace('question', '');
    answers[index] = event.target.value;
    event.target.closest('.question-card').classList.add('answered');
    safeStorage.set('zipdosoChecklistDraft', answers);
    updateProgress();
  });

  document.querySelectorAll('.step-tabs button').forEach(function (button) { button.addEventListener('click', function () { showStep(Number(this.dataset.step)); }); });
  document.getElementById('prevStep').addEventListener('click', function () { showStep(currentStep - 1); });
  document.getElementById('nextStep').addEventListener('click', function () { showStep(currentStep + 1); });
  document.getElementById('resetChecklist').addEventListener('click', function () {
    if (!confirm('작성한 답변을 모두 지울까요?')) return;
    answers = {};
    safeStorage.remove('zipdosoChecklistDraft');
    safeStorage.remove('zipdosoChecklistResult');
    form.reset();
    document.querySelectorAll('.question-card').forEach(function (card) { card.classList.remove('answered'); });
    updateProgress(); showStep(0); showToast('체크리스트를 초기화했어요.');
  });

  const modal = document.getElementById('incompleteModal');
  function closeModal() { modal.classList.remove('open'); modal.setAttribute('aria-hidden', 'true'); }
  form.addEventListener('submit', function (event) {
    event.preventDefault();
    const missing = questions.length - Object.keys(answers).length;
    if (missing > 0) {
      document.getElementById('missingCount').textContent = missing + '개';
      modal.classList.add('open'); modal.setAttribute('aria-hidden', 'false');
    } else saveAndGo();
  });
  modal.querySelectorAll('.modal-close, .modal-close-button').forEach(function (button) { button.addEventListener('click', closeModal); });
  modal.addEventListener('click', function (event) { if (event.target === modal) closeModal(); });
  document.getElementById('continueResult').addEventListener('click', saveAndGo);
  document.addEventListener('keydown', function (event) { if (event.key === 'Escape') closeModal(); });
})();
