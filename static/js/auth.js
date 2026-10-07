(function () {
  'use strict';

  const STORAGE_KEY = 'zipaiDemoUser';
  const PAGE_PATHS = {
    'index.html': '/',
    'finance-policy.html': '/board/finance-policy',
    'trend1.html': '/board/trend1',
    'trend2.html': '/board/trend2',
    'trend3.html': '/board/trend3',
    'trend4.html': '/board/trend4',
    'safety.html': '/safe/safety',
    'happy-housing.html': '/board/happy-housing',
    'customer-center.html': '/board/customer-center',
    'community.html': '/board/community',
    'community-detail.html': '/board/community-detail',
    'admin.html': '/admin',
    'login.html': '/member/login',
    'signup.html': '/member/signup',
    'social-profile.html': '/member/social-profile',
    'mypage.html': '/member/mypage',
    'lifestyle-analysis.html': '/ai/lifestyle-analysis',
    'sub33.html': '/defense/result',
    'fraud_result.html': '/defense/result',
    'checklist.html': '/defense/checklist',
    'jeonse-calculator.html': '/defense/calculator',
    'charter-rate-calculator.html': '/defense/calculator',
    'contract-guide.html': '/defense/guide'
  };
  const PAGE_ALIASES = {
    'sub33.html': 'fraud_result.html',
    'jeonse-calculator.html': 'charter-rate-calculator.html'
  };
  let memoryUser = null;
  let authReady;
  const nativeFetch = window.fetch.bind(window);
  const GUEST_READ_ONLY_POSTS = new Set([
    '/api/lifestyle/recommend',
    '/api/lifestyle/recommend/ml',
    '/api/happy-housing/diagnose',
    '/api/finance/calculate',
    '/api/chat'
  ]);
  const GUEST_AUTH_POSTS = new Set([
    '/api/auth/guest', '/api/auth/logout'
  ]);

  function getRootPrefix() {
    return window.location.pathname.replace(/\\/g, '/').includes('/templates/') ? '../../' : '';
  }

  function getPageName(href) {
    const path = String(href || '').split('#')[0].split('?')[0].replace(/\\/g, '/');
    const page = (path.split('/').pop() || '').toLowerCase();
    return PAGE_ALIASES[page] || page;
  }

  function resolvePage(href) {
    const value = String(href || '');
    if (!value || value === '#' || value.charAt(0) === '#' || /^[a-z][a-z0-9+.-]*:/i.test(value)) return value;
    const parts = value.split('#');
    const cleanPath = parts[0].replace(/\\/g, '/').replace(/^(\.\.\/)+/, '').replace(/^\.\//, '').toLowerCase();
    const pageName = getPageName(cleanPath);
    const target = PAGE_PATHS[cleanPath] || PAGE_PATHS[pageName] || parts[0];
    if (String(target).startsWith('/')) return target + (parts[1] ? '#' + parts[1] : '');
    return getRootPrefix() + target + (parts[1] ? '#' + parts[1] : '');
  }

  function getUser() {
    try {
      const sessionUser = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null');
      if (sessionUser && sessionUser.id) return sessionUser;
      const persistedUser = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
      if (persistedUser && persistedUser.id) {
        memoryUser = persistedUser;
        try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(persistedUser)); } catch (error) { /* 현재 탭에서만 복원 */ }
        return persistedUser;
      }
      return memoryUser;
    } catch (error) {
      return memoryUser;
    }
  }

  function isGuest(user) {
    const current = user || getUser();
    return Boolean(current && (current.guest === true || String(current.role || '').toLowerCase() === 'guest'));
  }

  function showGuestReadOnlyMessage() {
    window.alert('게스트 모드에서는 조회만 가능합니다.');
  }

  window.fetch = function guardedFetch(input, init) {
    const method = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
    if (!isGuest() || ['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      return nativeFetch(input, init);
    }
    const rawUrl = typeof input === 'string' ? input : input && input.url;
    const path = new URL(rawUrl || '', window.location.origin).pathname;
    if (GUEST_AUTH_POSTS.has(path) || (method === 'POST' && GUEST_READ_ONLY_POSTS.has(path))) {
      return nativeFetch(input, init);
    }
    showGuestReadOnlyMessage();
    return Promise.reject(new Error('게스트 모드에서는 조회만 가능합니다.'));
  };

  function storeUser(user) {
    const normalized = { ...user, loginAt: user.loginAt || new Date().toISOString() };
    memoryUser = normalized;
    try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(normalized)); } catch (error) { /* 현재 페이지에서만 유지될 수 있음 */ }
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(normalized)); } catch (error) { /* 현재 탭 로그인은 유지 */ }
    return normalized;
  }

  function clearUser() {
    memoryUser = null;
    try { sessionStorage.removeItem(STORAGE_KEY); } catch (error) { /* 이미 로그아웃된 상태로 처리 */ }
    try { localStorage.removeItem(STORAGE_KEY); } catch (error) { /* 이미 로그아웃된 상태로 처리 */ }
  }

  async function requestAuth(path, options) {
    let response;
    try {
      response = await fetch('/api/auth/' + path, {
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        ...options
      });
    } catch (error) {
      throw new Error('인증 서버에 연결할 수 없습니다. Spring Boot가 실행 중인지 확인해 주세요.');
    }
    const contentType = String(response.headers.get('content-type') || '');
    const payload = contentType.includes('application/json')
      ? await response.json().catch(function () { return {}; })
      : {};
    if (!response.ok) {
      if (!contentType.includes('application/json')) {
        throw new Error('현재 접속 주소(' + window.location.origin + ')에는 인증 API가 없습니다. Spring Boot의 /api/auth 경로를 확인해 주세요.');
      }
      throw new Error(payload.message || '인증 요청을 처리하지 못했습니다. (HTTP ' + response.status + ')');
    }
    return payload;
  }

  async function refreshUser() {
    try {
      const payload = await requestAuth('me', { method: 'GET', headers: {} });
      if (payload.authenticated && payload.user) storeUser(payload.user);
      else clearUser();
    } catch (error) {
      /* 서버에 연결할 수 없는 경우 현재 화면의 캐시 상태를 유지합니다. */
    }
    updateLoginButtons();
    return getUser();
  }

  async function login(credentials) {
    const payload = await requestAuth('login', {
      method: 'POST',
      body: JSON.stringify(credentials || {})
    });
    return storeUser(payload.user);
  }

  async function guestLogin() {
    const payload = await requestAuth('guest', { method: 'POST', body: '{}' });
    return storeUser(payload.user);
  }

  async function signup(data) {
    const payload = await requestAuth('signup', {
      method: 'POST',
      body: JSON.stringify(data || {})
    });
    return storeUser(payload.user);
  }

  async function logout() {
    try {
      await requestAuth('logout', { method: 'POST', body: '{}' });
    } finally {
      clearUser();
      updateLoginButtons();
    }
  }

  function updateLoginButtons() {
    const user = getUser();
    document.querySelectorAll('.login-button').forEach(function (button) {
      const icon = document.createElement('i');
      const label = document.createElement('span');
      button.href = resolvePage(user ? 'mypage.html' : 'login.html');
      button.classList.toggle('is-authenticated', Boolean(user));
      button.classList.toggle('is-guest', isGuest(user));
      button.setAttribute('aria-label', user ? user.id + ' 계정 페이지로 이동' : '로그인 페이지로 이동');
      icon.className = 'fa-solid ' + (user ? 'fa-user-check' : 'fa-user');
      icon.setAttribute('aria-hidden', 'true');
      label.className = 'auth-user-label';
      label.textContent = isGuest(user) ? '게스트 모드' : user ? user.id + '님' : '로그인';
      button.replaceChildren(icon, label);
    });
    document.querySelectorAll('.listing-button').forEach(function (button) {
      button.href = resolvePage('index.html#register-listing');
      button.setAttribute('aria-label', user ? '매물 등록 화면 열기' : '회원 전용 매물 등록 안내 열기');
    });

    document.querySelectorAll('.utility-signup-button').forEach(function (button) {
      button.href = resolvePage('signup.html');
      const shouldHideSignup = Boolean(user);
      button.hidden = shouldHideSignup;
      button.setAttribute('aria-hidden', String(shouldHideSignup));
    });

    document.querySelectorAll('.utility-links').forEach(function (container) {
      let mypageLink = container.querySelector('.utility-mypage');
      let adminLink = container.querySelector('.utility-admin');
      let notificationLink = container.querySelector('.utility-notifications');
      let logoutButton = container.querySelector('.utility-logout');
      if (!mypageLink) {
        mypageLink = document.createElement('a');
        mypageLink.className = 'utility-mypage';
        mypageLink.href = resolvePage('mypage.html');
        mypageLink.innerHTML = '<i class="fa-solid fa-user-gear" aria-hidden="true"></i><span>마이페이지</span>';
        container.appendChild(mypageLink);
      }
      if (!adminLink) {
        adminLink = document.createElement('a');
        adminLink.className = 'utility-admin';
      adminLink.href = isGuest(user) ? '/portfolio/admin' : resolvePage('admin.html');
        adminLink.innerHTML = '<i class="fa-solid fa-user-tie" aria-hidden="true"></i><span>관리자</span>';
        container.appendChild(adminLink);
      }
      if (!notificationLink) {
        notificationLink = document.createElement('a');
        notificationLink.className = 'utility-notifications';
        notificationLink.href = resolvePage('mypage.html') + '#notifications';
        notificationLink.innerHTML = '<i class="fa-solid fa-bell" aria-hidden="true"></i><span>알림</span>';
        container.insertBefore(notificationLink, mypageLink);
      }
      if (!logoutButton) {
        logoutButton = document.createElement('button');
        logoutButton.type = 'button';
        logoutButton.className = 'utility-logout';
        logoutButton.innerHTML = '<i class="fa-solid fa-right-from-bracket" aria-hidden="true"></i><span>로그아웃</span>';
        mypageLink.insertAdjacentElement('afterend', logoutButton);
        logoutButton.addEventListener('click', async function () {
          logoutButton.disabled = true;
          try {
            await logout();
            window.location.href = resolvePage('index.html');
          } catch (error) {
            logoutButton.disabled = false;
          }
        });
      }
      mypageLink.hidden = !user;
      mypageLink.setAttribute('aria-hidden', String(!user));
      logoutButton.hidden = !user;
      logoutButton.setAttribute('aria-hidden', String(!user));
      notificationLink.hidden = !user;
      notificationLink.setAttribute('aria-hidden', String(!user));
      const canViewAdmin = Boolean(user && (String(user.role || '').toLowerCase() === 'admin' || isGuest(user)));
      adminLink.hidden = !canViewAdmin;
      adminLink.setAttribute('aria-hidden', String(!canViewAdmin));
      if (user && !isGuest(user)) {
        fetch('/api/notifications', { credentials: 'same-origin' })
          .then(function (response) { return response.ok ? response.json() : null; })
          .then(function (payload) {
            const label = notificationLink.querySelector('span');
            if (label && payload) label.textContent = payload.unreadCount ? '알림 ' + payload.unreadCount : '알림';
          })
          .catch(function () { /* 알림 표시는 부가 기능이므로 헤더 렌더링을 유지 */ });
      }
    });
  }

  function listingGateElement() {
    let gate = document.getElementById('listingMemberGate');
    if (gate) return gate;
    gate = document.createElement('div');
    gate.id = 'listingMemberGate';
    gate.className = 'listing-member-gate';
    gate.hidden = true;
    gate.innerHTML =
      '<section class="listing-member-card" role="dialog" aria-modal="true" aria-labelledby="listingMemberTitle">' +
        '<button class="listing-member-close" type="button" aria-label="안내 닫기">×</button>' +
        '<span class="listing-member-symbol"><i class="fa-solid fa-house-lock" aria-hidden="true"></i></span>' +
        '<small>MEMBERS ONLY</small><h2 id="listingMemberTitle">매물 등록은 회원만 이용할 수 있습니다</h2>' +
        '<p>회원가입 또는 로그인 후 매물 정보를 안전하게 등록하고 관리할 수 있습니다.</p>' +
        '<div class="listing-member-actions">' +
          '<a class="listing-member-primary" href="' + resolvePage('signup.html') + '"><i class="fa-solid fa-user-plus" aria-hidden="true"></i><span><strong>회원가입</strong><small>ZipAI 회원으로 시작하기</small></span></a>' +
          '<a href="' + resolvePage('login.html') + '"><i class="fa-solid fa-right-to-bracket" aria-hidden="true"></i><span><strong>로그인하기</strong><small>기존 계정으로 이용하기</small></span></a>' +
        '</div>' +
      '</section>';
    document.body.appendChild(gate);
    gate.querySelector('.listing-member-close').addEventListener('click', function () {
      gate.hidden = true;
      document.body.classList.remove('listing-gate-open');
    });
    gate.addEventListener('click', function (event) {
      if (event.target === gate) {
        gate.hidden = true;
        document.body.classList.remove('listing-gate-open');
      }
    });
    return gate;
  }

  function showListingMemberGate() {
    const gate = listingGateElement();
    gate.hidden = false;
    document.body.classList.add('listing-gate-open');
    gate.querySelector('.listing-member-close').focus();
  }

  function setupListingAccess() {
    if (document.documentElement.dataset.listingAccessReady === 'true') return;
    document.documentElement.dataset.listingAccessReady = 'true';
    document.addEventListener('click', function (event) {
      const trigger = event.target.closest('.listing-button,[href$="#register-listing"]');
      if (!trigger) return;
      if (isGuest()) {
        event.preventDefault();
        event.stopImmediatePropagation();
        showGuestReadOnlyMessage();
        return;
      }
      if (getUser()) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      showListingMemberGate();
    }, true);
    document.addEventListener('keydown', function (event) {
      if (event.key !== 'Escape') return;
      const gate = document.getElementById('listingMemberGate');
      if (gate && !gate.hidden) {
        gate.hidden = true;
        document.body.classList.remove('listing-gate-open');
      }
    });
    if ((!getUser() || isGuest()) && window.location.hash === '#register-listing') {
      history.replaceState(null, '', window.location.pathname + window.location.search);
      showListingMemberGate();
    }
  }

  function getCurrentPage() {
    const pathname = window.location.pathname.replace(/\\/g, '/').replace(/\/+$/, '') || '/';
    const matched = Object.entries(PAGE_PATHS).find(function (entry) {
      return entry[1] === pathname;
    });
    if (matched) return getPageName(matched[0]);
    return getPageName(pathname) || 'index.html';
  }

  function headerGroupForPath(pathname) {
    const path = String(pathname || '/').replace(/\\/g, '/').replace(/\/+$/, '') || '/';
    if (path === '/' || path === '/index.html') return 'property';
    if (path.startsWith('/defense/')) return 'defense';
    if (path.startsWith('/safe/')) return 'safe';
    if (path === '/board/finance-policy' || /^\/board\/trend[1-4]$/.test(path)) return 'finance';
    if (path === '/board/happy-housing') return 'happy';
    if (path === '/ai/lifestyle-analysis' || path === '/ai/lifestyle-analysis.html') return 'lifestyle';
    return '';
  }

  function setupHeaderActiveState() {
    const header = document.querySelector('.zipai-header');
    if (!header) return;
    const activeGroup = headerGroupForPath(window.location.pathname);

    header.querySelectorAll('.menu-item').forEach(function (item) {
      const primary = item.querySelector('.menu-primary');
      const active = item.dataset.menuGroup === activeGroup;
      item.classList.toggle('active', active);
      if (!primary) return;
      primary.classList.toggle('active', active);
      if (active) primary.setAttribute('aria-current', 'page');
      else primary.removeAttribute('aria-current');
    });
  }

  function setupPendingHeaderLinks() {
    /* 공통 2단 메뉴는 실제 Spring Route만 사용하므로 pending 링크를 만들지 않는다. */
  }

  function setupFraudSubnav() {
    /* 사기방지 하위 메뉴는 templates/common/header.html의 공통 2단 메뉴로 통합했다. */
  }

  function refreshHeaderUi() {
    updateLoginButtons();
    setupPendingHeaderLinks();
    setupHeaderActiveState();
    setupFraudSubnav();
  }

  window.ZipaiAuth = {
    getUser: getUser,
    login: login,
    guestLogin: guestLogin,
    signup: signup,
    logout: logout,
    isGuest: isGuest,
    canMutate: function () { return Boolean(getUser()) && !isGuest(); },
    showGuestReadOnlyMessage: showGuestReadOnlyMessage,
    refreshUser: refreshUser,
    get ready() { return authReady; },
    updateLoginButtons: updateLoginButtons,
    refreshHeaderUi: refreshHeaderUi,
    resolvePage: resolvePage
  };

  function initAuthUi() {
    setupListingAccess();
    refreshHeaderUi();
  }

  authReady = refreshUser();
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initAuthUi);
  else initAuthUi();
})();
