import { register } from '../auth.js';
import { toFormErrors } from '../errors.js';
import { navigate, setFlash } from '../router.js';
import { banner, brand, field, h, passwordField, setBusy } from '../ui.js';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// These mirror the backend rules for a faster form. The server stays the authority and its errors are shown too.
function validate({ name, email, password }) {
  const errors = {};
  if (!name) errors.name = 'Enter your name';
  else if (name.length > 100) errors.name = 'Name must be at most 100 characters';
  if (!email) errors.email = 'Enter your email';
  else if (!EMAIL_PATTERN.test(email) || email.length > 254) errors.email = 'Enter a valid email address';
  if (password.length < 10 || password.length > 72) errors.password = 'Password must be between 10 and 72 characters';
  else if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) errors.password = 'Password must contain at least one letter and one digit';
  else if (new TextEncoder().encode(password).length > 72) errors.password = 'Password is too long (72 bytes maximum)';
  return errors;
}

export function renderRegister(container) {
  const name = field({ id: 'name', label: 'Full name', autocomplete: 'name', maxlength: 100 });
  const email = field({ id: 'email', label: 'Email', type: 'email', autocomplete: 'email', maxlength: 254 });
  const password = passwordField({
    id: 'password', label: 'Password', autocomplete: 'new-password', maxlength: 72,
    hint: 'At least 10 characters, with a letter and a number.',
  });
  const inputs = { name, email, password };
  const message = banner();
  const submit = h('button', { type: 'submit', class: 'button button-primary' }, 'Create account');
  const form = h('form', { class: 'form', novalidate: true }, name.root, email.root, password.root, message.node, submit);

  const showFieldErrors = (errors) => {
    for (const [key, control] of Object.entries(inputs)) control.setError(errors[key]);
    const first = Object.keys(inputs).find((key) => errors[key]);
    if (first) inputs[first].input.focus();
  };

  let busy = false;
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy) return;
    message.hide();

    const values = { name: name.input.value.trim(), email: email.input.value.trim(), password: password.input.value };
    const local = validate(values);
    showFieldErrors(local);
    if (Object.keys(local).length) return;

    busy = true;
    setBusy(submit, true, 'Creating account…');
    try {
      await register(values.name, values.email, values.password);
      setFlash('Account created. Sign in to continue.', 'success', { email: values.email });
      navigate('/login');
    } catch (error) {
      const { form: formError, fields } = toFormErrors(error, {
        context: 'register', knownFields: ['name', 'email', 'password'],
      });
      showFieldErrors(fields);
      if (formError) message.show(formError, 'error');
    } finally {
      busy = false;
      setBusy(submit, false);
    }
  });

  container.append(
    h('main', { class: 'auth-layout' },
      h('section', { class: 'auth-card', 'aria-labelledby': 'title' },
        brand(),
        h('h1', { id: 'title' }, 'Create your account'),
        h('p', { class: 'muted' }, 'Register to submit and track service requests.'),
        form,
        h('p', { class: 'auth-switch' }, 'Already have an account? ', h('a', { href: '#/login' }, 'Sign in')))));
  name.input.focus();
}
