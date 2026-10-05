import { ApiError, authApi } from './api.js';

const STORAGE_KEY = 'servicedesk.auth';
const listeners = new Set();

function storage() {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

function load() {
  try {
    const raw = storage()?.getItem(STORAGE_KEY);
    if (!raw) return null;
    const saved = JSON.parse(raw);
    const valid = saved && typeof saved.accessToken === 'string' && saved.user && saved.expiresAt;
    if (!valid || Date.parse(saved.expiresAt) <= Date.now()) {
      storage()?.removeItem(STORAGE_KEY);
      return null;
    }
    return saved;
  } catch {
    return null;
  }
}

function persist() {
  try {
    if (state) storage()?.setItem(STORAGE_KEY, JSON.stringify(state));
    else storage()?.removeItem(STORAGE_KEY);
  } catch {
    // storage unavailable: the session then lasts until the page is closed or reloaded
  }
}

function notify() {
  listeners.forEach((listener) => listener(state?.user ?? null));
}

// { accessToken, expiresAt, user } or null. Kept in memory and mirrored to sessionStorage.
let state = load();

export const getToken = () => state?.accessToken ?? null;
export const getUser = () => state?.user ?? null;
export const getExpiresAt = () => (state ? Date.parse(state.expiresAt) : null);

export function isAuthenticated() {
  if (!state) return false;
  if (Date.parse(state.expiresAt) <= Date.now()) {
    clear();
    return false;
  }
  return true;
}

export function clear() {
  state = null;
  persist();
  notify();
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export async function login(email, password) {
  const result = await authApi.login({ email, password });
  if (!result || typeof result.accessToken !== 'string' || !result.user || !result.expiresAt) {
    throw new ApiError({ status: 500, message: 'Unexpected response from the server.' });
  }
  state = { accessToken: result.accessToken, expiresAt: result.expiresAt, user: result.user };
  persist();
  notify();
  return state.user;
}

/** Registration does not sign the user in: the backend returns the new user, not a token. */
export function register(name, email, password) {
  return authApi.register({ name, email, password });
}

/** Logout is client-side only: there is no server session or revocation list. */
export function logout() {
  clear();
}
