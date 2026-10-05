// Tiny DOM helpers. Text is always inserted as text nodes (never innerHTML), so server data cannot inject markup.
export function h(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === null || value === undefined || value === false) continue;
    if (key.startsWith('on') && typeof value === 'function') {
      node.addEventListener(key.slice(2).toLowerCase(), value);
    } else if (value === true) {
      node.setAttribute(key, '');
    } else {
      node.setAttribute(key === 'className' ? 'class' : key, String(value));
    }
  }
  append(node, children);
  return node;
}

function append(node, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

/** A labelled input with an inline error message wired up for screen readers. */
export function field({ id, label, type = 'text', autocomplete, hint, value = '', maxlength, required = true }) {
  const input = h('input', {
    id, name: id, type, autocomplete, maxlength, required, value,
    class: 'input', spellcheck: 'false', autocapitalize: 'off',
  });
  const error = h('p', { class: 'field-error', id: `${id}-error`, role: 'alert' });
  const hintNode = hint ? h('p', { class: 'field-hint', id: `${id}-hint` }, hint) : null;
  const root = h('div', { class: 'field' }, h('label', { for: id }, label), input, hintNode, error);

  const describedBy = [hintNode ? `${id}-hint` : null].filter(Boolean);
  if (describedBy.length) input.setAttribute('aria-describedby', describedBy.join(' '));

  return {
    root,
    input,
    setError(message) {
      error.textContent = message ?? '';
      if (message) {
        input.setAttribute('aria-invalid', 'true');
        input.setAttribute('aria-describedby', [...describedBy, `${id}-error`].join(' '));
      } else {
        input.removeAttribute('aria-invalid');
        if (describedBy.length) input.setAttribute('aria-describedby', describedBy.join(' '));
        else input.removeAttribute('aria-describedby');
      }
    },
  };
}

/** Password input with a show/hide button. */
export function passwordField(options) {
  const base = field({ ...options, type: 'password' });
  const toggle = h('button', {
    type: 'button', class: 'toggle', 'aria-pressed': 'false', 'aria-controls': options.id,
    onclick: () => {
      const show = base.input.type === 'password';
      base.input.type = show ? 'text' : 'password';
      toggle.textContent = show ? 'Hide' : 'Show';
      toggle.setAttribute('aria-pressed', String(show));
    },
  }, 'Show');
  const wrap = h('div', { class: 'input-wrap' });
  base.input.replaceWith(wrap);
  wrap.append(base.input, toggle);
  return base;
}

/** Message box above a form's submit button: kind is 'error' | 'success' | 'info'. */
export function banner() {
  const node = h('div', { class: 'banner', hidden: true });
  return {
    node,
    show(message, kind = 'error') {
      node.className = `banner banner-${kind}`;
      node.setAttribute('role', kind === 'error' ? 'alert' : 'status');
      node.textContent = message;
      node.hidden = false;
    },
    hide() {
      node.hidden = true;
      node.textContent = '';
    },
  };
}

export function setBusy(button, busy, busyLabel) {
  if (!button.dataset.label) button.dataset.label = button.textContent;
  button.disabled = busy;
  button.setAttribute('aria-busy', String(busy));
  button.textContent = busy ? busyLabel : button.dataset.label;
}

export function brand() {
  return h('div', { class: 'brand' },
    h('span', { class: 'brand-mark', 'aria-hidden': 'true' }, 'SD'),
    h('span', { class: 'brand-name' }, 'ServiceDesk'));
}
