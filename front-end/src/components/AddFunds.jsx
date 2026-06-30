import React, { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';

const oid = (a) => a?._id || a?.id || '';

// Record income into a manual account (raises usable money). Plaid accounts get
// their balance from the bank, so they are not selectable here.
export default function AddFunds({ onChange }) {
  const [accounts, setAccounts] = useState([]);
  const [accountId, setAccountId] = useState('');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState('');

  const loadAccounts = useCallback(async () => {
    try {
      const r = await api('/accounts/listWithAllocations');
      const d = await r.json();
      const list = (Array.isArray(d?.data) ? d.data : []).filter((a) => a.source !== 'plaid');
      setAccounts(list);
      // Default to the first account only if nothing is selected yet.
      setAccountId((prev) => prev || (list[0] ? oid(list[0]) : ''));
    } catch (e) {
      // ignore; selector just stays empty
    }
  }, []);

  useEffect(() => { loadAccounts(); }, [loadAccounts]);

  async function submit() {
    const amt = Number(amount);
    if (!accountId || !(amt > 0)) { setMsg('Pick an account and a positive amount.'); return; }
    setBusy(true); setMsg('');
    try {
      const r = await api('/accounts/addFunds', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: { accountId, amount: amt },
      });
      const d = await r.json();
      if (r.ok && d.success !== false) {
        setMsg(`Added $${amt.toFixed(2)}.`);
        setAmount('');
        onChange?.();
        loadAccounts();
      } else {
        setMsg(d.message || 'Could not add funds.');
      }
    } catch (e) {
      setMsg(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h3 style={{ marginBottom: 8 }}>Add income</h3>
      {accounts.length === 0 ? (
        <div className="muted">Create a manual account first (e.g. "Checking").</div>
      ) : (
        <>
          <select
            className="select select--native"
            value={accountId}
            onChange={(e) => setAccountId(e.target.value)}
            style={{ width: '100%', marginBottom: 8 }}
          >
            {accounts.map((a) => (
              <option key={oid(a)} value={oid(a)}>
                {a.name} — ${Number(a.balance || 0).toFixed(2)} (usable ${Number(a.usable ?? a.unallocated ?? 0).toFixed(2)})
              </option>
            ))}
          </select>
          <input
            className="input"
            type="number" min="0" step="0.01"
            placeholder="Amount (e.g. 2000 paycheck)"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            style={{ width: '100%', marginBottom: 8 }}
          />
          <button className="btn btn--primary" onClick={submit} disabled={busy}>
            {busy ? 'Adding…' : 'Add to account'}
          </button>
        </>
      )}
      {msg && <div className="muted" style={{ marginTop: 8 }}>{msg}</div>}
    </div>
  );
}
