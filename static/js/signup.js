(async function () {
  'use strict';

  const auth = window.ZipaiAuth;
  const form = document.getElementById('signupForm');
  if (!auth || !form) return;

  await auth.ready;
  if (auth.getUser()) {
    window.location.replace(auth.resolvePage('index.html'));
    return;
  }

  const password = form.elements.password;
  const passwordConfirm = form.elements.passwordConfirm;
  const submit = document.getElementById('signupSubmit');
  const submitIcon = submit && submit.querySelector('i');
  const submitLabel = submit && submit.querySelector('span');

  function setSubmitState(state) {
    if (!submit || !submitIcon || !submitLabel) return;
    const states = {
      idle: { label: '회원가입', icon: 'fa-solid fa-user-plus', disabled: false },
      loading: { label: '가입 처리 중...', icon: 'fa-solid fa-spinner fa-spin', disabled: true },
      success: { label: '회원가입 완료', icon: 'fa-solid fa-circle-check', disabled: true }
    };
    const next = states[state] || states.idle;
    submitLabel.textContent = next.label;
    submitIcon.className = next.icon;
    submit.disabled = next.disabled;
    submit.setAttribute('aria-busy', String(state === 'loading'));
  }

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

  function validatePasswordMatch() {
    const same = !password.value || !passwordConfirm.value || password.value === passwordConfirm.value;
    passwordConfirm.setCustomValidity(same ? '' : '비밀번호가 일치하지 않습니다.');
  }

  password.addEventListener('input', validatePasswordMatch);
  passwordConfirm.addEventListener('input', validatePasswordMatch);

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    validatePasswordMatch();

    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }

    const previousError = form.querySelector('.login-page-error');
    if (previousError) previousError.remove();
    setSubmitState('loading');
    let succeeded = false;

    try {
      await auth.signup({
        userId: form.elements.userId.value,
        email: form.elements.email.value,
        phone: form.elements.phone.value,
        password: form.elements.password.value
      });

      succeeded = true;
      form.reset();
      setSubmitState('success');
      await new Promise(function (resolve) { window.setTimeout(resolve, 500); });
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
      if (!succeeded) setSubmitState('idle');
    }
  });
})();
