(function () {
  'use strict';

  const menuButton = document.querySelector('.menu-button');
  if (menuButton) {
    menuButton.addEventListener('click', function () {
      const open = this.classList.toggle('open');
      this.setAttribute('aria-expanded', String(open));
    });
  }

  document.querySelectorAll('a[href="#"]').forEach(function (link) {
    link.addEventListener('click', function (event) {
      event.preventDefault();
      showToast('이 메뉴는 데모 화면에서 제공되지 않아요.');
    });
  });

  document.querySelectorAll('.login-button').forEach(function (button) {
    button.addEventListener('click', function () {
      showToast('로그인 기능은 플랫폼 연동 후 사용할 수 있어요.');
    });
  });

  window.showToast = function (message) {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(window.__toastTimer);
    window.__toastTimer = setTimeout(function () {
      toast.classList.remove('show');
    }, 2400);
  };

  window.safeStorage = {
    get: function (key, fallback) {
      try {
        const value = localStorage.getItem(key);
        return value ? JSON.parse(value) : fallback;
      } catch (error) {
        return fallback;
      }
    },
    set: function (key, value) {
      try {
        localStorage.setItem(key, JSON.stringify(value));
        return true;
      } catch (error) {
        return false;
      }
    },
    remove: function (key) {
      try { localStorage.removeItem(key); } catch (error) { /* no-op */ }
    }
  };
})();
