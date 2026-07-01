import React from 'react';
import Header from '../components/Header';
import { api } from '../api/client';
import { idOf } from '../utils/oid';

function StackedGoalBar({ balance, goals, account }) {
  const total = Math.max(0, Number(balance || 0));

  // If account has rich allocation data, use it directly
  if (account?.allocations && Array.isArray(account.allocations)) {
    const allocated = Number(account.sumAllocated || 0);
    const unallocated = Number(account.unallocated || Math.max(0, total - allocated));
    const palette = ['#D7A54A', '#86C0A1', '#CF7052', '#E8C06A', '#9DB17C', '#C98A5E', '#7FB0A0'];

    return (
      <div>
        <div className="stackbar" title={`Allocated: $${allocated.toFixed(2)} / $${total.toFixed(2)}`}>
          {account.allocations.map((alloc, i) => {
            const amt = Number(alloc.allocatedAmount || 0);
            const width = total > 0 ? (amt / total) * 100 : 0;
            return (
              <div
                key={alloc.goalId || i}
                className="stackbar__seg"
                style={{ width: `${width}%`, background: palette[i % palette.length] }}
                title={`${alloc.goalName}: $${amt.toFixed(2)} (${total ? Math.round(width) : 0}%)`}
              />
            );
          })}
          {unallocated > 0 && (
            <div
              className="stackbar__seg unalloc"
              style={{ width: `${total ? (unallocated / total) * 100 : 0}%` }}
              title={`Unallocated: $${unallocated.toFixed(2)}`}
            />
          )}
        </div>

        <div className="legend">
          {account.allocations.map((alloc, i) => (
            <span key={alloc.goalId || i} className="legend__item">
              <i className="legend__dot" style={{ background: palette[i % palette.length] }} />
              {alloc.goalName} — ${Number(alloc.allocatedAmount || 0).toFixed(2)}
            </span>
          ))}
          {unallocated > 0 && (
            <span className="legend__item">
              <i className="legend__dot unalloc" />
              Unallocated — ${unallocated.toFixed(2)}
            </span>
          )}
        </div>
      </div>
    );
  }

  // Fallback to old logic for basic /accounts/list response
  const extractAllocated = (g) => {
    const candidates = [
      g.allocatedAmount,
      g.amountAllocated,
      g.balance,
      g.currentBalance,
      g.currentAmount,
      g.savedAmount,
      g.amount,
      g.value,
      g.amountCents != null ? g.amountCents / 100 : undefined,
      g.valueCents != null ? g.valueCents / 100 : undefined,
    ];
    for (const v of candidates) {
      if (v != null && !isNaN(v) && Number(v) > 0) return Number(v);
    }
    return 0;
  };

  const allocatedAmounts = goals.map(extractAllocated);
  const allocated = allocatedAmounts.reduce((a, b) => a + b, 0);
  const unallocated = Math.max(0, total - allocated);
  const palette = ['#D7A54A', '#86C0A1', '#CF7052', '#E8C06A', '#9DB17C', '#C98A5E', '#7FB0A0'];

  return (
    <div>
      <div className="stackbar" title={`Allocated: $${allocated.toFixed(2)} / $${total.toFixed(2)}`}>
        {goals.map((g, i) => {
          const amt = allocatedAmounts[i];
          const width = total > 0 ? (amt / total) * 100 : 0;
          return (
            <div
              key={idOf(g)}
              className="stackbar__seg"
              style={{ width: `${width}%`, background: palette[i % palette.length] }}
              title={`${g.name || g.goal?.name}: $${amt.toFixed(2)} (${total ? Math.round(width) : 0}%)`}
            />
          );
        })}
        {unallocated > 0 && (
          <div
            className="stackbar__seg unalloc"
            style={{ width: `${total ? (unallocated / total) * 100 : 0}%` }}
            title={`Unallocated: $${unallocated.toFixed(2)}`}
          />
        )}
      </div>

      <div className="legend">
        {goals.map((g, i) => (
          <span key={idOf(g)} className="legend__item">
            <i className="legend__dot" style={{ background: palette[i % palette.length] }} />
            {(g.name || g.goal?.name)} — ${allocatedAmounts[i].toFixed(2)}
          </span>
        ))}
        {unallocated > 0 && (
          <span className="legend__item">
            <i className="legend__dot unalloc" />
            Unallocated — ${unallocated.toFixed(2)}
          </span>
        )}
      </div>
    </div>
  );
}

