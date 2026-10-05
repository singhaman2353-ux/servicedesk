import { getExpiresAt, getUser, logout } from '../auth.js';
import { navigate, setFlash } from '../router.js';
import { brand, h } from '../ui.js';

const ROLE_LABELS = { ADMIN: 'Administrator', AGENT: 'Agent', REQUESTER: 'Requester' };
const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });

function formatRemaining(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = String(Math.floor(total / 60)).padStart(2, '0');
  const seconds = String(total % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
}

function navItem(label, { active = false, soon = false } = {}) {
  if (active) return h('a', { class: 'nav-item is-active', href: '#/dashboard', 'aria-current': 'page' }, label);
  return h('span', { class: 'nav-item is-disabled', 'aria-disabled': 'true' }, label, soon ? h('small', {}, 'Soon') : null);
}

function row(term, description) {
  return h('div', { class: 'row' }, h('dt', {}, term), h('dd', {}, description));
}

function placeholder(title, text) {
  return h('article', { class: 'card card-placeholder' },
    h('div', { class: 'card-head' }, h('h3', {}, title), h('span', { class: 'badge' }, 'Not connected yet')),
    h('p', { class: 'muted' }, text));
}

export function renderDashboard(container) {
  const user = getUser();
  const expiresAt = getExpiresAt();
  const role = user.role;

  const countdown = h('strong', { class: 'countdown' }, '--:--');
  const signOut = () => {
    logout();
    setFlash('You have been signed out.', 'info');
    navigate('/login');
  };

  const tick = () => {
    const remaining = expiresAt - Date.now();
    if (remaining <= 0) {
      logout();
      setFlash('Your session has expired. Please sign in again.', 'info');
      navigate('/login');
      return;
    }
    countdown.textContent = formatRemaining(remaining);
  };

  const nav = h('nav', { class: 'sidebar', 'aria-label': 'Main' },
    navItem('Overview', { active: true }),
    navItem('My requests', { soon: true }),
    role === 'AGENT' || role === 'ADMIN' ? navItem('Team queue', { soon: true }) : null,
    role === 'ADMIN' ? navItem('Administration', { soon: true }) : null);

  const header = h('header', { class: 'topbar' },
    brand(),
    h('div', { class: 'topbar-user' },
      h('div', { class: 'user-chip' },
        h('span', { class: 'user-name' }, user.name),
        h('span', { class: `role role-${String(role).toLowerCase()}` }, ROLE_LABELS[role] ?? role)),
      h('button', { type: 'button', class: 'button button-quiet', onclick: signOut }, 'Sign out')));

  const main = h('main', { class: 'content', id: 'main', tabindex: '-1' },
    h('h1', {}, `Welcome, ${user.name.split(' ')[0]}`),
    h('p', { class: 'muted' }, 'Your service desk workspace. Request features appear here as they are connected.'),
    h('div', { class: 'grid' },
      h('article', { class: 'card' },
        h('h2', {}, 'Your profile'),
        h('dl', { class: 'details' },
          row('Name', user.name),
          row('Email', user.email),
          row('Role', ROLE_LABELS[role] ?? role),
          row('Team', user.teamName ?? 'No team'),
          row('Member since', user.createdAt ? dateOnly.format(new Date(user.createdAt)) : '—'))),
      h('article', { class: 'card' },
        h('h2', {}, 'Session'),
        h('p', { class: 'session-time' }, countdown),
        h('p', { class: 'muted' }, `Signed in until ${dateTime.format(new Date(expiresAt))}. Sessions last 60 minutes, then you sign in again.`))),
    h('h2', { class: 'section-title' }, 'Coming next'),
    h('div', { class: 'grid grid-3' },
      placeholder('Service requests', 'Submit and track requests once the requests API is available.'),
      placeholder('SLA tracking', 'Deadlines and escalations will show here.'),
      placeholder('Notifications', 'Updates on your requests will show here.')));

  container.append(h('div', { class: 'shell' }, header, nav, main));
  main.focus();
  tick();
  const timer = window.setInterval(tick, 1000);
  return () => window.clearInterval(timer);
}
