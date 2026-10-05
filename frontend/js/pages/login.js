import { login } from '../auth.js';
import { toFormErrors } from '../errors.js';
import { navigate, takeFlash } from '../router.js';
import { banner, brand, field, h, passwordField, setBusy } from '../ui.js';

export function renderLogin(container) {
  const flash = takeFlash();
  const email = field({ id: 'email', label: 'Email', type: 'email', autocomplete: 'username', maxlength: 254, value: flash?.email ?? '' });
  const password = passwordField({ id: 'password', label: 'Password', autocomplete: 'current-password', maxlength: 128 });
  const message = banner();
  const submit = h('button', { type: 'submit', class: 'button button-primary' }, 'Sign in');
  const form = h('form', { class: 'form', novalidate: true }, email.root, password.root, message.node, submit);

  if (flash) message.show(flash.message, flash.kind);

  let busy = false;
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy) return;
    message.hide();
    email.setError(null);
    password.setError(null);

    const values = { email: email.input.value.trim(), password: password.input.value };
    if (!values.email || !values.password) {
      if (!values.email) email.setError('Enter your email');
      if (!values.password) password.setError('Enter your password');
      (values.email ? password : email).input.focus();
      return;
    }

    busy = true;
    setBusy(submit, true, 'Signing in…');
    try {
      await login(values.email, values.password);
      navigate('/dashboard');
    } catch (error) {
      const { form: formError, fields } = toFormErrors(error, { context: 'login', knownFields: ['email', 'password'] });
      email.setError(fields.email);
      password.setError(fields.password);
      if (formError) message.show(formError, 'error');
      password.input.value = '';
      (fields.email ? email : password).input.focus();
    } finally {
      busy = false;
      setBusy(submit, false);
    }
  });

  container.append(
    h('main', { class: 'auth-layout' },
      h('section', { class: 'auth-card', 'aria-labelledby': 'title' },
        brand(),
        h('h1', { id: 'title' }, 'Sign in'),
        h('p', { class: 'muted' }, 'Use your ServiceDesk account to continue.'),
        form,
        h('p', { class: 'auth-switch' }, 'New here? ', h('a', { href: '#/register' }, 'Create an account')))));
  email.input.focus();
}