export default function Accounts() {
  const [accounts, setAccounts] = React.useState([]);
  const [goals, setGoals] = React.useState([]);
  const [transactions, setTransactions] = React.useState([]);

  const [name, setName] = React.useState('');
  const [initialBalance, setInitialBalance] = React.useState('');

  async function loadTransactions() {
    const candidates = ['/transactions', '/transactions/list'];
    for (const url of candidates) {
      try {
        const r = await api(url);
        if (!r.ok) continue;
        const data = await r.json();
        const raw = data?.data || data?.transactions || data || [];
        if (Array.isArray(raw)) { setTransactions(raw); break; }
      } catch (error) {
        console.error(`Error loading transactions from ${url}:`, error);
        continue;
      }
    }
  }

  const reload = React.useCallback(async () => {
    // Try rich endpoint first, fall back to basic
    let accountsData = [];
    try {
      const r = await api('/accounts/listWithAllocations');
      if (r.ok) {
        const data = await r.json();
        accountsData = data?.data || [];
      }
    } catch (error) {
      console.error('Error loading accounts with allocations:', error);
    }

    if (!accountsData.length) {
      try {
        const r = await api('/accounts/list');
        if (r.ok) {
          const data = await r.json();
          accountsData = data?.data || [];
        }
      } catch (error) {
        console.error('Error loading accounts:', error);
      }
    }

    setAccounts(accountsData);

    const [g] = await Promise.all([
      api('/goals/list').then(async (r) => {
        if (r.ok) {
          const data = await r.json();
          if (data.success === false || data.status === false) return { data: [] };
          return data;
        }
        return { data: [] };
      }).catch(() => ({ data: [] })),
    ]);
    setGoals(g?.data || []);
    await loadTransactions();
  }, []);

  React.useEffect(() => { reload(); }, [reload]);

  const goalsByAccount = React.useMemo(() => {
    const map = {};
    for (const goal of goals) {
      const aid = idOf(goal.accountId);
      if (!aid) continue;
      (map[aid] ||= []).push({ ...goal, id: idOf(goal), accountId: aid });
    }
    return map;
  }, [goals]);

  // Build a computed allocation map from transactions if backend didn't send allocatedAmount.
  const computedAllocMap = React.useMemo(() => {
    if (!transactions.length) return {};
    const map = {};
    for (const t of transactions) {
      // Derive amount
      const cents = t.amountCents ?? t.valueCents ?? null;
      let raw = t.amount ?? t.value ?? t.delta ?? t.change ?? (cents != null ? cents / 100 : null);
      if (raw == null) raw = t.depositAmount ?? (t.withdrawAmount != null ? -Math.abs(t.withdrawAmount) : null);
      if (raw == null && typeof t.amountStr === 'string') {
        const m = t.amountStr.replace(/[$,]/g,'');
        const num = Number(m); if (!Number.isNaN(num)) raw = num;
      }
      if (raw == null) continue;
      const amt = Number(raw);
      const gid = idOf(t.goalId || t.goal?.id || t.goal?._id || t.goal?._id?.$oid || t.goal); // broad fallback
      if (!gid) continue;
      map[gid] = (map[gid] || 0) + amt;
    }
    return map;
  }, [transactions]);

  async function createAccount() {
    if (!name.trim()) return;

    try {
      const response = await api('/accounts/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: {
          name: name.trim(),
          type: 'savings',
          initialBalance: Number(initialBalance || 0),
        },
      });

      const data = await response.json();

      if (data.success === false || data.status === false || !response.ok) {
        console.error('Failed to create account:', data.message || response.status);
        return;
      }

      setName('');
      setInitialBalance('');
      await reload();
    } catch (error) {
      console.error('Error creating account:', error);
    }
  }

  return (
    <>
      <Header />
      <div className="page">
        <div className="page__inner">
          <h1>Accounts</h1>
          <div className="card card--padded">
            <h3>Create a savings account</h3>
            <div className="row">
              <input
                placeholder="Account name"
                value={name}
                onChange={e => setName(e.target.value)}
              />
              <input
                placeholder="Initial amount (optional)"
                type="number"
                step="0.01"
                value={initialBalance}
                onChange={e => setInitialBalance(e.target.value)}
              />
              <button className="btn btn-primary" onClick={createAccount}>Create</button>
            </div>
          </div>
          <div className="card card--padded">
            <h3>Connect your bank</h3>
            <p className="muted">Hook up your external bank to view balances. (Plaid integration placeholder)</p>
            <button className="btn">Connect with Plaid</button>
          </div>
          <div className="grid">
            {accounts.map(a => {
              const aid = idOf(a);
              const list = (goalsByAccount[aid] || []).map(g => {
                if ((g.allocatedAmount ?? g.amountAllocated) == null) {
                  const computed = computedAllocMap[g.id];
                  if (computed != null) return { ...g, allocatedAmount: computed };
                }
                return g;
              });
              return (
                <div key={aid} className="card card--padded">
                  <div className="card__title">
                    <div>{a.name}</div>
                    <span className="pill">savings</span>
                  </div>
                  <div className="muted">Balance: ${Number(a.balance || 0).toFixed(2)}</div>
                  <StackedGoalBar balance={a.balance} goals={list} account={a} />
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </>
  );
}
