(function () {
  'use strict';

  const form = document.getElementById('salePropertyForm');
  if (!form) return;

  const input = document.getElementById('propertyImages');
  const preview = document.getElementById('imagePreview');
  const message = document.getElementById('registerMessage');
  const representativeIndex = document.getElementById('representativeIndex');
  const addressSearch = document.getElementById('addressSearch');
  const addressSuggestions = document.getElementById('addressSuggestions');
  const addressSearchSpinner = document.getElementById('addressSearchSpinner');
  const geocodeStatus = document.getElementById('geocodeStatus');
  const detailAddress = document.getElementById('detailAddress');
  const addressInput = document.getElementById('selectedAddress');
  const sidoInput = document.getElementById('selectedSido');
  const sigunguInput = document.getElementById('selectedSigungu');
  const neighborhoodInput = document.getElementById('selectedNeighborhood');
  const latInput = document.getElementById('selectedLat');
  const lngInput = document.getElementById('selectedLng');
  const contactInput = document.getElementById('contactInput');
  const submitButton = document.getElementById('salePropertySubmitButton');
  const submitLabel = submitButton ? submitButton.querySelector('.submit-label') : null;
  const dealType = document.getElementById('dealType');
  const myListingGrid = document.getElementById('myListingGrid');
  const myListingEmpty = document.getElementById('myListingEmpty');
  const myListingMessage = document.getElementById('myListingMessage');
  const refreshMyListings = document.getElementById('refreshMyListings');
  const loginGate = document.getElementById('registerLoginGate');
  const formCard = document.getElementById('registerFormCard');
  const myListingsCard = document.getElementById('myListingsCard');
  const pageHero = document.getElementById('registerPageHero');
  const statusFilter = document.getElementById('myListingStatusFilter');
  const detailDialog = document.getElementById('listingDetailDialog');
  const detailTitle = document.getElementById('listingDetailTitle');
  const detailContent = document.getElementById('listingDetailContent');
  const closeDetail = document.getElementById('closeListingDetail');
  const cancelEditButton = document.getElementById('cancelEditButton');
  const formModeTitle = document.getElementById('formModeTitle');
  const formModeHelp = document.getElementById('formModeHelp');
  const photoHelp = document.getElementById('photoHelp');
  const existingImagePreview = document.getElementById('existingImagePreview');
  const priceFields = {
    SALE: document.getElementById('salePriceField'),
    JEONSE: document.getElementById('depositField'),
    MONTHLY: document.getElementById('monthlyField')
  };

  let searchTimer = null;
  let searchSequence = 0;
  let selectedAddressText = '';
  let editingId = null;
  let myListings = [];

  async function applyLoginView() {
    if (window.ZipaiAuth) await window.ZipaiAuth.ready;
    const logged = !!(window.ZipaiAuth && window.ZipaiAuth.getUser());
    const guest = Boolean(window.ZipaiAuth && window.ZipaiAuth.isGuest && window.ZipaiAuth.isGuest());
    loginGate.hidden = logged && !guest;
    formCard.hidden = !logged || guest;
    myListingsCard.hidden = !logged || guest;
    pageHero.hidden = !logged || guest;
    if (guest) {
      const message = loginGate.querySelector('strong');
      if (message) message.textContent = '게스트 모드에서는 매물 등록·수정·삭제를 이용할 수 없습니다.';
    }
    return logged && !guest;
  }

  function listingPrice(property) {
    if (property.dealType === 'SALE') return '매매 ' + Number(property.salePrice || 0).toLocaleString('ko-KR') + '만원';
    if (property.dealType === 'JEONSE') return '전세 ' + Number(property.deposit || 0).toLocaleString('ko-KR') + '만원';
    return '월세 ' + Number(property.deposit || 0).toLocaleString('ko-KR') + '/' + Number(property.monthly || 0).toLocaleString('ko-KR') + '만원';
  }

  function myListingCard(property) {
    const card = document.createElement('article');
    card.className = 'favorite-card';
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
    const price = document.createElement('p');
    price.className = 'favorite-price';
    price.textContent = listingPrice(property);
    const address = document.createElement('p');
    address.className = 'favorite-address';
    address.textContent = property.address || '';
    const meta = document.createElement('div');
    meta.className = 'favorite-meta';
    const status = document.createElement('span');
    const active = property.status === 'active';
    status.className = 'listing-status ' + (active ? 'is-active' : 'is-closed');
    status.textContent = active ? '거래중' : '거래완료';
    meta.appendChild(status);
    [property.buildingType || property.type, property.area ? property.area + '㎡' : ''].filter(Boolean).forEach(function (value) {
      const tag = document.createElement('span');
      tag.textContent = value;
      meta.appendChild(tag);
    });
    body.append(title, price, address, meta);
    const actions = document.createElement('div');
    actions.className = 'listing-card-actions';
    const detail = document.createElement('button');
    detail.type = 'button';
    detail.textContent = '상세보기';
    detail.addEventListener('click', function () { showListingDetail(property); });
    const edit = document.createElement('button');
    edit.type = 'button';
    edit.textContent = '수정';
    edit.addEventListener('click', function () { startEditing(property); });
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'danger-action';
    remove.textContent = '삭제';
    remove.addEventListener('click', function () { deleteListing(property, remove); });
    actions.append(detail, edit, remove);
    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'listing-status-button';
    toggle.textContent = active ? '거래완료로 변경' : '다시 거래중으로 변경';
    toggle.addEventListener('click', function () { changeListingStatus(property, toggle); });
    card.append(image, body, actions, toggle);
    return card;
  }

  function detailRow(list, label, value) {
    const term = document.createElement('dt');
    term.textContent = label;
    const description = document.createElement('dd');
    description.textContent = value == null || value === '' ? '-' : String(value);
    list.append(term, description);
  }

  function showListingDetail(property) {
    detailTitle.textContent = property.title || '매물 상세정보';
    detailContent.innerHTML = '';
    detailContent.className = 'listing-detail-content';
    if (Array.isArray(property.imageUrls) && property.imageUrls.length) {
      const images = document.createElement('div');
      images.className = 'listing-detail-images';
      property.imageUrls.forEach(function (url, index) {
        const img = document.createElement('img');
        img.src = url;
        img.alt = (property.title || '매물') + ' 사진 ' + (index + 1);
        images.appendChild(img);
      });
      detailContent.appendChild(images);
    }
    const list = document.createElement('dl');
    list.className = 'listing-detail-table';
    detailRow(list, '상태', property.status === 'active' ? '거래중' : '거래완료');
    detailRow(list, '유형', (property.buildingType || property.type || '-') + ' · ' + listingPrice(property));
    detailRow(list, '주소', property.address);
    detailRow(list, '면적·층', [property.area ? property.area + '㎡' : '', property.floor || ''].filter(Boolean).join(' · '));
    detailRow(list, '관리비', Number(property.maintenance || 0).toLocaleString('ko-KR') + '만원');
    detailRow(list, '연락처', property.ownerContact || property.contact);
    detailRow(list, '선택사항', [property.parking ? '주차' : '', property.elevator ? '엘리베이터' : '', property.pet ? '반려동물 협의' : ''].filter(Boolean).join(' · ') || '-');
    detailRow(list, '설명', property.description);
    detailContent.appendChild(list);
    if (typeof detailDialog.showModal === 'function') detailDialog.showModal();
    else detailDialog.setAttribute('open', '');
  }

  function renderExistingImages(property) {
    existingImagePreview.innerHTML = '';
    const urls = Array.isArray(property.imageUrls) ? property.imageUrls : [];
    existingImagePreview.hidden = urls.length === 0;
    urls.forEach(function (url, index) {
      const img = document.createElement('img');
      img.src = url;
      img.alt = (property.title || '매물') + ' 기존 사진 ' + (index + 1);
      existingImagePreview.appendChild(img);
    });
  }

  function startEditing(property) {
    editingId = Number(property.id);
    formModeTitle.textContent = '매물 수정';
    formModeHelp.textContent = '등록한 정보를 수정합니다. 주소를 바꾸려면 주소 검색 결과를 다시 선택해 주세요.';
    photoHelp.textContent = '사진을 선택하지 않으면 기존 사진을 유지합니다. 새 사진을 선택하면 기존 사진 전체를 교체합니다.';
    cancelEditButton.hidden = false;
    input.required = false;
    form.elements.title.value = property.title || '';
    form.elements.buildingType.value = property.buildingType || property.type || '';
    dealType.value = property.dealType || 'SALE';
    form.elements.salePrice.value = property.salePrice == null ? '' : property.salePrice;
    form.elements.deposit.value = property.deposit == null ? '' : property.deposit;
    form.elements.monthly.value = property.monthly == null ? '' : property.monthly;
    form.elements.area.value = property.area == null ? '' : property.area;
    form.elements.floor.value = property.floor || '';
    form.elements.maintenance.value = property.maintenance || 0;
    form.elements.contact.value = property.ownerContact || '';
    form.elements.description.value = property.description || '';
    form.elements.parking.checked = !!property.parking;
    form.elements.elevator.checked = !!property.elevator;
    form.elements.pet.checked = !!property.pet;
    selectedAddressText = property.address || '';
    addressSearch.value = selectedAddressText;
    addressInput.value = selectedAddressText;
    sidoInput.value = property.sido || '';
    sigunguInput.value = property.district || '';
    neighborhoodInput.value = property.neighborhood || '';
    latInput.value = property.lat == null ? '' : property.lat;
    lngInput.value = property.lng == null ? '' : property.lng;
    detailAddress.value = '';
    detailAddress.disabled = false;
    input.value = '';
    preview.innerHTML = '';
    renderExistingImages(property);
    updatePriceFields();
    setMessage('수정할 내용을 확인해 주세요.');
    setSubmitting(false, '수정 내용 저장');
    formCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function resetEditMode() {
    editingId = null;
    form.reset();
    formModeTitle.textContent = '새 매물 등록';
    formModeHelp.textContent = '매물 정보를 입력하고 사진을 등록해 주세요.';
    photoHelp.textContent = 'JPG · PNG · WEBP / 장당 5MB 이하 / 최대 10장 · 사진을 누르면 대표 사진으로 지정됩니다.';
    cancelEditButton.hidden = true;
    input.required = true;
    input.value = '';
    preview.innerHTML = '';
    existingImagePreview.innerHTML = '';
    existingImagePreview.hidden = true;
    representativeIndex.value = '0';
    clearSelectedAddress();
    addressSearch.value = '';
    closeSuggestions();
    updatePriceFields();
    setMessage('');
    setSubmitting(false, '매물 등록하기');
  }

  async function loadMyListings() {
    if (!myListingGrid) return;
    myListingMessage.textContent = '등록 매물을 불러오는 중입니다.';
    try {
      const logged = await applyLoginView();
      if (!logged) {
        myListingMessage.textContent = '';
        return;
      }
      const response = await fetch('/api/properties/mine', { credentials: 'same-origin' });
      const payload = await readResponse(response);
      if (!response.ok) throw new Error(payload.message || '등록 매물을 불러오지 못했습니다.');
      myListings = Array.isArray(payload.items) ? payload.items : [];
      const selectedStatus = statusFilter ? statusFilter.value : 'all';
      const listings = selectedStatus === 'all' ? myListings : myListings.filter(function (item) { return item.status === selectedStatus; });
      myListingGrid.innerHTML = '';
      listings.forEach(function (item) { myListingGrid.appendChild(myListingCard(item)); });
      myListingEmpty.hidden = listings.length > 0;
      myListingMessage.textContent = myListings.length ? '전체 ' + myListings.length + '건 · 현재 표시 ' + listings.length + '건' : '';
      myListingMessage.className = 'form-message';
    } catch (error) {
      myListingMessage.textContent = error.message;
      myListingMessage.className = 'form-message is-error';
    }
  }

  async function changeListingStatus(property, button) {
    const nextStatus = property.status === 'active' ? 'closed' : 'active';
    const question = nextStatus === 'closed'
      ? '이 매물을 거래완료로 변경할까요? 일반 검색과 찜 목록에서는 더 이상 표시되지 않습니다.'
      : '이 매물을 다시 거래중으로 변경할까요? 일반 검색에 다시 표시됩니다.';
    if (!window.confirm(question)) return;
    button.disabled = true;
    try {
      const response = await fetch('/api/properties/' + encodeURIComponent(property.id) + '/status', {
        method: 'PATCH', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: nextStatus })
      });
      const payload = await readResponse(response);
      if (!response.ok) throw new Error(payload.message || '매물 상태를 변경하지 못했습니다.');
      await loadMyListings();
    } catch (error) {
      button.disabled = false;
      myListingMessage.textContent = error.message;
      myListingMessage.className = 'form-message is-error';
    }
  }

  async function deleteListing(property, button) {
    if (!window.confirm('“' + (property.title || '매물') + '”을 삭제할까요? 삭제한 매물과 사진은 복구할 수 없습니다.')) return;
    button.disabled = true;
    try {
      const response = await fetch('/api/properties/' + encodeURIComponent(property.id), {
        method: 'DELETE', credentials: 'same-origin'
      });
      const payload = await readResponse(response);
      if (!response.ok) throw new Error(payload.message || '매물을 삭제하지 못했습니다.');
      if (editingId === Number(property.id)) resetEditMode();
      await loadMyListings();
      myListingMessage.textContent = '매물이 삭제되었습니다.';
      myListingMessage.className = 'form-message is-success';
    } catch (error) {
      button.disabled = false;
      myListingMessage.textContent = error.message;
      myListingMessage.className = 'form-message is-error';
    }
  }

  function updatePriceFields() {
    const selected = dealType ? dealType.value : 'SALE';
    Object.values(priceFields).forEach(function (field) {
      if (!field) return;
      field.hidden = true;
      const input = field.querySelector('input');
      if (input) input.required = false;
    });
    if (selected === 'SALE' && priceFields.SALE) {
      priceFields.SALE.hidden = false;
      priceFields.SALE.querySelector('input').required = true;
    } else if (selected === 'JEONSE' && priceFields.JEONSE) {
      priceFields.JEONSE.hidden = false;
      priceFields.JEONSE.querySelector('input').required = true;
    } else if (selected === 'MONTHLY') {
      [priceFields.JEONSE, priceFields.MONTHLY].forEach(function (field) {
        if (!field) return;
        field.hidden = false;
        field.querySelector('input').required = true;
      });
    }
  }

  if (dealType) dealType.addEventListener('change', updatePriceFields);
  const query = new URLSearchParams(window.location.search);
  const requestedBuildingType = query.get('buildingType');
  const requestedDealType = String(query.get('dealType') || '').toUpperCase();
  if (requestedBuildingType) form.elements.buildingType.value = requestedBuildingType;
  if (['SALE', 'JEONSE', 'MONTHLY'].includes(requestedDealType)) dealType.value = requestedDealType;
  if (query.get('purpose') === 'youth-relay') {
    form.elements.description.value = '청년 주거생활 퇴실 예정 방 연결 매물입니다.\n';
  }
  updatePriceFields();

  function setSubmitting(active, label) {
    if (!submitButton) return;
    submitButton.disabled = active;
    submitButton.classList.toggle('is-loading', active);
    if (submitLabel) submitLabel.textContent = label || (active ? '처리 중...' : (editingId !== null ? '수정 내용 저장' : '매물 등록하기'));
  }

  function focusInvalid(field) {
    if (!field) return;
    try { field.focus({ preventScroll: true }); } catch (_) { field.focus(); }
    const target = fieldContainer(field) || field;
    if (target && typeof target.scrollIntoView === 'function') {
      target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }

  async function readResponse(response) {
    const text = await response.text();
    if (!text) return {};
    try { return JSON.parse(text); }
    catch (_) { return { message: text }; }
  }

  function setMessage(text, type) {
    message.textContent = text || '';
    message.classList.toggle('is-error', type === 'error');
    message.classList.toggle('is-success', type === 'success');
  }

  function fieldContainer(field) {
    return field.closest('label') || field.parentElement;
  }

  function clearFieldError(field) {
    const container = fieldContainer(field);
    if (!container) return;
    container.classList.remove('has-error');
    const old = container.querySelector('.field-error');
    if (old) old.remove();
  }

  function setFieldError(field, text) {
    const container = fieldContainer(field);
    if (!container) return;
    clearFieldError(field);
    container.classList.add('has-error');
    const error = document.createElement('span');
    error.className = 'field-error';
    error.textContent = text;
    container.appendChild(error);
  }

  function validateField(field) {
    clearFieldError(field);
    const value = String(field.value || '').trim();
    if (field.required && !value) {
      setFieldError(field, field.tagName === 'SELECT' ? '항목을 선택해 주세요.' : '필수 입력 항목입니다.');
      return false;
    }
    if (field.type === 'number' && value) {
      const number = Number(value);
      if (!Number.isFinite(number)) {
        setFieldError(field, '숫자로 입력해 주세요.');
        return false;
      }
      if (field.min !== '' && number < Number(field.min)) {
        setFieldError(field, field.min + ' 이상 입력해 주세요.');
        return false;
      }
      if (field.max !== '' && number > Number(field.max)) {
        setFieldError(field, field.max + ' 이하 입력해 주세요. 관리비 단위는 만원입니다.');
        return false;
      }
    }
    if (field.name === 'contact' && value && !/^(?:02-\d{3,4}-\d{4}|0\d{2}-\d{3,4}-\d{4})$/.test(value)) {
      setFieldError(field, '연락처 형식을 확인해 주세요.');
      return false;
    }
    return true;
  }

  function validateImages() {
    const files = Array.from(input.files || []);
    clearFieldError(input);
    if (!files.length) {
      if (editingId !== null) return true;
      setFieldError(input, '매물 사진을 1장 이상 첨부해 주세요.');
      return false;
    }
    if (files.length > 10) {
      setFieldError(input, '사진은 최대 10장까지 첨부할 수 있습니다.');
      return false;
    }
    const allowed = new Set(['image/jpeg', 'image/png', 'image/webp']);
    const invalidType = files.find(function (file) { return !allowed.has(file.type); });
    if (invalidType) {
      setFieldError(input, 'JPG, PNG, WEBP 파일만 첨부할 수 있습니다.');
      return false;
    }
    const tooLarge = files.find(function (file) { return file.size > 5 * 1024 * 1024; });
    if (tooLarge) {
      setFieldError(input, '사진은 장당 5MB 이하만 첨부할 수 있습니다.');
      return false;
    }
    return true;
  }

  function validateAddressSelection() {
    clearFieldError(addressSearch);
    if (!addressInput.value || !latInput.value || !lngInput.value || !sigunguInput.value) {
      setFieldError(addressSearch, '검색 결과에서 정확한 주소를 선택해 주세요.');
      return false;
    }
    return true;
  }

  function validateForm() {
    let valid = true;
    let firstInvalid = null;
    Array.from(form.querySelectorAll('input, select, textarea')).forEach(function (field) {
      if (field.type === 'file' || field.type === 'hidden' || field.readOnly || field.disabled) return;
      const ok = validateField(field);
      if (!ok && !firstInvalid) firstInvalid = field;
      valid = ok && valid;
    });
    if (!validateAddressSelection()) {
      valid = false;
      if (!firstInvalid) firstInvalid = addressSearch;
    }
    if (!validateImages()) {
      valid = false;
      if (!firstInvalid) firstInvalid = input;
    }
    if (firstInvalid) focusInvalid(firstInvalid);
    return valid;
  }

  function showFiles() {
    preview.innerHTML = '';
    representativeIndex.value = '0';
    if (editingId !== null && !(input.files || []).length) {
      clearFieldError(input);
      return;
    }
    if (!validateImages()) return;
    clearFieldError(input);
    const files = Array.from(input.files || []);
    files.forEach(function (file, index) {
      const card = document.createElement('button');
      card.type = 'button';
      card.className = 'photo-preview-card' + (index === 0 ? ' is-representative' : '');
      card.dataset.index = String(index);
      card.setAttribute('aria-label', file.name + ' 대표 사진으로 지정');

      const img = document.createElement('img');
      img.alt = file.name + ' 미리보기';
      const objectUrl = URL.createObjectURL(file);
      img.src = objectUrl;
      img.addEventListener('load', function () { URL.revokeObjectURL(objectUrl); }, { once: true });

      const badge = document.createElement('span');
      badge.className = 'representative-badge';
      badge.textContent = index === 0 ? '대표 사진' : '대표로 지정';
      card.append(img, badge);
      card.addEventListener('click', function () {
        representativeIndex.value = String(index);
        preview.querySelectorAll('.photo-preview-card').forEach(function (item) {
          const selected = Number(item.dataset.index) === index;
          item.classList.toggle('is-representative', selected);
          item.querySelector('.representative-badge').textContent = selected ? '대표 사진' : '대표로 지정';
        });
      });
      preview.appendChild(card);
    });
  }

  function coordinate(location, primary, fallback) {
    if (!location) return null;
    const value = location[primary] ?? location[fallback];
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  function parseAdministrativeArea(address) {
    const tokens = String(address || '').trim().split(/\s+/).filter(Boolean);
    const result = { sido: '', sigungu: '', neighborhood: '' };
    if (!tokens.length) return result;

    if (/(특별시|광역시|특별자치시|특별자치도|도)$/.test(tokens[0])) {
      result.sido = tokens[0];
      if (tokens[1]) {
        if (/시$/.test(tokens[1]) && tokens[2] && /구$/.test(tokens[2])) result.sigungu = tokens[1] + ' ' + tokens[2];
        else result.sigungu = tokens[1];
      }
    } else if (/(시|군|구)$/.test(tokens[0])) {
      result.sigungu = tokens[0];
    }

    const dong = tokens.find(function (token) { return /(동|읍|면|리)$/.test(token); });
    if (dong) result.neighborhood = dong;
    return result;
  }

  function normalizeCandidate(location) {
    if (!location) return null;
    const address = String(location.address || location.roadAddress || location.name || '').replace(/<[^>]*>/g, '').trim();
    const lat = coordinate(location, 'latitude', 'lat');
    const lng = coordinate(location, 'longitude', 'lng');
    if (!address || lat === null || lng === null) return null;

    const parsed = parseAdministrativeArea(address);
    return {
      address: address,
      lat: lat,
      lng: lng,
      sido: String(location.sidoName || location.sido || parsed.sido || '').trim(),
      sigungu: String(location.sigunguName || location.sigungu || location.district || parsed.sigungu || '').trim(),
      neighborhood: String(location.neighborhood || parsed.neighborhood || '').trim()
    };
  }

  function clearSelectedAddress() {
    selectedAddressText = '';
    addressInput.value = '';
    sidoInput.value = '';
    sigunguInput.value = '';
    neighborhoodInput.value = '';
    latInput.value = '';
    lngInput.value = '';
    detailAddress.disabled = true;
    geocodeStatus.textContent = '';
    geocodeStatus.className = 'geocode-status';
  }

  function closeSuggestions() {
    addressSuggestions.hidden = true;
    addressSuggestions.innerHTML = '';
    addressSearch.setAttribute('aria-expanded', 'false');
  }

  function selectAddress(candidate) {
    selectedAddressText = candidate.address;
    addressSearch.value = candidate.address;
    addressInput.value = candidate.address;
    sidoInput.value = candidate.sido;
    sigunguInput.value = candidate.sigungu;
    neighborhoodInput.value = candidate.neighborhood;
    latInput.value = candidate.lat.toFixed(7);
    lngInput.value = candidate.lng.toFixed(7);
    detailAddress.disabled = false;
    clearFieldError(addressSearch);
    closeSuggestions();
    geocodeStatus.textContent = '주소 선택 완료 · 지역과 지도 좌표는 내부에서 자동 처리됩니다.';
    geocodeStatus.className = 'geocode-status is-success';
    detailAddress.focus();
  }

  function renderSuggestions(candidates) {
    addressSuggestions.innerHTML = '';
    if (!candidates.length) {
      const empty = document.createElement('div');
      empty.className = 'address-suggestion-empty';
      empty.textContent = '검색 결과가 없습니다. 도로명과 지역명을 조금 더 정확히 입력해 주세요.';
      addressSuggestions.appendChild(empty);
    } else {
      candidates.forEach(function (candidate) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'address-suggestion-item';
        button.setAttribute('role', 'option');
        const icon = document.createElement('i');
        icon.className = 'fa-solid fa-location-dot';
        icon.setAttribute('aria-hidden', 'true');
        const text = document.createElement('span');
        text.className = 'address-suggestion-text';
        const main = document.createElement('strong');
        main.textContent = candidate.address;
        const meta = document.createElement('small');
        meta.textContent = [candidate.sido, candidate.sigungu].filter(Boolean).join(' · ');
        text.append(main, meta);
        button.append(icon, text);
        button.addEventListener('click', function () { selectAddress(candidate); });
        addressSuggestions.appendChild(button);
      });
    }
    addressSuggestions.hidden = false;
    addressSearch.setAttribute('aria-expanded', 'true');
  }

  async function searchAddress(query) {
    const normalized = String(query || '').trim().replace(/\s+/g, ' ');
    if (normalized.length < 3) {
      closeSuggestions();
      geocodeStatus.textContent = normalized ? '주소를 3자 이상 입력해 주세요.' : '';
      geocodeStatus.className = 'geocode-status';
      return;
    }

    const sequence = ++searchSequence;
    addressSearchSpinner.classList.add('is-active');
    geocodeStatus.textContent = '주소를 검색하고 있습니다...';
    geocodeStatus.className = 'geocode-status';
    try {
      const response = await fetch('/api/safety/geocode?query=' + encodeURIComponent(normalized), { credentials: 'same-origin' });
      const payload = await readResponse(response);
      if (sequence !== searchSequence) return;
      if (!response.ok) throw new Error(payload.message || '주소 검색에 실패했습니다.');

      const rawCandidates = Array.isArray(payload.candidates) && payload.candidates.length
        ? payload.candidates
        : (payload.location ? [payload.location] : []);
      const seen = new Set();
      const candidates = rawCandidates.map(normalizeCandidate).filter(function (candidate) {
        if (!candidate || seen.has(candidate.address)) return false;
        seen.add(candidate.address);
        return true;
      }).slice(0, 8);

      renderSuggestions(candidates);
      geocodeStatus.textContent = candidates.length
        ? '검색 결과에서 정확한 주소를 선택해 주세요.'
        : '검색 결과가 없습니다.';
      geocodeStatus.className = candidates.length ? 'geocode-status' : 'geocode-status is-error';
    } catch (error) {
      if (sequence !== searchSequence) return;
      closeSuggestions();
      geocodeStatus.textContent = error && error.message ? error.message : '주소 검색 중 오류가 발생했습니다.';
      geocodeStatus.className = 'geocode-status is-error';
    } finally {
      if (sequence === searchSequence) addressSearchSpinner.classList.remove('is-active');
    }
  }

  function scheduleAddressSearch() {
    const value = addressSearch.value;
    if (value !== selectedAddressText) clearSelectedAddress();
    clearFieldError(addressSearch);
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(function () { searchAddress(value); }, 350);
  }

  function formatPhoneNumber(value) {
    let digits = String(value || '').replace(/\D/g, '');
    if (digits.startsWith('02')) {
      digits = digits.slice(0, 10);
      if (digits.length <= 2) return digits;
      if (digits.length <= 5) return digits.slice(0, 2) + '-' + digits.slice(2);
      if (digits.length <= 9) return digits.slice(0, 2) + '-' + digits.slice(2, 5) + '-' + digits.slice(5);
      return digits.slice(0, 2) + '-' + digits.slice(2, 6) + '-' + digits.slice(6);
    }
    digits = digits.slice(0, 11);
    if (digits.length <= 3) return digits;
    if (digits.length <= 6) return digits.slice(0, 3) + '-' + digits.slice(3);
    if (digits.length <= 10) return digits.slice(0, 3) + '-' + digits.slice(3, 6) + '-' + digits.slice(6);
    return digits.slice(0, 3) + '-' + digits.slice(3, 7) + '-' + digits.slice(7);
  }

  input.addEventListener('change', showFiles);
  addressSearch.addEventListener('input', scheduleAddressSearch);
  addressSearch.addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      window.clearTimeout(searchTimer);
      searchAddress(addressSearch.value);
    }
    if (event.key === 'Escape') closeSuggestions();
  });
  addressSearch.addEventListener('focus', function () {
    if (addressSuggestions.children.length) {
      addressSuggestions.hidden = false;
      addressSearch.setAttribute('aria-expanded', 'true');
    }
  });
  document.addEventListener('click', function (event) {
    if (!event.target.closest('.address-search-field')) closeSuggestions();
  });

  contactInput.addEventListener('input', function () {
    const formatted = formatPhoneNumber(contactInput.value);
    if (contactInput.value !== formatted) contactInput.value = formatted;
    clearFieldError(contactInput);
  });

  form.querySelectorAll('input, select, textarea').forEach(function (field) {
    if (field === addressSearch || field === contactInput) return;
    field.addEventListener('input', function () { if (field.type !== 'file') clearFieldError(field); });
    field.addEventListener('change', function () { if (field.type !== 'file') clearFieldError(field); });
  });

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (submitButton && submitButton.disabled) return;

    setMessage('입력 내용을 확인하고 있습니다...');
    setSubmitting(true, '입력 확인 중...');

    if (!validateForm()) {
      setMessage('입력되지 않았거나 올바르지 않은 항목이 있습니다. 빨간 안내를 확인해 주세요.', 'error');
      setSubmitting(false);
      return;
    }

    try {
      if (window.ZipaiAuth) {
        try { await window.ZipaiAuth.ready; }
        catch (_) { throw new Error('로그인 상태 확인에 실패했습니다. 새로고침 후 다시 시도해 주세요.'); }
        if (!window.ZipaiAuth.getUser()) {
          setMessage('매물 등록은 로그인 후 이용할 수 있습니다. 로그인 화면으로 이동합니다.', 'error');
          setSubmitting(false, '로그인 필요');
          window.setTimeout(function () { location.href = '/member/login'; }, 700);
          return;
        }
      }

      const data = new FormData(form);
      const detail = String(detailAddress.value || '').trim();
      data.set('address', addressInput.value + (detail ? ' ' + detail : ''));
      data.set('contact', formatPhoneNumber(contactInput.value));

      const isEditing = editingId !== null;
      setMessage(isEditing ? '수정 내용을 저장하고 있습니다.' : '사진을 포함해 매물을 등록하고 있습니다. 잠시만 기다려 주세요.');
      setSubmitting(true, isEditing ? '수정 중...' : '등록 중...');

      const target = isEditing ? '/api/properties/' + encodeURIComponent(editingId) : '/api/properties';
      const response = await fetch(target, { method: isEditing ? 'PUT' : 'POST', credentials: 'same-origin', body: data });
      const payload = await readResponse(response);
      if (!response.ok) throw new Error(payload.message || ('매물 ' + (isEditing ? '수정' : '등록') + '에 실패했습니다. (HTTP ' + response.status + ')'));

      resetEditMode();
      setMessage(isEditing ? '매물 수정이 완료되었습니다.' : '매물이 등록되었습니다. 주소·지역·지도 좌표와 사진이 함께 저장되었습니다.', 'success');
      setSubmitting(true, isEditing ? '수정 완료' : '등록 완료');
      await loadMyListings();
      window.setTimeout(function () { setSubmitting(false); }, 1200);
    } catch (error) {
      setMessage(error && error.message ? error.message : '매물 저장 중 오류가 발생했습니다.', 'error');
      setSubmitting(false, editingId !== null ? '다시 수정하기' : '다시 등록하기');
    }
  });

  if (refreshMyListings) refreshMyListings.addEventListener('click', loadMyListings);
  if (statusFilter) statusFilter.addEventListener('change', loadMyListings);
  if (cancelEditButton) cancelEditButton.addEventListener('click', resetEditMode);
  if (closeDetail) closeDetail.addEventListener('click', function () { detailDialog.close(); });
  if (detailDialog) detailDialog.addEventListener('click', function (event) {
    if (event.target === detailDialog) detailDialog.close();
  });
  loadMyListings();
})();
