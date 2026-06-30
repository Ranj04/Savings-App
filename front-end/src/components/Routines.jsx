import React, { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';

const oid = (x) => x?._id || x?.id || x?.uniqueId || x?.$oid || '';

// Recurring monthly "set aside" routines, e.g. $100 to the Hawaii goal each month.
export default function Routines({ onChange }) {
  const [routines, setRoutines] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [goals, setGoals] = useState([]);
  const [accountId, setAccountId] = useState('');
  const [goalId, setGoalId] = useState('');
  const [amount, setAmount] = useState('');
  const [dayOfMonth, setDayOfMonth] = useState('1');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState('');

  const loadAll = useCallback(async () => {
    try {
      const [rRes, aRes, gRes] = await Promise.all([
        api('/routines/list'),
        api('/accounts/listWithAllocations'),
        api('/goals/list'),
      ]);
      const rData = await rRes.json();
      const aData = await aRes.json();
      const gData = await gRes.json();
      setRoutines(Array.isArray(rData?.data) ? rData.data : []);
      const accs = Array.isArray(aData?.data) ? aData.data : [];
      setAccounts(accs);
      const gls = Array.isArray(gData?.data) ? gData.data : (Array.isArray(gData) ? gData : []);
      setGoals(gls);
      // Default to the first account only if nothing is selected yet.
      setAccountId((prev) => prev || (accs[0] ? oid(accs[0]) : ''));
    } catch (e) {
      // ignore
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const goalsForAccount = goals.filter((g) => oid(g.accountId) === accountId || g.accountId === accountId);

  async function create() {
    const amt = Number(amount);
    if (!accountId || !goalId || !(amt > 0)) { setMsg('Pick an account, a goal, and a positive amount.'); return; }
    setBusy(true); setMsg('');
    try {
      const r = await api('/routines/create', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: { accountId, goalId, amount: amt, dayOfMonth: Number(dayOfMonth) || 1 },
      });
      const d = await r.json();
      if (r.ok && d.success !== false) {
        setMsg('Routine created.');
        setAmount('');
        loadAll();
        onChange?.();
      } else {
        setMsg(d.message || 'Could not create routine.');
      }
    } catch (e) { setMsg(e.message); } finally { setBusy(false); }
  }

  async function runNow(id) {
    setBusy(true); setMsg('');
    try {
      const r = await api('/routines/run', { method: 'POST', headers: { 'content-type': 'application/json' }, body: { routineId: id } });
      const d = await r.json();
      setMsg(r.ok && d.success !== false ? 'Applied now.' : (d.message || 'Could not apply.'));
      loadAll();
      onChange?.();
    } catch (e) { setMsg(e.message); } finally { setBusy(false); }
  }

  async function remove(id) {
    setBusy(true); setMsg('');
    try {
      await api('/routines/delete', { method: 'POST', headers: { 'content-type': 'application/json' }, body: { routineId: id } });
      loadAll();
    } catch (e) { setMsg(e.message); } finally { setBusy(false); }
  }

  return (
    <div>
      <h3 style={{ marginBottom: 8 }}>Monthly auto-save routines</h3>

      <select className="select select--native" value={accountId}
        onChange={(e) => { setAccountId(e.target.value); setGoalId(''); }}
        style={{ width: '100%', marginBottom: 8 }}>
        {accounts.length === 0 ? <option value="">No accounts</option>
          : accounts.map((a) => <option key={oid(a)} value={oid(a)}>{a.name}</option>)}
      </select>

      <select className="select select--native" value={goalId}
        onChange={(e) => setGoalId(e.target.value)}
        disabled={!accountId || goalsForAccount.length === 0}
        style={{ width: '100%', marginBottom: 8 }}>
        <option value="">{goalsForAccount.length === 0 ? 'No goals in this account' : 'Select goal…'}</option>
        {goalsForAccount.map((g) => <option key={oid(g)} value={oid(g)}>{g.name}</option>)}
      </select>

      <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
        <input className="input" type="number" min="0" step="0.01" placeholder="Amount / month"
          value={amount} onChange={(e) => setAmount(e.target.value)} style={{ flex: 2 }} />
        <input className="input" type="number" min="1" max="28" placeholder="Day"
          value={dayOfMonth} onChange={(e) => setDayOfMonth(e.target.value)} style={{ flex: 1 }} title="Day of month (1-28)" />
      </div>
      <button className="btn btn--primary" onClick={create} disabled={busy}>
        {busy ? 'Saving…' : 'Create routine'}
      </button>

      <div style={{ marginTop: 12 }}>
        {routines.length === 0 ? (
          <div className="muted">No routines yet.</div>
        ) : routines.map((r) => (
          <div key={oid(r)} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid rgba(255,255,255,0.08)' }}>
            <div>
              <strong>{r.name}</strong> — ${Number(r.amount || 0).toFixed(2)} on day {r.dayOfMonth} each month
              {r.lastStatus && <div className="muted" style={{ fontSize: '0.8rem' }}>last run: {r.lastStatus}</div>}
            </div>
            <div style={{ display: 'flex', gap: 6 }}>
              <button className="btn btn--ghost" onClick={() => runNow(oid(r))} disabled={busy}>Run now</button>
              <button className="btn btn--ghost" onClick={() => remove(oid(r))} disabled={busy}>Delete</button>
            </div>
          </div>
        ))}
      </div>
      {msg && <div className="muted" style={{ marginTop: 8 }}>{msg}</div>}
    </div>
  );
}
