// The API base URL comes from <meta name="api-base-url"> in index.html (default "/api").
// There are no secrets in the frontend.
const meta = document.querySelector('meta[name="api-base-url"]');
const raw = (meta && meta.content) || '/api';

export const API_BASE_URL = raw.replace(/\/+$/, '');
