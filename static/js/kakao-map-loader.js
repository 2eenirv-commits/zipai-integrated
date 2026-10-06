(function () {
  'use strict';

  const mapElement = document.getElementById('realMap')
    || document.querySelector('[data-safety-map] .safety-map-canvas');

  function showError(message) {
    if (mapElement) mapElement.innerHTML = '<div class="map-fallback">' + message + '</div>';
  }

  function notify(name, reason) {
    document.dispatchEvent(new CustomEvent(name, { detail: { reason: reason } }));
  }

  function loadHome() {
    notify('zipai:kakao-map-ready', 'ready');
    if (!document.getElementById('realMap') || document.querySelector('script[data-zipai-home]')) return;
    const script = document.createElement('script');
    script.src = 'static/js/home.js?v=20260922-3';
    script.dataset.zipaiHome = 'true';
    document.body.appendChild(script);
  }

  function fetchJavaScriptKey() {
    const configured = window.ZIPAI_CONFIG && window.ZIPAI_CONFIG.kakaoMapJavaScriptKey;
    if (configured) return Promise.resolve(String(configured).trim());

    return fetch('/api/config/maps', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    }).then(function (response) {
      if (!response.ok) throw new Error('config-load-failed');
      return response.json();
    }).then(function (config) {
      return String(config && config.kakaoMapJavaScriptKey || '').trim();
    });
  }

  function loadSdk(javaScriptKey) {
    if (!javaScriptKey) throw new Error('missing-key');

    return new Promise(function (resolve, reject) {
      function finishLoading() {
        if (!window.kakao || !window.kakao.maps || typeof window.kakao.maps.load !== 'function') {
          reject(new Error('sdk-unavailable'));
          return;
        }
        window.kakao.maps.load(resolve);
      }

      if (window.kakao && window.kakao.maps) {
        finishLoading();
        return;
      }

      const existing = document.querySelector('script[data-zipai-kakao-sdk]');
      if (existing) {
        existing.addEventListener('load', finishLoading, { once: true });
        existing.addEventListener('error', function () { reject(new Error('sdk-load-failed')); }, { once: true });
        return;
      }

      const sdk = document.createElement('script');
      sdk.src = '//dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=' + encodeURIComponent(javaScriptKey);
      sdk.async = true;
      sdk.dataset.zipaiKakaoSdk = 'true';
      sdk.onload = finishLoading;
      sdk.onerror = function () { reject(new Error('sdk-load-failed')); };
      document.head.appendChild(sdk);
    });
  }

  if (!window.__zipaiKakaoMapPromise) {
    window.__zipaiKakaoMapPromise = fetchJavaScriptKey().then(loadSdk);
  }

  window.__zipaiKakaoMapPromise.then(loadHome).catch(function (error) {
    const reason = error && error.message === 'missing-key' ? 'missing-key' : 'load-failed';
    window.__zipaiKakaoMapLoadError = reason;
    showError(reason === 'missing-key'
      ? '카카오 지도 JavaScript 키가 설정되지 않았습니다.'
      : '카카오 지도를 불러오지 못했습니다. 허용 도메인과 네트워크를 확인해 주세요.');
    notify('zipai:kakao-map-error', reason);
  });
})();
