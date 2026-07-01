import React, { useEffect, useMemo, useState } from "react";
import { api } from "../api/client";
import { oid, num, fmt } from "../utils/oid";
import InlineNotice from "./InlineNotice";

// Define first then export to avoid any tooling edge cases where an inline default
// declaration could transiently produce an empty module object during HMR.
function QuickActions({ onAnyChange }) {
  const [accounts, setAccounts] = useState([]);
  const [goals, setGoals] = useState([]);
  const [accountId, setAccountId] = useState("");
  const [goalId, setGoalId] = useState("");
  const [amount, setAmount] = useState("");
  const [mode, setMode] = useState("deposit");
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState({kind:"ok", text:""});
  const [transferToGoal, setTransferToGoal] = useState("");

  const setFlash = (kind, text) => {
    setMsg({ kind, text });
    setTimeout(() => setMsg({kind:"ok", text:""}), 3600);
  };

  useEffect(() => {
    (async () => {
      // 1) accounts
      let acc = [];
      console.log('QuickActions: Loading accounts...');
      try {
        let r = await api("/accounts/listWithAllocations");
        console.log('QuickActions: Accounts with allocations response status:', r.status);
        if (r.ok) {
          const data = await r.json();
          console.log('QuickActions: Accounts with allocations response data:', data);
          if (data.success === false) {
            console.error('Failed to load accounts with allocations:', data.message);
            throw new Error('Failed to load accounts with allocations');
          }
          acc = data;
        } else {
          throw new Error(`Failed to load accounts with allocations: ${r.status}`);
        }
      } catch (error) {
        console.error('Error loading accounts with allocations, trying fallback:', error);
        try {
          const r2 = await api("/accounts/list");
          console.log('QuickActions: Fallback accounts response status:', r2.status);
          if (r2.ok) {
            const data = await r2.json();
            console.log('QuickActions: Fallback accounts response data:', data);
            if (data.success === false) {
              console.error('Failed to load accounts:', data.message);
              acc = { data: [] };
            } else {
              acc = data;
            }
          } else {
            console.error('Failed to load accounts:', r2.status);
            acc = { data: [] };
          }
        } catch (fallbackError) {
          console.error('Error loading accounts fallback:', fallbackError);
          acc = { data: [] };
        }
      }
      acc = Array.isArray(acc.data) ? acc.data : Array.isArray(acc) ? acc : [];
      console.log('QuickActions: Processed accounts array:', acc);
      acc = acc.map(a => ({
        _id: oid(a._id) || oid(a.id),
        name: a.name || "Savings",
        balance: num(a.balance, 0),
        sumAllocated: num(a.sumAllocated, 0),
        unallocated: a.unallocated != null ? num(a.unallocated,0) : Math.max(0, num(a.balance,0) - num(a.sumAllocated,0)),
      })).filter(a => a._id);
      console.log('QuickActions: Final processed accounts:', acc);

      // 2) goals
      let gl = [];
      try {
        const r = await api("/goals/list");
        if (r.ok) {
          const data = await r.json();
          if (data.success === false) {
            console.error('Failed to load goals:', data.message);
            gl = { data: [] };
          } else {
            gl = data;
          }
        } else {
          console.error('Failed to load goals:', r.status);
          gl = { data: [] };
        }
      } catch (error) {
        console.error('Error loading goals:', error);
        gl = { data: [] };
      }
      gl = Array.isArray(gl.data) ? gl.data : Array.isArray(gl) ? gl : [];
      gl = gl.map(g => ({
        _id: oid(g._id) || oid(g.id),
        accountId: oid(g.accountId) || oid(g.account?._id),
        name: g.name || "Goal",
        allocatedAmount: num(g.allocatedAmount, 0),
      })).filter(g => g._id && g.accountId);

      // if sumAllocated missing, compute from goals
      if (acc.length && gl.length && acc.some(a => !a.sumAllocated)) {
        const sums = new Map();
        gl.forEach(g => sums.set(g.accountId, (sums.get(g.accountId)||0) + g.allocatedAmount));
        acc = acc.map(a => {
          const s = sums.get(a._id) || 0;
          return { ...a, sumAllocated: s, unallocated: Math.max(0, a.balance - s) };
        });
      }

      setAccounts(acc);
      setGoals(gl);

      // ✅ FIX: pick sensible defaults without using an out-of-scope "prev"
      const firstAccId = acc[0]?._id || "";
      const nextAccountId = accountId || firstAccId;
      setAccountId(nextAccountId);

      const firstGoalForAccount =
        gl.find(g => g.accountId === nextAccountId)?._id || "";
      const nextGoalId = goalId || firstGoalForAccount;
      setGoalId(nextGoalId);
    })();
  }, [accountId, goalId]);

  const accountGoals = useMemo(() => goals.filter(g => g.accountId === accountId), [goals, accountId]);
  useEffect(() => {
    // when account changes, pick first goal under it
    const first = accountGoals[0]?._id || "";
    setGoalId(first);
  }, [accountId]); // eslint-disable-line

  const account = useMemo(() => accounts.find(a => a._id === accountId), [accounts, accountId]);
  const unallocated = num(account?.unallocated, 0);
  const goalAlloc = num(accountGoals.find(g => g._id === goalId)?.allocatedAmount, 0);

  const amt = num(amount, NaN);
  const amtValid = Number.isFinite(amt) && amt > 0;
  const canDeposit  = mode === "deposit"  && accountId && goalId && amtValid && amt <= unallocated && !loading;
  const canWithdraw = mode === "withdraw" && accountId && goalId && amtValid && amt <= goalAlloc    && !loading;

  async function doAction(endpoint, okMsg) {
    setLoading(true);
    try {
      const body = endpoint === "/goals/transfer"
        // Backend expects fromGoalId/toGoalId — not goalId/transferToGoal.
        ? { accountId, fromGoalId: goalId, toGoalId: transferToGoal, amount: amt }
        : { accountId, goalId, amount: amt };
        
      const res = await api(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: body
      });
      const data = await res.json();
      if (!res.ok || data?.success === false) throw new Error(data?.message || data?.error || "Request failed");
      setAmount("");
      setTransferToGoal(""); // Reset transfer selection
      setFlash("ok", okMsg);
      onAnyChange?.();
      // refresh unallocated/allocated quickly
      setTimeout(() => window.location.reload(), 250); // simplest refresh; replace with a local re-fetch if you prefer
    } catch (e) {
      setFlash("error", e.message || String(e));
    } finally { setLoading(false); }
  }

  return (
    <div>
      <h3 style={{ marginBottom: 8 }}>Quick Actions</h3>

      {/* ACCOUNT SELECT */}
      <select
        className="select select--native"
        value={accountId}
        onChange={e => setAccountId(e.target.value)}
        disabled={accounts.length === 0}
        style={{ width: "100%", marginBottom: 10 }}
      >
        {accounts.length === 0
          ? <option value="">No accounts found</option>
          : accounts.map(a => (
              <option key={a._id} value={a._id}>
                {a.name} — ${fmt(a.balance)}
              </option>
            ))
        }
      </select>

      {/* GOAL SELECT */}
      <select
        className="select select--native"
        value={goalId}
        onChange={e => setGoalId(e.target.value)}
        disabled={!accountId || accountGoals.length === 0}
        style={{ width: "100%", marginBottom: 10 }}
      >
        {!accountId
          ? <option value="">Select an account first…</option>
          : accountGoals.length === 0
            ? <option value="">No goals in this account</option>
            : <>
                <option value="">Select goal…</option>
                {accountGoals.map(g => (
                  <option key={g._id} value={g._id}>{g.name} — allocated ${fmt(g.allocatedAmount)}</option>
                ))}
              </>
        }
      </select>

      {/* AMOUNT */}
      <input
        className="input"
        type="number" inputMode="decimal" min="0" step="0.01"
        placeholder={mode === "deposit" ? `Amount (≤ $${fmt(unallocated)})` : `Amount (≤ $${fmt(goalAlloc)})`}
        value={amount}
        onChange={e => setAmount(e.target.value)}
        style={{ width: "100%", marginBottom: 8 }}
      />

      {/* TOGGLE + ACTIONS */}
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        <button className={`btn ${mode==='deposit'?'btn--primary':'btn--ghost'}`} onClick={() => setMode("deposit")} disabled={loading}>Deposit</button>
        <button className={`btn ${mode==='withdraw'?'btn--primary':'btn--ghost'}`} onClick={() => setMode("withdraw")} disabled={loading}>Withdraw</button>
      </div>

      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginTop: 10 }}>
        <button className="btn btn--primary" onClick={() => doAction("/deposit", `Deposited $${fmt(amt)} successfully.`)} disabled={!canDeposit}>
          {loading && mode==='deposit' ? "Depositing…" : "Confirm deposit"}
        </button>
        <button className="btn" onClick={() => doAction("/withdraw", `Withdrew $${fmt(amt)} successfully.`)} disabled={!canWithdraw}>
          {loading && mode==='withdraw' ? "Withdrawing…" : "Confirm withdraw"}
        </button>
        <button className="btn btn--ghost" onClick={() => doAction("/goals/contribute", `Contributed $${fmt(amt)} to goal successfully.`)} disabled={!canDeposit || !goalId}>
          {loading ? "Contributing…" : "Contribute to Goal"}
        </button>
      </div>

      {/* Transfer between goals */}
      <div style={{ marginTop: 16, padding: 12, background: 'rgba(255,255,255,0.05)', borderRadius: 8 }}>
        <h4 style={{ margin: '0 0 8px 0', fontSize: '0.9rem' }}>Transfer between Goals</h4>
        <select 
          className="select select--native" 
          style={{ width: "100%", marginBottom: 8 }}
          onChange={e => setTransferToGoal(e.target.value)}
        >
          <option value="">Select goal to transfer to...</option>
          {goals.filter(g => g._id !== goalId).map(g => (
            <option key={g._id} value={g._id}>
              {g.name} — ${fmt(g.allocatedAmount)}
            </option>
          ))}
        </select>
        <button 
          className="btn btn--ghost" 
          style={{ width: "100%" }}
          onClick={() => doAction("/goals/transfer", `Transferred $${fmt(amt)} between goals successfully.`)} 
          disabled={!canDeposit || !goalId || !transferToGoal}
        >
          {loading ? "Transferring…" : "Transfer to Goal"}
        </button>
      </div>

      {msg.text && <InlineNotice kind={msg.kind === "error" ? "error" : "ok"}>{msg.text}</InlineNotice>}
    </div>
  );
}

QuickActions.displayName = 'QuickActions';

// Helpful runtime sanity check (will only log once in production build optimally stripped)
if (process.env.NODE_ENV !== 'production') {
  // eslint-disable-next-line no-console
  console.log('[QuickActions] module loaded: export is function =', typeof QuickActions === 'function');
}

export default QuickActions;
