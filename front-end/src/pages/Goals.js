import React from "react";
import Header from "../components/Header";
import useFlashMessage from "../hooks/useFlashMessage";
import InlineNotice from "../components/InlineNotice";
import { oid, num, fmt } from "../utils/oid";
import { api } from "../api/client";
import { fetchWhoami } from "../api/whoami";

export default function GoalsPage() {
  const [accounts, setAccounts] = React.useState([]);
  const [goals, setGoals] = React.useState([]);
  const [accId, setAccId] = React.useState("");
  const [name, setName] = React.useState("");
  const [target, setTarget] = React.useState("");
  const [error, setError] = React.useState("");

  const flash = useFlashMessage(3600);

  // Add one-time console for debugging
  React.useEffect(() => {
    console.debug('whoami cookie present?', document.cookie.includes('auth='));
  }, []);

  // Handle 401 authentication errors locally without redirect
  const handleAuthError = async (endpoint) => {
    // Check session health without redirecting yet
    try {
      const who = await fetchWhoami();
      if (who.status === 401) {
        // real logout; allow ProtectedRoute to handle redirect (don't navigate here)
        setError('Session expired. Please sign in.');
        return true; // indicates real logout
      } else {
        // transient/handler-level auth hiccup; show inline message and stay
        setError(`Could not load ${endpoint} right now. Retrying…`);
        // Optionally retry once after a delay
        setTimeout(() => reload(), 3000);
        return false; // indicates transient error
      }
    } catch {
      // Network error during whoami check
      setError(`Could not load ${endpoint} right now. Retrying…`);
      setTimeout(() => reload(), 3000);
      return false;
    }
  };

  React.useEffect(() => { reload(); }, []);

  // --- reload(): normalize account ids robustly ---
  async function reload() {
    setError(""); // Clear any previous errors
    
    try {
      const a = await api('/accounts/list');
      if (a.status === 401) {
        await handleAuthError('accounts');
        return;
      }
      if (!a.ok) {
        console.error('Failed to load accounts:', a.status);
        setAccounts([]);
      } else {
        const body = await a.json();
        // Accepts either the wrapped shape {status, data:[...]} or a raw array
        const accountsRaw = Array.isArray(body) ? body : (Array.isArray(body?.data) ? body.data : []);
        const accountsNorm = accountsRaw
          .map(x => ({ _id: oid(x._id) || oid(x.id), name: x.name, balance: num(x.balance,0) }))
          .filter(x => !!x._id);
        setAccounts(accountsNorm);
        setAccId(prev => prev || accountsNorm[0]?._id || "");
      }
    } catch (error) {
      console.error('Error loading accounts:', error);
      setAccounts([]);
    }

    try {
      const g = await api('/goals/list');
      if (g.status === 401) {
        await handleAuthError('goals');
        return;
      }
      if (!g.ok) {
        console.error('Failed to load goals:', g.status);
        setGoals([]);
      } else {
        const body = await g.json();
        // /goals/list currently returns a raw array; also tolerate a wrapped shape
        const goalsRaw = Array.isArray(body) ? body : (Array.isArray(body?.data) ? body.data : []);
        const goalsNorm = goalsRaw.map(x => ({
          _id: oid(x._id) || oid(x.id),
          accountId: oid(x.accountId) || oid(x.account?._id),
          name: x.name || "",
          allocatedAmount: num(x.allocatedAmount,0),
          targetAmount: x.targetAmount != null ? num(x.targetAmount) : null,
        }));
        setGoals(goalsNorm);
      }
    } catch (error) {
      console.error('Error loading goals:', error);
      setGoals([]);
    }
  }

  // Helper function to check if a goal exists
  function goalExists(list, accountName, goalName) {
    if (!Array.isArray(list)) return false;
    return list.some(g => {
      const acc = g.accountName || g.account?.name || '';
      const name = g.goalName || g.name || '';
      return acc === accountName && name === goalName;
    });
  }

  // Helper function to fetch goals
  async function fetchGoalsList() {
    const r = await api('/goals/list');
    if (r.status === 401) {
      await handleAuthError('goals');
      return []; // Return empty array on auth errors
    }
    if (!r.ok) throw new Error(String(r.status));
    const data = await r.json();
    return Array.isArray(data) ? data : (data?.data || []);
  }

  // --- createGoal(): resilient to proxy/transport hiccups ---
  async function createGoal(e) {
    e.preventDefault();

    // Resolve the id from state or the accounts list
    const selected = accounts.find(a => a._id === accId) || accounts[0];
    const accountHex = oid(accId) || oid(selected?._id);

    if (!accountHex) {
      flash.flash("error", "Could not resolve an account id. Try refreshing Accounts, then come back.");
      return;
    }

    const accountName = selected?.name || "Unknown Account";
    const goalName = name.trim();

    try {
      const result = await createGoalCore({ accountName, goalName });
      
      if (result.authError) {
        if (result.isRealLogout) {
          // Real logout - ProtectedRoute will handle redirect
          return;
        }
        // Transient error - already handled by handleAuthError
        return;
      }
      
      if (result.success) {
        flash.flash("success", result.verified ? 
          "Goal created successfully (verified after connection issues)." : 
          "Goal created successfully.");
        setName(""); 
        setTarget("");
        setError(""); // Clear any previous errors
        await reload();
      }
    } catch (e) {
      flash.flash("error", e.message || String(e));
    }
  }

  // Core goal creation logic
  async function createGoalCore({ accountName, goalName }) {
    const res = await api('/goals/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: { accountName, goalName },
    });
    
    if (res.status === 401) {
      const isRealLogout = await handleAuthError('goals');
      return { success: false, authError: true, isRealLogout };
    }

    if (res.ok) {
      try {
        const data = await res.json();
        return data ?? { success: true };
      } catch {
        // Empty response body, treat as success
        return { success: true };
      }
    }

    // Dev proxy/transport hiccup: backend may have succeeded even though proxy says 500
    if (res.status >= 500) {
      try {
        const text = await res.text();
        if (typeof text === 'string' && text.startsWith('Proxy error')) {
          const list = await fetchGoalsList().catch(() => []);
          if (goalExists(list, accountName, goalName)) return { success: true, verified: true };
        }
      } catch {
        // Couldn't read response text, continue to error
      }
    }

    // Real failure
    try {
      const data = await res.json();
      const msg = (data && (data.message || data.error)) || `Failed (${res.status})`;
      throw new Error(msg);
    } catch (parseError) {
      // If JSON parsing failed, try to get text
      try {
        const text = await res.text();
        throw new Error(text || `Failed (${res.status})`);
      } catch {
        throw new Error(`Failed (${res.status})`);
      }
    }
  }

  const accountNameFor = id => accounts.find(a => a._id === id)?.name || "Savings";

  return (
    <>
      <Header />
      <div className="page">
        <div className="page__inner">
          {/* Create form */}
          <div className="card card--padded hero">
            <h2 className="card__title">Create a goal</h2>
            <form onSubmit={createGoal} className="row" style={{ gap:12, flexWrap:"wrap" }}>
              <select className="select" value={accId} onChange={e=>setAccId(e.target.value)} required style={{ minWidth:240 }}>
                {accounts.map(a => <option key={a._id} value={a._id}>{a.name} — ${fmt(a.balance)}</option>)}
              </select>
              <input className="input" placeholder="Goal name" value={name} onChange={e=>setName(e.target.value)} required style={{ minWidth:220 }}/>
              <input className="input" placeholder="Target (optional)" type="number" min="0" step="0.01" value={target} onChange={e=>setTarget(e.target.value)} style={{ minWidth:200 }}/>
              <button type="submit" className="btn btn--primary">Create</button>
            </form>
            {flash.text && <InlineNotice kind={flash.kind === "error" ? "error" : flash.kind}>{flash.text}</InlineNotice>}
            {error && <InlineNotice kind="error">{error}</InlineNotice>}
          </div>

          {/* List */}
          <div className="card card--padded">
            <h2 className="card__title">Your goals</h2>
            {goals.length === 0 ? (
              <div className="muted">No goals yet. Create your first goal above.</div>
            ) : (
              <table className="table">
                <thead><tr><th>Goal</th><th>Account</th><th>Allocated</th><th>Target</th></tr></thead>
                <tbody>
                  {goals.map(g => (
                    <tr key={g._id}>
                      <td>{g.name}</td>
                      <td><span className="pill">in {accountNameFor(g.accountId)}</span></td>
                      <td>${fmt(g.allocatedAmount)}</td>
                      <td>{g.targetAmount != null ? `$${fmt(g.targetAmount)}` : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </>
  );
}