import React, { useState } from 'react';
import { api } from '../api/client';

// Load Plaid Link from the CDN once (avoids adding an npm dependency).
function loadPlaidScript() {
  return new Promise((resolve, reject) => {
    if (window.Plaid) return resolve();
    const existing = document.getElementById('plaid-link-script');
    if (existing) { existing.addEventListener('load', () => resolve()); return; }
    const s = document.createElement('script');
    s.id = 'plaid-link-script';
    s.src = 'https://cdn.plaid.com/link/v2/stable/link-initialize.js';
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('Failed to load Plaid Link'));
    document.body.appendChild(s);
  });
}

export default function LinkBankButton({ onLinked }) {
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState('');

  async function linkBank() {
    setBusy(true);
    setMsg('');
    try {
      await loadPlaidScript();
      const r = await api('/plaid/create_link_token', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: {},
      });
      const data = await r.json();
      if (!r.ok || data.success === false) {
        setMsg(data.message || 'Bank linking is not available.');
        setBusy(false);
        return;
      }
      const linkToken = data.data?.link_token;
      const handler = window.Plaid.create({
        token: linkToken,
        onSuccess: async (publicToken, metadata) => {
          try {
            const ex = await api('/plaid/exchange_public_token', {
              method: 'POST',
              headers: { 'content-type': 'application/json' },
              body: { public_token: publicToken, institutionName: metadata?.institution?.name },
            });
            const exData = await ex.json();
            if (ex.ok && exData.success !== false) {
              setMsg('Bank linked! Balances imported.');
              onLinked?.();
            } else {
              setMsg(exData.message || 'Could not finish linking.');
            }
          } catch (e) {
            setMsg(e.message || 'Could not finish linking.');
          } finally {
            setBusy(false);
          }
        },
        onExit: () => setBusy(false),
      });
      handler.open();
    } catch (e) {
      setMsg(e.message || 'Error opening Plaid.');
      setBusy(false);
    }
  }

  return (
    <div>
      <button className="btn btn--primary" onClick={linkBank} disabled={busy}>
        {busy ? 'Linking…' : 'Link a bank (Plaid)'}
      </button>
      <button
        className="btn btn--ghost"
        style={{ marginLeft: 8 }}
        onClick={async () => {
          setBusy(true); setMsg('');
          try {
            const r = await api('/plaid/refresh', { method: 'POST', headers: { 'content-type': 'application/json' }, body: {} });
            const d = await r.json();
            setMsg(r.ok && d.success !== false ? 'Balances refreshed.' : (d.message || 'Nothing to refresh.'));
            if (r.ok) onLinked?.();
          } catch (e) { setMsg(e.message); } finally { setBusy(false); }
        }}
        disabled={busy}
      >
        Refresh balances
      </button>
      {msg && <div className="muted" style={{ marginTop: 8 }}>{msg}</div>}
    </div>
  );
}
