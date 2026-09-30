(function () {
  'use strict';

  const host = document.querySelector('[data-safety-map]');
  if (!host) return;

  const canvas = host.querySelector('.safety-map-canvas');
  const status = host.querySelector('.safety-map-status');
  const filterButtons = Array.from(host.querySelectorAll('[data-map-filter]'));
  const filterTypes = ['cctv', 'police', 'bell', 'light', 'women'];
  const activeFilters = new Set(filterTypes);
  const markerColors = {
    cctv: '#2563eb', police: '#15803d', bell: '#ea580c',
    light: '#ca8a04', women: '#9333ea'
  };
  const state = {
    map: null, mapAdapter: null, circle: null, centerMarker: null, infoWindow: null,
    entries: [], primaryFacilities: [], womenFacilities: [],
    center: null, radiusMeters: 500, ready: false, initialization: null
  };

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function setStatus(title, message, isError) {
    if (!status) return;
    status.hidden = false;
    status.classList.toggle('is-error', Boolean(isError));
    status.innerHTML = '<strong>' + escapeHtml(title) + '</strong><span>' + escapeHtml(message) + '</span>';
  }

  function hideStatus() {
    if (status) status.hidden = true;
  }

  function validCenter(value) {
    if (!value) return null;
    const latitude = Number(value.latitude);
    const longitude = Number(value.longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
    return Object.assign({}, value, { latitude: latitude, longitude: longitude });
  }

  function latLng(value) {
    return new naver.maps.LatLng(Number(value.latitude), Number(value.longitude));
  }

  function waitForNaver() {
    if (window.naver && window.naver.maps) return Promise.resolve();
    if (window.__zipaiNaverMapLoadError) return Promise.reject(new Error('NAVER Maps SDK ' + window.__zipaiNaverMapLoadError));
    if (window.__zipaiNaverMapPromise) return window.__zipaiNaverMapPromise;
    return new Promise(function (resolve, reject) {
      let finished = false;
      const timer = window.setTimeout(function () {
        if (finished) return;
        finished = true;
        reject(new Error('NAVER Maps SDK timeout'));
      }, 10000);
      document.addEventListener('zipai:naver-map-ready', function () {
        if (finished) return;
        finished = true;
        window.clearTimeout(timer);
        resolve();
      }, { once: true });
      document.addEventListener('zipai:naver-map-error', function () {
        if (finished) return;
        finished = true;
        window.clearTimeout(timer);
        reject(new Error('NAVER Maps SDK load failed'));
      }, { once: true });
    });
  }

  function ensureMap() {
    if (state.ready) return Promise.resolve(state.map);
    if (state.initialization) return state.initialization;
    setStatus('네이버 지도를 불러오는 중입니다.', '잠시만 기다려 주세요.', false);
    state.initialization = waitForNaver().then(function () {
      if (!window.L || typeof window.L.map !== 'function') {
        throw new Error('NAVER map adapter is unavailable');
      }
      const canvasRect = canvas.getBoundingClientRect();
      if (canvasRect.width < 1 || canvasRect.height < 1) {
        throw new Error('NAVER map container has no visible size: '
          + Math.round(canvasRect.width) + 'x' + Math.round(canvasRect.height));
      }
      const initial = validCenter(state.center) || { latitude: 37.5665, longitude: 126.9780 };
      state.mapAdapter = window.L.map(canvas, {
        zoomControl: false,
        minZoom: 7,
        maxZoom: 19
      }).setView([initial.latitude, initial.longitude], 14);
      window.L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19
      }).addTo(state.mapAdapter);
      state.map = state.mapAdapter._native;
      state.map.setOptions({
        scrollWheel: false,
        disableKineticPan: true,
        keyboardShortcuts: false
      });
      naver.maps.Event.addListener(state.map, 'click', closeFacilityOverlay);
      state.ready = true;
      host.classList.remove('is-map-unavailable');
      if (state.center) renderCurrentState();
      else setStatus('지도 준비', '지역을 검색하면 안전시설 위치가 표시됩니다.', false);
      [0, 250, 600].forEach(function (delay) {
        window.setTimeout(function () {
          if (!state.ready || !state.map) return;
          state.mapAdapter.invalidateSize();
          if (state.center) state.map.setCenter(latLng(state.center));
        }, delay);
      });
      return state.map;
    }).catch(function (error) {
      state.initialization = null;
      host.classList.add('is-map-unavailable');
      const sdkError = window.__zipaiNaverMapLoadError;
      const message = sdkError
        ? '지도 인증키와 Web 서비스 URL을 확인해 주세요.'
        : '지도 실행 중 오류가 발생했습니다. 브라우저 콘솔을 확인해 주세요.';
      console.error('[ZipAI Safety Map]', error);
      setStatus('네이버 지도를 불러오지 못했습니다.', message, true);
      return null;
    });
    return state.initialization;
  }

  function facilityType(facility, women) {
    if (women) return 'women';
    const type = String(facility.facilityType || '').toUpperCase();
    if (type === 'POLICE_STATION' || type === 'POLICE_BOX') return 'police';
    if (type === 'SAFETY_BELL' || type === 'EMERGENCY_BELL') return 'bell';
    if (type === 'STREET_LIGHT' || type === 'SECURITY_LIGHT') return 'light';
    return 'cctv';
  }

  function typeLabel(facility, type) {
    if (facility.label) return facility.label;
    if (type === 'cctv') return 'CCTV';
    if (type === 'police') return facility.facilityType === 'POLICE_BOX' ? '파출소·지구대' : '경찰서';
    if (type === 'bell') return '안전 비상벨';
    if (type === 'light') return '보안등';
    if (facility.facilityType === 'SAFE_PARCEL_LOCKER') return '안심택배함';
    if (facility.facilityType === 'SAFE_STORE') return '여성안심점포';
    if (facility.facilityType === 'SAFE_ROUTE') return '여성안심길';
    if (facility.facilityType === 'SAFE_HOUSE') return '여성안심지킴이집';
    return '여성안전시설';
  }

  function markerContent(type) {
    const symbols = { cctv: '●', police: '◆', bell: '!', light: '✦', women: '♥' };
    return '<span class="safety-map-marker" style="--marker-color:' + markerColors[type] + '">'
      + '<span>' + symbols[type] + '</span></span>';
  }

  function clearMarkers() {
    closeFacilityOverlay();
    state.entries.forEach(function (entry) { entry.marker.setMap(null); });
    state.entries = [];
  }

  function createMarker(facility, type) {
    const position = validCenter(facility);
    if (!position) return null;
    const marker = new naver.maps.Marker({
      position: latLng(position),
      icon: {
        content: markerContent(type),
        size: new naver.maps.Size(36, 42),
        anchor: new naver.maps.Point(18, 42)
      },
      title: (facility.name || typeLabel(facility, type)) + ' · ' + typeLabel(facility, type),
      clickable: true,
      map: activeFilters.has(type) ? state.map : null
    });
    naver.maps.Event.addListener(marker, 'click', function () {
      openFacilityOverlay(marker, facility, type);
    });
    return { marker: marker, facility: facility, type: type };
  }

  function rebuildMarkers() {
    if (!state.ready) return;
    clearMarkers();
    state.primaryFacilities.forEach(function (facility) {
      const entry = createMarker(facility, facilityType(facility, false));
      if (entry) state.entries.push(entry);
    });
    state.womenFacilities.forEach(function (facility) {
      const entry = createMarker(facility, facilityType(facility, true));
      if (entry) state.entries.push(entry);
    });
  }

  function updateVisibleMarkers() {
    closeFacilityOverlay();
    state.entries.forEach(function (entry) {
      entry.marker.setMap(activeFilters.has(entry.type) ? state.map : null);
    });
  }

  function renderCenter() {
    if (state.centerMarker) state.centerMarker.setMap(null);
    state.centerMarker = new naver.maps.Marker({
      position: latLng(state.center),
      map: state.map,
      zIndex: 100,
      icon: {
        content: '<div class="safety-search-center" title="검색 위치"><span></span><strong>검색 위치</strong></div>',
        size: new naver.maps.Size(80, 36),
        anchor: new naver.maps.Point(40, 18)
      }
    });
  }

  function updateRadiusCircle() {
    if (state.circle) state.circle.setMap(null);
    state.circle = new naver.maps.Circle({
      map: state.map,
      center: latLng(state.center),
      radius: state.radiusMeters,
      strokeWeight: 2, strokeColor: '#2563eb', strokeOpacity: 0.72,
      fillColor: '#60a5fa', fillOpacity: 0.09,
      clickable: false
    });
  }

  function fitBounds() {
    const latDelta = state.radiusMeters / 111320;
    const lngDelta = state.radiusMeters / (111320 * Math.max(0.25, Math.cos(state.center.latitude * Math.PI / 180)));
    const bounds = new naver.maps.LatLngBounds(
      new naver.maps.LatLng(state.center.latitude - latDelta, state.center.longitude - lngDelta),
      new naver.maps.LatLng(state.center.latitude + latDelta, state.center.longitude + lngDelta)
    );
    state.map.fitBounds(bounds, { top: 56, right: 56, bottom: 88, left: 56 });
  }

  function renderCurrentState() {
    if (!state.ready || !state.center) return;
    hideStatus();
    renderCenter();
    updateRadiusCircle();
    rebuildMarkers();
    fitBounds();
  }

  function openFacilityOverlay(marker, facility, type) {
    closeFacilityOverlay();
    const name = facility.name || [facility.brandName, facility.storeName].filter(Boolean).join(' ') || typeLabel(facility, type);
    const source = [facility.sourceName, facility.sourceUpdatedAt || facility.dataYear].filter(Boolean).join(' · ');
    const content = '<article class="safety-facility-card" data-tone="' + type + '"><button type="button" class="safety-overlay-close" aria-label="시설 정보 닫기">×</button>'
      + '<span class="safety-facility-type">' + escapeHtml(typeLabel(facility, type)) + '</span><strong>' + escapeHtml(name) + '</strong>'
      + '<p>' + escapeHtml(facility.address || '주소 정보 없음') + '</p><span>검색 위치에서 ' + escapeHtml(String(facility.distanceMeters == null ? '-' : facility.distanceMeters)) + 'm</span>'
      + (source ? '<small>' + escapeHtml(source) + '</small>' : '') + '</article>';
    state.infoWindow = new naver.maps.InfoWindow({
      content: content,
      borderWidth: 0,
      backgroundColor: 'transparent',
      anchorSize: new naver.maps.Size(0, 0),
      pixelOffset: new naver.maps.Point(0, -12)
    });
    state.infoWindow.open(state.map, marker);
    window.setTimeout(function () {
      const close = host.querySelector('.safety-overlay-close');
      if (close) close.addEventListener('click', closeFacilityOverlay, { once: true });
    }, 0);
  }

  function closeFacilityOverlay() {
    if (state.infoWindow) state.infoWindow.close();
    state.infoWindow = null;
  }

  function render(payload) {
    state.center = validCenter(payload && payload.center);
    state.radiusMeters = Math.max(100, Number(payload && payload.radiusMeters) || 500);
    state.primaryFacilities = Array.isArray(payload && payload.facilities) ? payload.facilities : [];
    state.womenFacilities = [];
    ensureMap().then(function (map) { if (map && state.center) renderCurrentState(); });
  }

  function appendWomenFacilities(payload) {
    if (payload && validCenter(payload.center)) state.center = validCenter(payload.center);
    if (payload && payload.radiusMeters) state.radiusMeters = Math.max(100, Number(payload.radiusMeters) || 500);
    state.womenFacilities = Array.isArray(payload && payload.facilities) ? payload.facilities : [];
    ensureMap().then(function (map) { if (map && state.center) rebuildMarkers(); });
  }

  function reset() {
    state.primaryFacilities = [];
    state.womenFacilities = [];
    state.center = null;
    clearMarkers();
    if (state.circle) state.circle.setMap(null);
    if (state.centerMarker) state.centerMarker.setMap(null);
    state.circle = null;
    state.centerMarker = null;
    if (state.ready) setStatus('지도 준비', '지역을 검색하면 안전시설 위치가 표시됩니다.', false);
  }

  function relayout() {
    ensureMap().then(function (map) {
      if (!map) return;
      if (state.mapAdapter) state.mapAdapter.invalidateSize();
      else naver.maps.Event.trigger(map, 'resize');
      if (state.center) {
        map.setCenter(latLng(state.center));
        fitBounds();
      }
    });
  }

  function updateFilterButtonState() {
    filterButtons.forEach(function (button) {
      const type = button.dataset.mapFilter;
      const pressed = type === 'all' ? activeFilters.size === filterTypes.length : activeFilters.has(type);
      button.setAttribute('aria-pressed', String(pressed));
    });
  }

  filterButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      const type = button.dataset.mapFilter;
      if (type === 'all') {
        if (activeFilters.size === filterTypes.length) activeFilters.clear();
        else filterTypes.forEach(function (item) { activeFilters.add(item); });
      } else if (activeFilters.has(type)) activeFilters.delete(type);
      else activeFilters.add(type);
      updateFilterButtonState();
      updateVisibleMarkers();
    });
  });

  host.querySelectorAll('[data-map-action]').forEach(function (button) {
    button.addEventListener('click', function () {
      if (!state.ready) return;
      const action = button.dataset.mapAction;
      if (action === 'zoom-in') state.map.setZoom(Math.min(19, state.map.getZoom() + 1));
      if (action === 'zoom-out') state.map.setZoom(Math.max(7, state.map.getZoom() - 1));
      if (action === 'recenter' && state.center) {
        state.map.panTo(latLng(state.center));
        fitBounds();
      }
    });
  });

  const legendToggle = host.querySelector('.map-legend-toggle');
  if (legendToggle) {
    legendToggle.addEventListener('click', function () {
      const expanded = legendToggle.getAttribute('aria-expanded') !== 'true';
      legendToggle.setAttribute('aria-expanded', String(expanded));
      host.classList.toggle('is-legend-open', expanded);
    });
  }

  window.ZipaiSafetyMap = {
    render: render,
    appendWomenFacilities: appendWomenFacilities,
    reset: reset,
    relayout: relayout,
    closeFacilityOverlay: closeFacilityOverlay
  };

  if ('ResizeObserver' in window) {
    let resizeFrame = null;
    const resizeObserver = new ResizeObserver(function () {
      if (!state.ready || !canvas.clientWidth || !canvas.clientHeight) return;
      if (resizeFrame) window.cancelAnimationFrame(resizeFrame);
      resizeFrame = window.requestAnimationFrame(function () {
        if (state.mapAdapter) state.mapAdapter.invalidateSize();
        else naver.maps.Event.trigger(state.map, 'resize');
        if (state.center) state.map.setCenter(latLng(state.center));
      });
    });
    resizeObserver.observe(canvas);
  }

  if (!host.closest('[hidden]')) ensureMap();
})();
