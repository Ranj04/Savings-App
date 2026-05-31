// front-end/src/ProtectedRoute.js
import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { fetchWhoami } from './api/whoami';

export default function ProtectedRoute({ children }) {
  const [state, setState] = useState('checking'); // checking | authed | anon

  useEffect(() => {
    let cancelled = false;
    async function check() {
      try {
        const r = await fetchWhoami();
        if (cancelled) return;
        if (r.status === 200) setState('authed');
        else if (r.status === 401) setState('anon');
        else {
          // network/500/transient → do NOT force logout; retry after 5s
          setTimeout(() => !cancelled && check(), 5000);
        }
      } catch {
        // transient network error → do NOT force logout; retry
        setTimeout(() => !cancelled && check(), 5000);
      }
    }
    check();
    return () => { cancelled = true; };
  }, []);

  if (state === 'checking') {
    return <div style={{ padding: '20px', textAlign: 'center' }}>Checking authentication...</div>;
  }
  
  if (state === 'anon') {
    return <Navigate to="/" replace />;
  }
  
  return children;
}