(async function () {
  'use strict';

  const auth = window.ZipaiAuth;
  const form = document.getElementById('loginForm');
  const guestButton = document.getElementById('guestLoginButton');
  if (!auth || !form) return;

  await auth.ready;
  if (auth.getUser()) {
    window.location.replace(auth.resolvePage('index.html'));
    return;
  }

  const query = new URLSearchParams(window.location.search);
  if (query.has('oauthError')) {
    const message = document.createElement('p');
    message.className = 'login-page-error';
    message.textContent = '소셜 로그인에 실패했습니다. 제공 동의와 앱 설정을 확인해 주세요.';
    form.appendChild(message);
  }

  try {
    const response = await fetch('/api/auth/social-providers', { credentials: 'same-origin' });
    if (response.ok) {
      const providers = await response.json();
      let visible = 0;
      document.querySelectorAll('[data-social-provider]').forEach(function (button) {
        const enabled = providers[button.dataset.socialProvider] === true;
        button.hidden = !enabled;
        if (enabled) visible += 1;
      });
      const social = document.getElementById('socialLogin');
      if (social) social.hidden = visible === 0;
    }
  } catch (ignored) {
    // 자체 아이디 로그인은 소셜 공급자 상태 조회 실패와 무관하게 계속 사용할 수 있습니다.
  }

  form.addEventListener('submit', async function (event) {
    event.preventDefault();

    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }

    const submit = form.querySelector('[type="submit"]');
    submit.disabled = true;

    try {
      await auth.login({
        userId: form.elements.userId.value,
        password: form.elements.password.value
      });

      form.reset();
      window.location.href = auth.resolvePage('index.html');
    } catch (error) {
      let message = form.querySelector('.login-page-error');
      if (!message) {
        message = document.createElement('p');
        message.className = 'login-page-error';
        form.appendChild(message);
      }
      message.textContent = error.message;
    } finally {
      submit.disabled = false;
    }
  });

  if (guestButton) {
    guestButton.addEventListener('click', async function () {
      guestButton.disabled = true;
      try {
        await auth.guestLogin();
        window.location.href = auth.resolvePage('index.html');
      } catch (error) {
        let message = form.querySelector('.login-page-error');
        if (!message) {
          message = document.createElement('p');
          message.className = 'login-page-error';
          form.appendChild(message);
        }
        message.textContent = error.message;
      } finally {
        guestButton.disabled = false;
      }
    });
  }
})();
