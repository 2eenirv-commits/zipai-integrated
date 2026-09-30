(async function () {
  'use strict';

  const form = document.getElementById('inquiryForm');
  const list = document.getElementById('inquiryList');
  const empty = document.getElementById('inquiryEmpty');
  const count = document.getElementById('inquiryCount');
  const toast = document.getElementById('customerToast');
  const userMessage = document.getElementById('customerUserMessage');
  const memberGate = document.getElementById('inquiryMemberGate');
  const memberContent = document.getElementById('inquiryMemberContent');
  const statusLabels = { received: '접수', in_progress: '확인 중', answered: '답변 완료' };
  if (window.ZipaiAuth) await window.ZipaiAuth.ready;
  const user = window.ZipaiAuth && window.ZipaiAuth.getUser();

  if (!form || !list || !empty || !count || !toast) return;
  if (!user) {
    if (memberGate) memberGate.hidden = false;
    if (memberContent) memberContent.hidden = true;
    if (userMessage) userMessage.textContent = '1:1 문의 작성과 문의내역 확인은 로그인 회원만 이용할 수 있습니다.';
    return;
  }
  if (memberGate) memberGate.hidden = true;
  if (memberContent) memberContent.hidden = false;
  const emailInput = form.elements.email;
  if (emailInput && !emailInput.value) emailInput.value = user.email || '';

  function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    clearTimeout(window.__customerToast);
    window.__customerToast = setTimeout(function () { toast.classList.remove('is-visible'); }, 2400);
  }

  function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '날짜 정보 없음';
    return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(date);
  }

  async function api(path, options) {
    const response = await fetch(path, {
      credentials: 'same-origin',
      headers: options && options.body ? { 'Content-Type': 'application/json' } : {},
      ...options
    });
    const payload = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(payload.message || '요청을 처리하지 못했습니다.');
    return payload;
  }

  function renderHistory(items) {
    list.replaceChildren();
    count.textContent = items.length;
    empty.hidden = items.length > 0;
    list.hidden = items.length === 0;

    items.forEach(function (item) {
      const article = document.createElement('article');
      const top = document.createElement('div');
      const category = document.createElement('span');
      const status = document.createElement('span');
      const title = document.createElement('strong');
      const time = document.createElement('time');
      const answer = document.createElement('div');
      article.className = 'inquiry-item';
      top.className = 'inquiry-item-top';
      category.className = 'inquiry-item-category';
      status.className = 'inquiry-item-status';
      category.textContent = item.category;
      status.textContent = statusLabels[item.status] || item.status;
      title.textContent = item.title;
      time.dateTime = item.createdAt || '';
      time.textContent = formatDate(item.createdAt);
      answer.className = 'inquiry-item-answer';
      answer.textContent = item.answer ? '답변: ' + item.answer : '아직 등록된 답변이 없습니다.';
      answer.classList.toggle('is-pending', !item.answer);
      top.append(category, status);
      article.append(top, title, time, answer);
      list.appendChild(article);
    });
  }

  async function loadHistory() {
    try {
      const payload = await api('/api/inquiries');
      renderHistory(payload.items || []);
    } catch (error) {
      showToast(error.message);
    }
  }

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }
    const button = form.querySelector('[type="submit"]');
    const data = new FormData(form);
    if (button) button.disabled = true;
    try {
      await api('/api/inquiries', {
        method: 'POST',
        body: JSON.stringify({
          category: data.get('category'),
          title: String(data.get('title') || '').trim(),
          email: String(data.get('email') || '').trim(),
          message: String(data.get('message') || '').trim()
        })
      });
      form.reset();
      if (emailInput) emailInput.value = user.email || '';
      await loadHistory();
      showToast('문의가 접수되었습니다.');
    } catch (error) {
      showToast(error.message);
    } finally {
      if (button) button.disabled = false;
    }
  });

  if (userMessage) userMessage.textContent = user.id + '님, 무엇을 도와드릴까요?';
  await loadHistory();
})();
