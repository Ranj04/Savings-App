import { api } from './client';

// Single source of truth for the /auth/whoami call so the URL, credentials,
// and response-shape parsing aren't re-implemented across components.

// Returns the raw fetch Response (credentials included). Useful when the caller
// needs to branch on the HTTP status code (e.g. 401 vs transient 5xx).
export function fetchWhoami(signal) {
  return api('/auth/whoami', signal ? { signal } : {});
}

// Resolves to the current userName string, or '' when unauthenticated or on error.
export async function getUsername(signal) {
  try {
    const res = await fetchWhoami(signal);
    if (!res.ok) return '';
    const data = await res.json();
    if (data?.status === false) return '';
    return data?.data?.userName || '';
  } catch {
    return '';
  }
}
