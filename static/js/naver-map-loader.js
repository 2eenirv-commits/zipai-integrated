(function () {
  'use strict';

  const mapElement = document.getElementById('realMap');

  function showError(message) {
    if (mapElement) mapElement.innerHTML = '<div class="map-fallback">' + message + '</div>';
  }

  function notifyReady() {
    document.dispatchEvent(new CustomEvent('zipai:naver-map-ready'));
  }

  function loadHome() {
    notifyReady();
    if (!mapElement || document.querySelector('script[data-zipai-home]')) return;
    const script = document.createElement('script');
    script.src = 'static/js/home.js?v=20260922-3';
    script.dataset.zipaiHome = 'true';
    document.body.appendChild(script);
  }

  const config = window.ZIPAI_CONFIG || {};
  if (!config.naverMapNcpKeyId) {
    window.__zipaiNaverMapLoadError = 'missing-key';
    showError('네이버 지도 인증키가 필요합니다. static/js/map-config.js를 확인해 주세요.');
    document.dispatchEvent(new CustomEvent('zipai:naver-map-error', { detail: { reason: 'missing-key' } }));
    return;
  }

  if (!window.__zipaiNaverMapPromise) {
    window.__zipaiNaverMapPromise = window.naver && window.naver.maps
      ? Promise.resolve()
      : new Promise(function (resolve, reject) {
          const sdk = document.createElement('script');
          sdk.src = 'https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=' + encodeURIComponent(config.naverMapNcpKeyId);
          sdk.async = true;
          sdk.onload = resolve;
          sdk.onerror = reject;
          document.head.appendChild(sdk);
        });
  }

  window.__zipaiNaverMapPromise.then(loadHome).catch(function () {
    window.__zipaiNaverMapLoadError = 'load-failed';
    showError('네이버 지도를 불러오지 못했습니다. 인증키와 Web 서비스 URL을 확인해 주세요.');
    document.dispatchEvent(new CustomEvent('zipai:naver-map-error', { detail: { reason: 'load-failed' } }));
  });
})();
