import { API_BASE_URL } from './config.js';

/** Every failed request becomes one of these. status 0 means the server could not be reached. */
export class ApiError extends Error {
  constructor({ status, code = null, message, fieldErrors = [], retryAfterSeconds = null }) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

let getToken = () => null;
let handleUnauthorized = () => {};

/** Wired once in app.js, so this module does not depend on auth state or the router. */
export function configureApi({ tokenProvider, onUnauthorized }) {
  getToken = tokenProvider;
  handleUnauthorized = onUnauthorized;
}

function parseRetryAfter(value) {
  const seconds = Number.parseInt(value ?? '', 10);
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : null;
}

async function readJson(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function defaultMessage(status) {
  if (status >= 500) return 'Something went wrong on our side. Please try again.';
  return 'The request could not be completed.';
}

/**
 * auth: true  -> attach "Authorization: Bearer <token>" and treat a 401 as "session invalid"
 *                (clears auth state and redirects to login through the global handler).
 * auth: false -> public endpoint (login, register): a 401 is an ordinary error for the caller.
 */
export async function request(path, { method = 'GET', body, auth = true, signal } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = auth ? getToken() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(API_BASE_URL + path) {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new ApiError({
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Cannot reach the server. Check your connection and try again.',
    });
  }

  const payload = await readJson(response);
  if (response.ok) return payload;

  const error = new ApiError({
    status: response.status,
    code: payload?.error ?? null,
    message: payload?.message ?? defaultMessage(response.status),
    fieldErrors: Array.isArray(payload?.fieldErrors) ? payload.fieldErrors : [],
    retryAfterSeconds: parseRetryAfter(response.headers.get('Retry-After')),
  });
  if (response.status === 401 && auth) handleUnauthorized(error);
  throw error;
}

// Only the endpoints that exist on the backend today.
export const authApi = {
  register: (data) => request('/auth/register', { method: 'POST', body: data, auth: false }),
  login: (data) => request('/auth/login', { method: 'POST', body: data, auth: false }),
};
