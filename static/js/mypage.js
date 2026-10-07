(async function () {
  'use strict';

  const auth = window.ZipaiAuth;
  const guestView = document.querySelector('[data-mypage-view="guest"]');
  const memberView = document.querySelector('[data-mypage-view="member"]');
  const userId = document.getElementById('mypageUserId');
  const loginTime = document.getElementById('mypageLoginTime');
  const favoriteCount = document.getElementById('mypageFavoriteCount');
  const favoriteCountText = document.getElementById('mypageFavoriteCountText');
  const unreadCount = document.getElementById('mypageUnreadCount');
  const inquiryCount = document.getElementById('mypageInquiryCount');
  const inquiries = document.getElementById('mypageInquiries');
  const logoutButton = document.getElementById('mypageLogout');
  const diagnosis = document.getElementById('mypageDiagnosis');
  const notifications = document.getElementById('mypageNotifications');
  const readAllNotifications = document.getElementById('mypageReadAllNotifications');

  if (!auth || !guestView || !memberView || !userId || !loginTime || !favoriteCount || !favoriteCountText || !logoutButton) return;
  await auth.ready;

  function formatLoginTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '로그인 시간 정보 없음';
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(date);
  }

  function render() {
    const user = auth.getUser();
    guestView.hidden = Boolean(user);
    memberView.hidden = !user;
    if (!user) return;

    userId.textContent = user.id + '님';
    loginTime.textContent = formatLoginTime(user.loginAt);
    auth.updateLoginButtons();
    if (auth.isGuest && auth.isGuest(user)) {
      renderGuestDemo();
      return;
    }
    loadFavorites();
    loadLatestDiagnosis();
    loadNotifications();
    loadInquiries();
  }

  function renderGuestDemo() {
    userId.textContent = '포트폴리오 게스트';
    favoriteCount.textContent = '3';
    favoriteCountText.textContent = '3개 데모 매물';
    if (unreadCount) unreadCount.textContent = '1';
    if (inquiryCount) inquiryCount.textContent = '2';
    if (readAllNotifications) readAllNotifications.hidden = true;
    if (diagnosis) {
      diagnosis.innerHTML = '<div class="mypage-diagnosis-score" data-level="safe"><strong>82</strong><span>점</span></div>'
        + '<div><strong>비교적 안전</strong><p>안전 8 · 주의 2 · 위험 0 · 포트폴리오 데모</p><small>실제 회원 데이터가 아닙니다.</small></div>'
        + '<a href="/defense/result">결과 화면 보기</a>';
    }
    if (notifications) {
      notifications.innerHTML = '<article class="mypage-notification-item is-unread"><i class="fa-solid fa-bell" aria-hidden="true"></i>'
        + '<div><strong>게스트 모드 안내</strong><p>서비스 기능을 둘러볼 수 있으며 변경 요청은 차단됩니다.</p><small>포트폴리오 데모 알림</small></div></article>';
    }
    if (inquiries) {
      inquiries.innerHTML = '<article class="mypage-inquiry-item"><div><strong>매물 이용 문의 데모</strong><small>비식별 예시 · 답변 완료</small></div>'
        + '<span class="mypage-inquiry-status" data-status="answered">답변 완료</span></article>'
        + '<article class="mypage-inquiry-item"><div><strong>계약 안전 문의 데모</strong><small>비식별 예시 · 확인 중</small></div>'
        + '<span class="mypage-inquiry-status" data-status="in_progress">확인 중</span></article>';
    }
  }

  async function loadFavorites() {
    try {
      const response = await fetch('/api/favorites', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('찜한 매물을 불러오지 못했습니다.');
      const payload = await response.json();
      const savedCount = Array.isArray(payload.ids) ? payload.ids.length : 0;
      favoriteCount.textContent = savedCount;
      favoriteCountText.textContent = savedCount + '개 매물';
    } catch (error) {
      favoriteCount.textContent = '0';
      favoriteCountText.textContent = '찜한 매물';
    }
  }

  async function loadNotifications() {
    if (!notifications) return;
    try {
      const response = await fetch('/api/notifications', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('알림을 불러오지 못했습니다.');
      const payload = await response.json();
      const items = Array.isArray(payload.items) ? payload.items : [];
      if (unreadCount) unreadCount.textContent = Number(payload.unreadCount || 0);
      if (readAllNotifications) readAllNotifications.hidden = !items.some(function (item) { return !item.read; });
      notifications.replaceChildren();
      if (!items.length) {
        const empty = document.createElement('div');
        empty.className = 'mypage-notification-empty';
        empty.innerHTML = '<i class="fa-regular fa-bell" aria-hidden="true"></i><div><strong>새 알림이 없습니다</strong><p>새로운 소식이 도착하면 이곳에서 확인할 수 있습니다.</p></div>';
        notifications.appendChild(empty);
        return;
      }
      items.forEach(function (item) {
        const article = document.createElement('article');
        const icon = document.createElement('i');
        const copy = document.createElement('div');
        article.className = 'mypage-notification-item';
        article.classList.toggle('is-unread', !item.read);
        icon.className = 'fa-solid fa-bell';
        icon.setAttribute('aria-hidden', 'true');
        const title = document.createElement('strong');
        const message = document.createElement('p');
        const meta = document.createElement('small');
        title.textContent = item.title || 'ZipAI 알림';
        message.textContent = item.message || '';
        meta.textContent = formatLoginTime(item.createdAt);
        copy.append(title, message, meta);
        article.append(icon, copy);
        if (item.targetUrl) {
          article.classList.add('is-linked');
          article.tabIndex = 0;
          article.setAttribute('role', 'link');
          article.addEventListener('click', async function () {
            if (!item.read) {
              await fetch('/api/notifications/' + item.id + '/read', {
                method: 'PATCH', credentials: 'same-origin'
              }).catch(function () { /* 이동은 유지 */ });
            }
            window.location.href = item.targetUrl;
          });
          article.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              article.click();
            }
          });
        }
        notifications.appendChild(article);
      });
    } catch (error) {
      notifications.innerHTML = '<p class="mypage-notification-error">알림을 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</p>';
    }
  }

  if (readAllNotifications) {
    readAllNotifications.addEventListener('click', async function () {
      readAllNotifications.disabled = true;
      try {
        const response = await fetch('/api/notifications/read-all', {
          method: 'PATCH', credentials: 'same-origin'
        });
        if (!response.ok) throw new Error('알림을 처리하지 못했습니다.');
        await loadNotifications();
        auth.updateLoginButtons();
      } finally {
        readAllNotifications.disabled = false;
      }
    });
  }

  async function loadInquiries() {
    if (!inquiries) return;
    const labels = { received: '접수', in_progress: '확인 중', answered: '답변 완료' };
    try {
      const response = await fetch('/api/inquiries', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('문의내역을 불러오지 못했습니다.');
      const payload = await response.json();
      const items = Array.isArray(payload.items) ? payload.items : [];
      if (inquiryCount) inquiryCount.textContent = items.length;
      inquiries.replaceChildren();
      if (!items.length) {
        inquiries.innerHTML = '<p>접수한 문의가 없습니다. 고객센터에서 궁금한 내용을 문의할 수 있습니다.</p>';
        return;
      }
      items.slice(0, 3).forEach(function (item) {
        const article = document.createElement('article');
        const copy = document.createElement('div');
        const title = document.createElement('strong');
        const meta = document.createElement('small');
        const status = document.createElement('span');
        article.className = 'mypage-inquiry-item';
        title.textContent = item.title || '제목 없는 문의';
        meta.textContent = (item.category || '기타') + ' · ' + formatLoginTime(item.updatedAt || item.createdAt);
        status.className = 'mypage-inquiry-status';
        status.dataset.status = item.status || '';
        status.textContent = labels[item.status] || item.status || '상태 확인';
        copy.append(title, meta);
        article.append(copy, status);
        inquiries.appendChild(article);
      });
    } catch (error) {
      inquiries.innerHTML = '<p>문의내역을 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</p>';
    }
  }

  async function loadLatestDiagnosis() {
    if (!diagnosis) return;
    try {
      const response = await fetch('/api/fraud-diagnoses/latest', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('진단 결과를 불러오지 못했습니다.');
      const payload = await response.json();
      if (!payload.available || !payload.diagnosis) {
        diagnosis.innerHTML = '<p>저장된 진단 결과가 없습니다.</p><a href="/defense/checklist">안전진단 시작하기</a>';
        return;
      }
      const item = payload.diagnosis;
      const labels = { safe: '비교적 안전', caution: '주의 필요', danger: '위험 신호' };
      diagnosis.innerHTML = '<div class="mypage-diagnosis-score" data-level="' + item.riskLevel + '"><strong>'
        + item.finalScore + '</strong><span>점</span></div><div><strong>' + (labels[item.riskLevel] || '진단 완료')
        + '</strong><p>안전 ' + item.safeCount + ' · 주의 ' + item.cautionCount + ' · 위험 ' + item.dangerCount
        + (item.jeonseRatio == null ? '' : ' · 전세가율 ' + Number(item.jeonseRatio).toFixed(1).replace('.0', '') + '%')
        + '</p><small>' + formatLoginTime(item.updatedAt) + '</small></div><a href="/defense/result">결과 보기</a>';
    } catch (error) {
      diagnosis.innerHTML = '<p>최근 진단 결과를 불러오지 못했습니다.</p>';
    }
  }

  logoutButton.addEventListener('click', async function () {
    await auth.logout();
    window.location.href = auth.resolvePage('login.html');
  });

  render();
})();
