export async function api(path, options={}) {
  const opts = { credentials: 'include', ...(options || {}) };
  if (opts.headers && opts.headers['content-type'] === 'application/json' && typeof opts.body !== 'string') {
    opts.body = JSON.stringify(opts.body);
  }
  return fetch(path.startsWith('/') ? path : `/${path}`, opts);
}
