import { configureApi } from './api.js';
import * as auth from './auth.js';
import { renderDashboard } from './pages/dashboard.js';
import { renderLogin } from './pages/login.js';
import { renderRegister } from './pages/register.js';
import { navigate, setFlash, start } from './router.js';

// A 401 on any authenticated request means the token is missing, expired or no longer valid.
configureApi({
  tokenProvider: auth.getToken,
  onUnauthorized: () => {
    auth.clear();
    setFlash('Your session has expired. Please sign in again.', 'info');
    navigate('/login');
  },
});

start(document.getElementById('app'), {
  isAuthenticated: auth.isAuthenticated,
  routes: {
    '/login': { access: 'guest', title: 'Sign in', render: renderLogin },
    '/register': { access: 'guest', title: 'Create account', render: renderRegister },
    '/dashboard': { access: 'protected', title: 'Dashboard', render: renderDashboard },
  },
});
