// REST client: session cookie (same origin) + CSRF header + uniform error handling.

export class ApiError extends Error {
  constructor(status, body) {
    super((body && body.message) || 'Request failed (' + status + ')');
    this.status = status;
    this.code = body && body.code;
    this.fieldErrors = (body && body.fieldErrors) || null;
  }
}

let csrf = null;

export async function refreshCsrf() {
  const res = await fetch('/api/auth/csrf', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
  if (!res.ok) throw new ApiError(res.status, null);
  csrf = await res.json();
  return csrf;
}

export function resetSession() {
  csrf = null;
}

async function parse(res) {
  if (res.status === 204) return null;
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** Core request. Returns the parsed body; pass {full:true} to get {data,status,headers}. */
export async function request(method, url, { body, headers, full, retried } = {}) {
  const opts = { method, credentials: 'same-origin', headers: { Accept: 'application/json', ...(headers || {}) } };
  if (method !== 'GET') {
    if (!csrf) await refreshCsrf();
    opts.headers[csrf.headerName] = csrf.token;
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
  }
  const res = await fetch(url, opts);
  const data = await parse(res);
  if (!res.ok) {
    // A stale CSRF token is the only 403 worth retrying once.
    if (res.status === 403 && method !== 'GET' && !retried && (!data || data.code === 'FORBIDDEN')) {
      await refreshCsrf();
      return request(method, url, { body, headers, full, retried: true });
    }
    if (res.status === 401 && !url.startsWith('/api/auth/login')) {
      window.dispatchEvent(new CustomEvent('session-expired'));
    }
    throw new ApiError(res.status, data);
  }
  return full ? { data, status: res.status, headers: res.headers } : data;
}

export const api = {
  get: (url, opts) => request('GET', url, opts),
  post: (url, body, opts) => request('POST', url, { ...opts, body }),
  put: (url, body, opts) => request('PUT', url, { ...opts, body }),
  patch: (url, body, opts) => request('PATCH', url, { ...opts, body }),
  del: (url, opts) => request('DELETE', url, opts),
};
