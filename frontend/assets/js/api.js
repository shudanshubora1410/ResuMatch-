/**
 * The single HTTP client for ResuMatch frontend pages.
 *
 * Responsibilities (so no page has to repeat them):
 *  - base URL and JSON headers,
 *  - the CSRF token on state-changing requests,
 *  - unwrapping the {success, data} / {success:false, ...} envelopes,
 *  - turning a non-JSON or failed response into a typed ApiError,
 *  - announcing session expiry (401) so pages can redirect to login,
 *  - retrying GETs when the free hosting tier is still waking up.
 */

const API_BASE = '/api/v1';

/** Render returns 502/503/504 while a sleeping service is booting. */
const RETRYABLE_STATUSES = new Set([502, 503, 504]);

/** Waits between cold-start retries: roughly 1.5s, 4s, 8s. */
const COLD_START_DELAYS_MS = [1500, 4000, 8000];

/** Custom events the pages listen to. */
export const EVENTS = {
  WAKING: 'resumatch:waking',
  UNAUTHORIZED: 'resumatch:unauthorized',
};

let csrfToken = null;

/** Called by the auth pages once the token has been fetched (Phase 1). */
export function setCsrfToken(token) {
  csrfToken = token || null;
}

export function getCsrfToken() {
  return csrfToken;
}

/** Error carrying the backend's standard error envelope. */
export class ApiError extends Error {
  constructor(message, { status = 0, code = 'NETWORK_ERROR', fieldErrors = {} } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors || {};
  }

  /** True when the user's session is gone and a login page should be shown. */
  get isUnauthorized() {
    return this.status === 401;
  }
}

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function announceWaking(attempt, maxAttempts) {
  window.dispatchEvent(new CustomEvent(EVENTS.WAKING, {
    detail: { attempt, maxAttempts },
  }));
}

async function readPayload(response) {
  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    // A proxy error page (for example Render's 502 HTML) is not our envelope.
    return null;
  }
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function toApiError(response, payload) {
  if (payload && payload.success === false) {
    return new ApiError(payload.message || 'The request failed.', {
      status: payload.status || response.status,
      code: payload.error || 'ERROR',
      fieldErrors: payload.fieldErrors,
    });
  }
  return new ApiError(`Request failed with status ${response.status}.`, {
    status: response.status,
  });
}

async function request(path, { method = 'GET', body, headers = {}, retry = method === 'GET' } = {}) {
  const url = path.startsWith('http') ? path : `${API_BASE}${path}`;
  const requestHeaders = { Accept: 'application/json', ...headers };

  if (body !== undefined) {
    requestHeaders['Content-Type'] = 'application/json';
  }
  // CSRF only matters for state-changing calls; sending it on GETs is harmless but noisy.
  if (csrfToken && method !== 'GET' && method !== 'HEAD') {
    requestHeaders['X-CSRF-Token'] = csrfToken;
  }

  const attempts = retry ? COLD_START_DELAYS_MS.length + 1 : 1;

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    let response;
    try {
      response = await fetch(url, {
        method,
        headers: requestHeaders,
        // Cookies are first-party: Vercel rewrites /api/* to the backend, so no CORS is needed.
        credentials: 'same-origin',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (networkError) {
      if (attempt < attempts) {
        announceWaking(attempt, attempts);
        await sleep(COLD_START_DELAYS_MS[attempt - 1]);
        continue;
      }
      throw new ApiError(
        'Could not reach the server. Check your connection and try again.',
        { code: 'NETWORK_ERROR' },
      );
    }

    const payload = await readPayload(response);

    if (RETRYABLE_STATUSES.has(response.status) && attempt < attempts) {
      announceWaking(attempt, attempts);
      await sleep(COLD_START_DELAYS_MS[attempt - 1]);
      continue;
    }

    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent(EVENTS.UNAUTHORIZED, { detail: { url } }));
    }

    if (!response.ok) {
      throw toApiError(response, payload);
    }

    if (response.status === 204 || payload === null) {
      return null;
    }
    return payload.data === undefined ? payload : payload.data;
  }

  throw new ApiError('The server did not respond. Please try again.', { code: 'TIMEOUT' });
}

export const api = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  patch: (path, body, options) => request(path, { ...options, method: 'PATCH', body }),
  del: (path, options) => request(path, { ...options, method: 'DELETE' }),
};

/** Convenience wrapper for the one endpoint that exists in Phase 0. */
export function checkHealth() {
  return api.get('/health');
}
