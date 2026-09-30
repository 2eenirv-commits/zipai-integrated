(async function () {
  'use strict';

  const auth = window.ZipaiAuth;
  const form = document.getElementById('socialProfileForm');
  if (!auth || !form) return;

  await auth.ready;
  const user = auth.getUser();
  if (!user) {
    window.location.replace('/member/login');
    return;
  }
  form.elements.userId.value = user.id || '';

  function formatPhone(value) {
    const digits = String(value || '').replace(/[^0-9]/g, '').slice(0, 11);
    if (digits.length <= 3) return digits;
    if (digits.length <= 7) return digits.slice(0, 3) + '-' + digits.slice(3);
    if (digits.length <= 10) return digits.slice(0, 3) + '-' + digits.slice(3, 6) + '-' + digits.slice(6);
    return digits.slice(0, 3) + '-' + digits.slice(3, 7) + '-' + digits.slice(7);
  }

  form.elements.phone.addEventListener('input', function () {
    form.elements.phone.value = formatPhone(form.elements.phone.value);
  });
  if (user.email && !String(user.email).endsWith('@social.zipai.invalid')) {
    form.elements.email.value = user.email;
  }
  form.elements.phone.value = formatPhone(user.phone || '');

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }

    const submit = form.querySelector('[type="submit"]');
    submit.disabled = true;
    try {
      const response = await fetch('/api/auth/social-profile', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: form.elements.userId.value,
          email: form.elements.email.value,
          phone: form.elements.phone.value
        })
      });
      const payload = await response.json().catch(function () { return {}; });
      if (!response.ok) throw new Error(payload.message || '추가정보를 저장하지 못했습니다.');
      await auth.refreshUser();
      window.location.href = '/';
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
})();
