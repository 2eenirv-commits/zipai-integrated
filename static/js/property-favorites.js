(function () {
  'use strict';

  const grid = document.getElementById('favoriteGrid');
  const empty = document.getElementById('favoriteEmpty');
  const emptyTitle = document.getElementById('favoriteEmptyTitle');
  const emptyText = document.getElementById('favoriteEmptyText');
  const emptyAction = document.getElementById('favoriteEmptyAction');
  const summary = document.getElementById('favoriteSummary');
  const filters = document.getElementById('favoriteFilters');
  const queryInput = document.getElementById('favoriteQuery');
  const buildingInput = document.getElementById('favoriteBuilding');
  const dealInput = document.getElementById('favoriteDeal');
  const loginGate = document.getElementById('favoriteLoginGate');
  const content = document.getElementById('favoriteContent');
  const pageHero = document.getElementById('favoritePageHero');
  let items = [];
  let logged = false;

  function price(property) {
    if (property.dealType === 'SALE') return '매매 ' + Number(property.salePrice || 0).toLocaleString('ko-KR') + '만원';
    if (property.dealType === 'JEONSE') return '전세 ' + Number(property.deposit || 0).toLocaleString('ko-KR') + '만원';
    return '월세 ' + Number(property.deposit || 0).toLocaleString('ko-KR') + '/' + Number(property.monthly || 0).toLocaleString('ko-KR') + '만원';
  }

  function dealLabel(value) {
    return value === 'SALE' ? '매매' : value === 'JEONSE' ? '전세' : '월세';
  }

  function card(property) {
    const el = document.createElement('article');
    el.className = 'favorite-card';
    const image = document.createElement('div');
    image.className = 'favorite-image';
    if (property.imageUrl) {
      const img = document.createElement('img');
      img.src = property.imageUrl;
      img.alt = (property.title || '매물') + ' 대표 사진';
      image.appendChild(img);
    } else {
      const icon = document.createElement('i');
      icon.className = 'fa-solid fa-house';
      image.appendChild(icon);
    }

    const body = document.createElement('div');
    body.className = 'favorite-body';
    const title = document.createElement('h3');
    title.textContent = property.title || '매물';
    const priceText = document.createElement('p');
    priceText.className = 'favorite-price';
    priceText.textContent = price(property);
    const address = document.createElement('p');
    address.className = 'favorite-address';
    address.textContent = property.address || '';
    const meta = document.createElement('div');
    meta.className = 'favorite-meta';
    [property.buildingType || property.type, dealLabel(property.dealType), property.area ? property.area + '㎡' : ''].filter(Boolean).forEach(function (value) {
      const tag = document.createElement('span');
      tag.textContent = value;
      meta.appendChild(tag);
    });
    const source = document.createElement('p');
    source.className = 'favorite-source';
    source.textContent = property.source || property.sourceType || '찜한 매물';
    body.append(title, priceText, address, meta, source);

    const actions = document.createElement('div');
    actions.className = 'favorite-card-actions';
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'favorite-remove';
    remove.textContent = '찜 해제';
    remove.addEventListener('click', function () { removeFavorite(Number(property.id), remove); });
    const view = document.createElement('a');
    view.href = '/#saved-homes';
    view.textContent = property.lat != null && property.lng != null ? '목록·지도에서 보기' : '목록에서 보기';
    actions.append(remove, view);
    el.append(image, body, actions);
    return el;
  }

  function filteredItems() {
    const query = String(queryInput.value || '').trim().toLowerCase().replace(/\s+/g, '');
    const building = buildingInput.value;
    const deal = dealInput.value;
    return items.filter(function (item) {
      if (building !== 'all' && String(item.buildingType || item.type) !== building) return false;
      if (deal !== 'all' && item.dealType !== deal) return false;
      if (!query) return true;
      return [item.title, item.address, item.sido, item.district, item.neighborhood]
        .some(function (value) { return String(value || '').toLowerCase().replace(/\s+/g, '').includes(query); });
    });
  }

  function render() {
    grid.innerHTML = '';
    if (!logged) {
      loginGate.hidden = false;
      content.hidden = true;
      pageHero.hidden = true;
      return;
    }

    loginGate.hidden = true;
    content.hidden = false;
    pageHero.hidden = false;

    filters.hidden = items.length === 0;
    const visible = filteredItems();
    visible.forEach(function (item) { grid.appendChild(card(item)); });
    empty.hidden = visible.length > 0;
    if (!visible.length) {
      const filtering = items.length > 0;
      emptyTitle.textContent = filtering ? '조건에 맞는 찜한 매물이 없습니다.' : '아직 찜한 매물이 없습니다.';
      emptyText.textContent = filtering ? '검색어나 필터 조건을 변경해 보세요.' : '전체 매물에서 마음에 드는 매물을 찜해 보세요.';
      emptyAction.href = '/';
      emptyAction.textContent = '매물 보러 가기';
    }
    summary.textContent = '찜한 매물 ' + items.length + '건' + (visible.length !== items.length ? ' · 현재 표시 ' + visible.length + '건' : '');
  }

  async function removeFavorite(id, button) {
    button.disabled = true;
    const nextIds = items.map(function (item) { return Number(item.id); }).filter(function (value) { return value !== id; });
    try {
      const response = await fetch('/api/favorites', {
        method: 'PUT', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ids: nextIds })
      });
      if (!response.ok) throw new Error('찜 목록을 저장하지 못했습니다.');
      items = items.filter(function (item) { return Number(item.id) !== id; });
      render();
    } catch (error) {
      button.disabled = false;
      summary.textContent = error.message;
    }
  }

  async function run() {
    if (window.ZipaiAuth) {
      await window.ZipaiAuth.ready;
      logged = !!window.ZipaiAuth.getUser();
    }
    if (!logged) {
      render();
      return;
    }
    try {
      const response = await fetch('/api/favorites', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('찜 목록을 불러오지 못했습니다.');
      const payload = await response.json();
      items = Array.isArray(payload.items) ? payload.items : [];
    } catch (error) {
      summary.textContent = error.message;
    }
    render();
  }

  [queryInput, buildingInput, dealInput].forEach(function (control) {
    control.addEventListener(control === queryInput ? 'input' : 'change', render);
  });
  run();
})();
