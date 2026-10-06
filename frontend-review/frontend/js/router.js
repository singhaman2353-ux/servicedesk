// Hash router (#/login, #/register, #/dashboard). It works on any static host without rewrite rules.
let flash = null;
let resolveCurrent = () => {};

export function setFlash(message, kind = 'info', extra = {}) {
  flash = { message, kind, ...extra };
}

export function takeFlash() {
  const value = flash;
  flash = null;
  return value;
}

export function navigate(path) {
  if (location.hash === `#${path}`) resolveCurrent();
  else location.hash = path;
}

/**
 * routes: { '/path': { access: 'public' | 'guest' | 'protected', title, render(container) -> cleanup? } }
 * guest      = only for signed-out users (login, register)
 * protected  = only for signed-in users
 */
export function start(container, { routes, isAuthenticated }) {
  let cleanup = null;

  const redirect = (path) => {
    history.replaceState(null, '', `#${path}`);
    resolve();
  };

  function resolve() {
    const path = location.hash.replace(/^#/, '') || '/';
    const route = routes[path];
    const signedIn = isAuthenticated();

    if (!route) return redirect(signedIn ? '/dashboard' : '/login');
    if (route.access === 'protected' && !signedIn) return redirect('/login');
    if (route.access === 'guest' && signedIn) return redirect('/dashboard');

    if (cleanup) cleanup();
    container.replaceChildren();
    cleanup = route.render(container) ?? null;
    document.title = `${route.title} · ServiceDesk`;
    window.scrollTo(0, 0);
  }

  resolveCurrent = resolve;
  window.addEventListener('hashchange', resolve);
  resolve();
}
