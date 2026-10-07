(async function () {
  'use strict';

  const auth = window.ZipaiAuth;
  const deniedView = document.querySelector('[data-admin-view="denied"]');
  const adminView = document.querySelector('[data-admin-view="dashboard"]');
  const switchButton = document.getElementById('adminSwitchAccount');
  const logoutButton = document.getElementById('adminLogout');
  if (!auth || !deniedView || !adminView || !switchButton || !logoutButton) return;

  await auth.ready;
  const user = auth.getUser();
  const isAdmin = Boolean(user && String(user.role || '').toLowerCase() === 'admin');
  const isGuestDemo = Boolean(auth.isGuest && auth.isGuest(user) && window.location.pathname === '/portfolio/admin');
  const canView = isAdmin || isGuestDemo;
  deniedView.hidden = canView;
  adminView.hidden = !canView;
  switchButton.addEventListener('click', logout);
  logoutButton.addEventListener('click', logout);
  async function logout() {
    await auth.logout();
    window.location.href = auth.resolvePage('login.html');
  }
  if (!canView) return;

  const adminApiBase = isGuestDemo ? '/api/portfolio/admin' : '/api/admin';
  const guestBanner = document.getElementById('adminGuestBanner');
  if (guestBanner) guestBanner.hidden = !isGuestDemo;

  const searchInput = document.getElementById('adminSearch');
  const categoryFilter = document.getElementById('adminCategoryFilter');
  const statusFilter = document.getElementById('adminStatusFilter');
  const list = document.getElementById('adminInquiryList');
  const empty = document.getElementById('adminEmpty');
  const resultCount = document.getElementById('adminResultCount');
  const detailEmpty = document.getElementById('adminDetailEmpty');
  const detailContent = document.getElementById('adminDetailContent');
  const statusSelect = document.getElementById('adminDetailStatus');
  const replyMessage = document.getElementById('adminReplyMessage');
  const statusLabels = { received: '접수', in_progress: '확인 중', answered: '답변 완료' };
  const propertyStatusLabels = {
    active: '게시 중', pending: '검토 대기', review: '검토 중', received: '접수',
    rejected: '거절', closed: '종료', inactive: '비공개'
  };
  const inquiryPrev = document.getElementById('adminInquiryPrev');
  const inquiryNext = document.getElementById('adminInquiryNext');
  const inquiryPage = document.getElementById('adminInquiryPage');
  const adminToast = document.getElementById('adminToast');
  const pageSize = 8;
  let selectedId = null;
  let items = [];
  let currentPage = 1;
  let pendingPropertyCount = 0;
  let postItems = [];
  let visitItems = [];
  const adminUserId = document.getElementById('adminUserId');
  if (adminUserId) adminUserId.textContent = isGuestDemo ? '포트폴리오 게스트' : (user.id || '');

  function showToast(message) {
    if (!adminToast) return;
    adminToast.textContent = message;
    adminToast.classList.add('is-visible');
    clearTimeout(window.__adminToastTimer);
    window.__adminToastTimer = window.setTimeout(function () {
      adminToast.classList.remove('is-visible');
    }, 2200);
  }

  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = String(value);
  }

  async function api(path, options) {
    const method = String(options && options.method || 'GET').toUpperCase();
    if (isGuestDemo && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      showToast('게스트 모드에서는 조회만 가능합니다.');
      throw new Error('게스트 모드에서는 조회만 가능합니다.');
    }
    const response = await fetch(path, {
      credentials: 'same-origin',
      headers: options && options.body ? { 'Content-Type': 'application/json' } : {},
      ...options
    });
    const payload = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(payload.message || '요청을 처리하지 못했습니다.');
    return payload;
  }

  function formatDate(value, withTime) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return new Intl.DateTimeFormat('ko-KR', withTime ? { dateStyle: 'medium', timeStyle: 'short' } : { dateStyle: 'medium' }).format(date);
  }

  function filteredItems() {
    const query = searchInput.value.trim().toLowerCase();
    return items.filter(function (item) {
      if (categoryFilter.value && item.category !== categoryFilter.value) return false;
      if (statusFilter.value && item.status !== statusFilter.value) return false;
      return !query || [item.title, item.email, item.message, item.username].join(' ').toLowerCase().includes(query);
    });
  }

  function updateStats() {
    const newInquiryCount = items.filter(function (item) { return item.status === 'received'; }).length;
    const unansweredCount = items.filter(function (item) { return item.status !== 'answered'; }).length;
    setText('adminNewInquiries', newInquiryCount);
    setText('adminUnanswered', unansweredCount);
    setText('adminPendingProperties', pendingPropertyCount);
  }

  async function loadSummary() {
    try {
      const payload = await api(adminApiBase + '/summary');
      setText('adminNewMembers', payload.newMembers || 0);
      setText('adminActiveMembers', payload.activeMembers || 0);
      setText('adminNewInquiries', payload.newInquiries || 0);
      setText('adminUnanswered', payload.unansweredInquiries || 0);
      setText('adminPendingProperties', payload.pendingProperties || 0);
      setText('adminPendingVisits', payload.pendingVisits || 0);
    } catch (error) {
      showToast(error.message);
    }
  }

  function renderDetail() {
    const item = items.find(function (entry) { return String(entry.id) === String(selectedId); });
    detailEmpty.hidden = Boolean(item);
    detailContent.hidden = !item;
    if (!item) return;
    document.getElementById('adminDetailCategory').textContent = item.category;
    document.getElementById('adminDetailDate').textContent = formatDate(item.createdAt, true);
    document.getElementById('adminDetailDate').dateTime = item.createdAt;
    document.getElementById('adminDetailTitle').textContent = item.title;
    document.getElementById('adminDetailEmail').textContent = item.email;
    document.getElementById('adminDetailMessage').textContent = item.message;
    replyMessage.value = item.answer || '';
    statusSelect.value = item.status;
  }

  function render() {
    const visible = filteredItems();
    const selectedVisible = visible.some(function (item) { return String(item.id) === String(selectedId); });
    if (!selectedVisible) selectedId = visible.length ? Number(visible[0].id) : null;
    const totalPages = Math.max(1, Math.ceil(visible.length / pageSize));
    if (currentPage > totalPages) currentPage = totalPages;
    if (currentPage < 1) currentPage = 1;
    const start = (currentPage - 1) * pageSize;
    const pageItems = visible.slice(start, start + pageSize);

    updateStats();
    list.replaceChildren();
    resultCount.textContent = visible.length + '건';
    empty.hidden = visible.length > 0;
    list.hidden = visible.length === 0;
    if (inquiryPage) inquiryPage.textContent = currentPage + ' / ' + totalPages;
    if (inquiryPrev) inquiryPrev.disabled = currentPage <= 1;
    if (inquiryNext) inquiryNext.disabled = currentPage >= totalPages;

    pageItems.forEach(function (item) {
      const button = document.createElement('button');
      const category = document.createElement('span');
      const copy = document.createElement('span');
      const title = document.createElement('strong');
      const meta = document.createElement('small');
      const status = document.createElement('span');
      button.type = 'button';
      button.className = 'admin-inquiry' + (String(item.id) === String(selectedId) ? ' is-active' : '');
      button.dataset.id = item.id;
      category.className = 'admin-inquiry-category';
      copy.className = 'admin-inquiry-copy';
      status.className = 'admin-status';
      category.textContent = item.category;
      title.textContent = item.title;
      meta.textContent = (item.username || item.email) + ' · ' + formatDate(item.createdAt, false);
      status.textContent = statusLabels[item.status] || item.status;
      status.dataset.status = item.status;
      copy.append(title, meta);
      button.append(category, copy, status);
      list.appendChild(button);
    });
    renderDetail();
  }

  async function load() {
    try {
      const payload = await api(adminApiBase + '/inquiries');
      items = payload.items || [];
      if (selectedId == null && items.length) selectedId = Number(items[0].id);
      render();
    } catch (error) {
      window.alert(error.message);
    }
  }

  async function loadProperties() {
    const target = document.getElementById('adminPropertyList');
    if (!target) return;
    try {
      const payload = await api(adminApiBase + '/properties');
      const properties = Array.isArray(payload.items) ? payload.items : [];
      setText('adminPropertyCount', properties.length + '건');
      pendingPropertyCount = properties.filter(function (item) {
        return ['pending', 'review', 'received'].includes(String(item.status || '').toLowerCase());
      }).length;
      updateStats();
      target.replaceChildren();
      properties.forEach(function (item) {
        const row = document.createElement('article');
        const copy = document.createElement('span');
        const title = document.createElement('strong');
        const meta = document.createElement('small');
        const actions = document.createElement('span');
        row.className = 'admin-management-row';
        copy.className = 'admin-management-copy';
        actions.className = 'admin-row-actions';
        title.textContent = item.title || '제목 없는 매물';
        const currentStatus = String(item.status || '').toLowerCase();
        meta.textContent = (item.owner || '소유자 정보 없음') + ' · ' + item.address + ' · 상태 ' + (propertyStatusLabels[currentStatus] || item.status);
        copy.append(title, meta);
        ['approved', 'rejected', 'closed'].forEach(function (status) {
          const button = document.createElement('button');
          button.type = 'button';
          button.textContent = status === 'approved' ? '승인' : status === 'rejected' ? '거절' : '종료';
          button.disabled = (status === 'approved' && currentStatus === 'active') || currentStatus === status;
          if (button.disabled) button.title = '현재 상태입니다.';
          button.addEventListener('click', async function () {
            button.disabled = true;
            try {
              await api(adminApiBase + '/properties/' + item.id + '/status', {
                method: 'PATCH', body: JSON.stringify({ status: status })
              });
              showToast('매물 상태를 변경했습니다.');
              await Promise.all([loadProperties(), loadAudit(), loadSummary()]);
            } catch (error) {
              window.alert(error.message);
              button.disabled = false;
            }
          });
          actions.appendChild(button);
        });
        row.append(copy, actions);
        target.appendChild(row);
      });
      if (!target.children.length) target.innerHTML = '<p class="admin-list-message">등록된 사용자 매물이 없습니다.</p>';
    } catch (error) {
      target.textContent = error.message;
    }
  }

  async function loadPosts() {
    const target = document.getElementById('adminPostList');
    if (!target) return;
    try {
      const payload = await api(adminApiBase + '/community/posts');
      postItems = payload.items || [];
      setText('adminPostCount', postItems.length + '건');
      renderPosts();
    } catch (error) {
      target.textContent = error.message;
    }
  }

  function renderPosts() {
    const target = document.getElementById('adminPostList');
    const input = document.getElementById('adminPostSearch');
    if (!target) return;
    const query = input ? input.value.trim().toLowerCase() : '';
    const visible = postItems.filter(function (item) {
      return !query || [item.title, item.username, item.category, item.area].join(' ').toLowerCase().includes(query);
    });
    setText('adminPostCount', visible.length + '건');
    target.replaceChildren();
    visible.forEach(function (item) {
        const row = document.createElement('article');
        const copy = document.createElement('span');
        const title = document.createElement('strong');
        const meta = document.createElement('small');
        const remove = document.createElement('button');
        row.className = 'admin-management-row';
        copy.className = 'admin-management-copy';
        const actions = document.createElement('span');
        actions.className = 'admin-row-actions';
        title.textContent = item.title;
        meta.textContent = item.username + ' · ' + item.category + ' · 조회 ' + item.views + ' · ' + formatDate(item.createdAt, false);
        remove.type = 'button';
        remove.textContent = '삭제';
        remove.className = 'danger';
        remove.addEventListener('click', async function () {
          if (!window.confirm('이 게시글을 삭제할까요?')) return;
          remove.disabled = true;
          try {
            await api(adminApiBase + '/community/posts/' + item.id, { method: 'DELETE' });
            showToast('게시글을 삭제했습니다.');
            await Promise.all([loadPosts(), loadAudit()]);
          } catch (error) {
            window.alert(error.message);
            remove.disabled = false;
          }
        });
        copy.append(title, meta);
        actions.appendChild(remove);
        row.append(copy, actions);
        target.appendChild(row);
      });
    if (!target.children.length) target.innerHTML = '<p class="admin-list-message">조건에 맞는 게시글이 없습니다.</p>';
  }

  const visitLabels = {
    pending: '승인 대기', reschedule_requested: '일정 변경 대기', approved: '예약 확정', rejected: '거절',
    completed: '방문 완료', no_show: '노쇼', cancelled_by_user: '사용자 취소', cancelled_by_admin: '관리자 취소'
  };

  async function loadVisits() {
    const target = document.getElementById('adminVisitList');
    if (!target) return;
    try {
      const payload = await api(adminApiBase + '/visits');
      visitItems = payload.items || [];
      setText('adminVisitCount', visitItems.length + '건');
      renderVisits();
    } catch (error) {
      target.textContent = error.message;
    }
  }

  function renderVisits() {
    const target = document.getElementById('adminVisitList');
    const filter = document.getElementById('adminVisitStatus');
    if (!target) return;
    const visible = visitItems.filter(function (item) { return !filter || !filter.value || item.status === filter.value; });
    setText('adminVisitCount', visible.length + '건');
    target.replaceChildren();
    visible.forEach(function (item) {
      const row = document.createElement('article');
      const copy = document.createElement('div');
      const title = document.createElement('strong');
      const meta = document.createElement('small');
      const actions = document.createElement('div');
      const select = document.createElement('select');
      const saveButton = document.createElement('button');
      row.className = 'admin-management-row';
      copy.className = 'admin-management-copy';
      actions.className = 'admin-row-actions';
      title.textContent = item.title || item.roomId;
      meta.textContent = item.date + ' ' + String(item.time || '').slice(0, 5) + ' · ' + item.phone + ' · ' + (visitLabels[item.status] || item.status);
      Object.keys(visitLabels).forEach(function (status) {
        const option = document.createElement('option');
        option.value = status;
        option.textContent = visitLabels[status];
        option.selected = status === item.status;
        select.appendChild(option);
      });
      saveButton.type = 'button';
      saveButton.textContent = '상태 저장';
      saveButton.addEventListener('click', async function () {
        saveButton.disabled = true;
        try {
          await api(adminApiBase + '/visits/' + item.id + '/status', {
            method: 'PATCH', body: JSON.stringify({ status: select.value })
          });
          showToast('방문 예약 상태를 변경했습니다.');
          await Promise.all([loadVisits(), loadAudit(), loadSummary()]);
        } catch (error) {
          window.alert(error.message);
          saveButton.disabled = false;
        }
      });
      copy.append(title, meta);
      actions.append(select, saveButton);
      row.append(copy, actions);
      target.appendChild(row);
    });
    if (!target.children.length) target.innerHTML = '<p class="admin-list-message">조건에 맞는 방문 예약이 없습니다.</p>';
  }

  async function loadAudit() {
    const target = document.getElementById('adminAuditList');
    if (!target) return;
    try {
      const payload = await api(adminApiBase + '/audit');
      const auditItems = payload.items || [];
      setText('adminAuditCount', auditItems.length + '건');
      target.replaceChildren();
      auditItems.forEach(function (item) {
        const row = document.createElement('article');
        const copy = document.createElement('div');
        const title = document.createElement('strong');
        const meta = document.createElement('small');
        row.className = 'admin-management-row';
        copy.className = 'admin-management-copy';
        title.innerHTML = '<span class="admin-audit-action"></span><span class="admin-audit-title"></span>';
        title.querySelector('.admin-audit-action').textContent = item.action;
        title.querySelector('.admin-audit-title').textContent = item.targetType + (item.targetId ? ' #' + item.targetId : '');
        meta.textContent = item.admin + ' · ' + (item.details || '상세 내용 없음') + ' · ' + formatDate(item.createdAt, true);
        copy.append(title, meta);
        row.appendChild(copy);
        target.appendChild(row);
      });
      if (!target.children.length) target.innerHTML = '<p class="admin-list-message">기록된 관리자 작업이 없습니다.</p>';
    } catch (error) {
      target.textContent = error.message;
    }
  }

  async function loadFinanceUpdates() {
    const target = document.getElementById('adminFinanceUpdates');
    if (!target) return;
    if (isGuestDemo) {
      setText('adminFinanceCount', '0건');
      target.textContent = '게스트 데모에서는 실제 금융정책 검토 데이터에 접근하지 않습니다.';
      return;
    }
    try {
      const payload = await api('/api/finance/policy-updates');
      const candidates = (payload.items || []).filter(function (item) { return item.status === 'pending'; });
      setText('adminFinanceCount', candidates.length + '건');
      target.replaceChildren();
      candidates.forEach(function (item) {
        const card = document.createElement('article');
        card.className = 'admin-finance-card';
        card.innerHTML = '<header><div><strong></strong><small></small></div><a target="_blank" rel="noopener noreferrer">공식 원문 열기</a></header>'
          + '<p class="admin-finance-snapshot"></p>'
          + '<div class="admin-finance-fields">'
          + '<label>한도<input data-field="limitInfo"></label>'
          + '<label>금리<input data-field="rateInfo"></label>'
          + '<label>설명<textarea data-field="description"></textarea></label></div>'
          + '<div class="admin-finance-actions"><button type="button" data-action="reject">변경 없음·거절</button><button type="button" data-action="approve">정책 수정 후 승인</button></div>';
        card.querySelector('strong').textContent = item.policyName;
        card.querySelector('small').textContent = item.sourceName + ' · ' + formatDate(item.detectedAt, true);
        card.querySelector('a').href = item.sourceUrl;
        card.querySelector('.admin-finance-snapshot').textContent = item.snapshotExcerpt;
        card.querySelector('[data-field="limitInfo"]').value = item.limitInfo || '';
        card.querySelector('[data-field="rateInfo"]').value = item.rateInfo || '';
        card.querySelector('[data-field="description"]').value = item.description || '';
        card.addEventListener('click', async function (event) {
          const action = event.target.closest('[data-action]');
          if (!action) return;
          action.disabled = true;
          try {
            if (action.dataset.action === 'approve') {
              await api('/api/finance/policies/' + item.policyId, {
                method: 'PUT',
                body: JSON.stringify({
                  category: item.category,
                  name: item.policyName,
                  targetType: item.targetType,
                  limitInfo: card.querySelector('[data-field="limitInfo"]').value.trim(),
                  rateInfo: card.querySelector('[data-field="rateInfo"]').value.trim(),
                  description: card.querySelector('[data-field="description"]').value.trim()
                })
              });
            }
            await api('/api/finance/policy-updates/' + item.id + '/review', {
              method: 'POST', body: JSON.stringify({ decision: action.dataset.action === 'approve' ? 'approved' : 'rejected' })
            });
            await Promise.all([loadFinanceUpdates(), loadAudit()]);
          } catch (error) {
            window.alert(error.message);
            action.disabled = false;
          }
        });
        target.appendChild(card);
      });
      if (!candidates.length) target.textContent = '검토 대기 중인 금융정책 변경이 없습니다.';
    } catch (error) {
      target.textContent = error.message;
    }
  }

  async function save(status, answer) {
    if (!selectedId) return;
    const saveStatusButton = document.getElementById('adminSaveStatus');
    const saveReplyButton = document.getElementById('adminSaveReply');
    const activeButton = status === 'answered' ? saveReplyButton : saveStatusButton;
    const originalLabel = activeButton ? activeButton.textContent : '';
    if (saveStatusButton) saveStatusButton.disabled = true;
    if (saveReplyButton) saveReplyButton.disabled = true;
    if (activeButton) activeButton.textContent = '저장 중...';
    try {
      await api(adminApiBase + '/inquiries/' + selectedId + '/answer', {
        method: 'PATCH',
        body: JSON.stringify({ status: status, answer: answer })
      });
      await Promise.all([load(), loadAudit(), loadSummary()]);
      showToast(status === 'answered' ? '답변을 저장하고 회원에게 알림을 보냈습니다.' : '처리 상태를 저장했습니다.');
    } catch (error) {
      window.alert(error.message);
    } finally {
      if (saveStatusButton) saveStatusButton.disabled = false;
      if (saveReplyButton) saveReplyButton.disabled = false;
      if (activeButton) activeButton.textContent = originalLabel;
    }
  }

  list.addEventListener('click', function (event) {
    const button = event.target.closest('[data-id]');
    if (!button) return;
    selectedId = Number(button.dataset.id);
    render();
  });
  [searchInput, categoryFilter, statusFilter].forEach(function (control) {
    if (!control) return;
    control.addEventListener(control.tagName === 'INPUT' ? 'input' : 'change', function () {
      currentPage = 1;
      render();
    });
  });
  if (inquiryPrev) inquiryPrev.addEventListener('click', function () {
    if (currentPage > 1) {
      currentPage -= 1;
      render();
    }
  });
  if (inquiryNext) inquiryNext.addEventListener('click', function () {
    const totalPages = Math.max(1, Math.ceil(filteredItems().length / pageSize));
    if (currentPage < totalPages) {
      currentPage += 1;
      render();
    }
  });
  const saveStatusButton = document.getElementById('adminSaveStatus');
  if (saveStatusButton) saveStatusButton.addEventListener('click', function () {
    save(statusSelect.value, replyMessage.value.trim());
  });
  const saveReplyButton = document.getElementById('adminSaveReply');
  if (saveReplyButton) saveReplyButton.addEventListener('click', function () {
    const answer = replyMessage.value.trim();
    if (!answer) {
      replyMessage.focus();
      return;
    }
    save('answered', answer);
  });
  const deleteInquiryButton = document.getElementById('adminDeleteInquiry');
  if (deleteInquiryButton) deleteInquiryButton.hidden = true;
  const postSearch = document.getElementById('adminPostSearch');
  if (postSearch) postSearch.addEventListener('input', renderPosts);
  const visitStatus = document.getElementById('adminVisitStatus');
  if (visitStatus) visitStatus.addEventListener('change', renderVisits);
  document.querySelectorAll('[data-collapse-target]').forEach(function (button) {
    button.addEventListener('click', function () {
      const body = document.getElementById(button.dataset.collapseTarget);
      if (!body) return;
      const willCollapse = !body.hidden;
      body.hidden = willCollapse;
      button.setAttribute('aria-expanded', String(!willCollapse));
      button.textContent = willCollapse ? '펼치기' : '접기';
    });
  });
  await Promise.all([load(), loadSummary(), loadProperties(), loadVisits(), loadPosts(), loadFinanceUpdates(), loadAudit()]);
})();
