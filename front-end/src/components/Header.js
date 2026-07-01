import { useEffect, useState } from 'react';
import { getUsername } from '../api/whoami';

export function useUsername() {
  const [username, setUsername] = useState('');

  useEffect(() => {
    const ac = new AbortController();
    (async () => {
      const name = await getUsername(ac.signal);
      if (!ac.signal.aborted) setUsername(name);
    })();
    return () => ac.abort();
  }, []);

  return username;
}

export default function Header({ titleOverride }) {
  const user = useUsername();

  // Use titleOverride if provided, otherwise fall back to default logic
  const title = titleOverride || (user ? `${user}'s Personal Finance App` : 'Personal Finance App');

  return (
    <header className="app-header">
      <div className="app-brand">
        <div className="brand-title">{title}</div>
        <div className="brand-tag">helping you achieve your financial goals!</div>
      </div>
      <nav className="nav-actions">
        <button className="btn-link" onClick={() => window.location.href = '/home'}>Home</button>
        <button className="btn-link" onClick={() => window.location.href = '/accounts'}>Accounts</button>
        <button className="btn-link" onClick={() => window.location.href = '/goals'}>Goals</button>
        <button className="btn-link danger" onClick={async () => {
          try {
            const r = await fetch('/logout', { method: 'POST', credentials: 'include' });
            if (r.ok) {
              const data = await r.json();
              if (data.success === false) {
                console.error('Logout failed:', data.message);
              }
            } else {
              console.error('Logout failed:', r.status);
              // Try fallback logout endpoint
              try {
                const fallbackResponse = await fetch('/auth/logout', { method: 'POST', credentials: 'include' });
                if (fallbackResponse.ok) {
                  const fallbackData = await fallbackResponse.json();
                  if (fallbackData.success === false) {
                    console.error('Fallback logout failed:', fallbackData.message);
                  }
                } else {
                  console.error('Fallback logout failed:', fallbackResponse.status);
                }
              } catch (fallbackError) {
                console.error('Error during fallback logout:', fallbackError);
              }
            }
          } catch (error) {
            console.error('Error during logout:', error);
          }
          window.location.href = '/';
        }}>Log out</button>
      </nav>
    </header>
  );
}