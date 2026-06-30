export async function api(path, options={}) {
  const opts = { credentials: 'include', ...(options || {}) };
  // Find the Content-Type header regardless of casing — callers use both
  // "Content-Type" and "content-type". Without this, a capitalized header
  // skipped JSON.stringify and the body went out as "[object Object]".
  const headers = opts.headers || {};
  const ctKey = Object.keys(headers).find(k => k.toLowerCase() === 'content-type');
  const isJson = ctKey && String(headers[ctKey]).toLowerCase().includes('application/json');
  if (isJson && opts.body != null && typeof opts.body !== 'string') {
    opts.body = JSON.stringify(opts.body);
  }
  return fetch(path.startsWith('/') ? path : `/${path}`, opts);
}
