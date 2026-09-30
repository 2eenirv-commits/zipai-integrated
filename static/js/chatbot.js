(function () {
  'use strict';
  if (window.ZipaiChatbotLoaded) return;
  window.ZipaiChatbotLoaded = true;

  function createElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text != null) element.textContent = text;
    return element;
  }

  function safeInternalUrl(value) {
    if (!value) return '';
    try {
      const url = new URL(String(value), window.location.origin);
      if (!['http:', 'https:'].includes(url.protocol) || url.origin !== window.location.origin) return '';
      return url.pathname + url.search + url.hash;
    } catch (error) {
      return '';
    }
  }

  function addMessage(messages, type, text, actionLabel, actionUrl) {
    const wrapper = createElement('div', 'zipai-chatbot-message ' + type, text);
    const safeActionUrl = safeInternalUrl(actionUrl);
    if (type === 'bot' && actionLabel && safeActionUrl) {
      const action = createElement('a', 'zipai-chatbot-action', actionLabel + ' →');
      action.href = safeActionUrl;
      wrapper.appendChild(document.createElement('br'));
      wrapper.appendChild(action);
    }
    messages.appendChild(wrapper);
    messages.scrollTop = messages.scrollHeight;
  }

  function initChatbot() {
    if (document.querySelector('.zipai-chatbot')) return;

    const root = createElement('div', 'zipai-chatbot');
    const launcher = createElement('button', 'zipai-chatbot-launcher');
    launcher.type = 'button';
    launcher.setAttribute('aria-label', 'ZipAI 챗봇 열기');
    launcher.setAttribute('aria-expanded', 'false');
    launcher.innerHTML = '<i class="fa-solid fa-comments" aria-hidden="true"></i>';

    const panel = createElement('section', 'zipai-chatbot-panel');
    panel.hidden = true;
    panel.setAttribute('aria-label', 'ZipAI 챗봇');

    const header = createElement('header', 'zipai-chatbot-header');
    const headerCopy = createElement('div', 'zipai-chatbot-header-copy');
    headerCopy.appendChild(createElement('strong', '', 'ZipAI 챗봇'));
    headerCopy.appendChild(createElement('small', '', '1차 상담 · Happy Housing / Lifestyle'));

    const close = createElement('button', 'zipai-chatbot-close', '×');
    close.type = 'button';
    close.setAttribute('aria-label', '챗봇 닫기');
    header.appendChild(headerCopy);
    header.appendChild(close);

    const messages = createElement('div', 'zipai-chatbot-messages');
    messages.setAttribute('aria-live', 'polite');

    const quick = createElement('div', 'zipai-chatbot-quick');
    [
      '행복주택 자격진단 알려줘',
      '생활지역 추천 어디서 해?',
      '매물 찾기는 어떻게 해?'
    ].forEach(function (label) {
      const button = createElement('button', '', label);
      button.type = 'button';
      button.dataset.message = label;
      quick.appendChild(button);
    });

    const form = createElement('form', 'zipai-chatbot-form');
    const input = createElement('input', 'zipai-chatbot-input');
    input.type = 'text';
    input.placeholder = '궁금한 내용을 입력하세요';
    input.maxLength = 300;
    input.autocomplete = 'off';
    input.setAttribute('aria-label', '챗봇 질문');

    const send = createElement('button', 'zipai-chatbot-send', '전송');
    send.type = 'submit';
    form.appendChild(input);
    form.appendChild(send);

    panel.appendChild(header);
    panel.appendChild(messages);
    panel.appendChild(quick);
    panel.appendChild(form);
    root.appendChild(panel);
    root.appendChild(launcher);
    document.body.appendChild(root);

    addMessage(messages, 'bot',
      '안녕하세요. ZipAI 1차 챗봇입니다.\n행복주택 자격진단, Lifestyle 지역추천, 매물 찾기를 안내할 수 있어요.');

    function setOpen(open) {
      panel.hidden = !open;
      launcher.setAttribute('aria-expanded', String(open));
      launcher.setAttribute('aria-label', open ? 'ZipAI 챗봇 닫기' : 'ZipAI 챗봇 열기');
      if (open) input.focus();
    }

    launcher.addEventListener('click', function () { setOpen(panel.hidden); });
    close.addEventListener('click', function () { setOpen(false); });

    async function sendMessage(message) {
      const trimmed = String(message || '').trim();
      if (!trimmed) return;

      addMessage(messages, 'user', trimmed);
      input.value = '';
      input.disabled = true;
      send.disabled = true;

      try {
        const response = await fetch('/api/chat', {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json;charset=UTF-8' },
          body: JSON.stringify({ message: trimmed })
        });
        if (!response.ok) throw new Error('HTTP ' + response.status);

        const data = await response.json();
        addMessage(messages, 'bot',
          data.message || '응답을 준비하지 못했습니다.',
          data.actionLabel, data.actionUrl);
      } catch (error) {
        console.warn('ZipAI chatbot error:', error);
        addMessage(messages, 'bot',
          '현재 챗봇 서버와 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
      } finally {
        input.disabled = false;
        send.disabled = false;
        input.focus();
      }
    }

    form.addEventListener('submit', function (event) {
      event.preventDefault();
      sendMessage(input.value);
    });

    quick.addEventListener('click', function (event) {
      const button = event.target.closest('button[data-message]');
      if (!button) return;
      sendMessage(button.dataset.message);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initChatbot);
  } else {
    initChatbot();
  }
})();