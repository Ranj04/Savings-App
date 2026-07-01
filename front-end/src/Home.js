import React from 'react';
import { useEffect } from 'react';
import Header, { useUsername } from './components/Header';
import QuickActions from './components/QuickActions';
import LinkBankButton from './components/LinkBankButton';
import AddFunds from './components/AddFunds';
import Routines from './components/Routines';
import { listGoals } from './api/goalsApi';
import { possessive } from './utils/possessive';
import { api } from './api/client';

export default function Home() {
    const username = useUsername();
    const [txns, setTxns] = React.useState([]);
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState('');

    // Compute personalized title and welcome message
    const owner = username || '';
    const pageTitle = owner ? `${possessive(owner)} Personal Finance App` : 'Personal Finance App';
    const welcome = owner ? `Welcome, ${owner}` : 'Welcome, Friend';

    // Set document title
    useEffect(() => { 
        document.title = pageTitle; 
    }, [pageTitle]);

    const loadRecent = React.useCallback(async function loadRecent() {
        const candidates = ['/transactions', '/transactions/list'];
        for (const url of candidates) {
            try {
                const r = await api(url);
                if (!r.ok) {
                    if (r.status === 401) {
                        // Don't crash on 401 - just show a small message
                        setError('Please sign in to view transactions');
                        return;
                    }
                    continue;
                }
                const data = await r.json();
                const raw = data?.data || data?.transactions || data || [];
                if (!Array.isArray(raw)) continue;

                const normalized = raw.map(t => {
                    // numeric amount
                    const cents = t.amountCents ?? t.valueCents ?? null;
                    let rawAmt = t.amount ?? t.value ?? (cents != null ? cents / 100 : null);
                    if (rawAmt == null && typeof t.amountStr === 'string') {
                        const n = Number(t.amountStr.replace(/[$,]/g, '')); if (!Number.isNaN(n)) rawAmt = n;
                    }
                    if (rawAmt == null) rawAmt = 0;
                    const type = (t.type || t.transactionType || '').toLowerCase();

                    // prefer enriched names from backend
                    const displayAccount = t.displayAccount || t.accountName || t.account?.name || t.account || '';
                    const displayGoal = t.displayGoal || t.goalName || t.goal?.name || t.goal || '';

                    // withdraw should display negative
                    const signed = type === 'withdraw' ? -Math.abs(Number(rawAmt || 0)) : Number(rawAmt || 0);

                    return {
                        id: t.id || t._id?.$oid || t._id || crypto.randomUUID(),
                        type: type || (signed >= 0 ? 'deposit' : 'withdraw'),
                        amount: signed,
                        accountName: displayAccount,
                        goalName: displayGoal,
                        createdAt: Number(t.createdAt ?? t.timestamp ?? Date.parse(t.date) ?? Date.now()),
                    };
                });

                normalized.sort((a, b) => b.createdAt - a.createdAt);
                setTxns(normalized.slice(0, 5));
                setError(''); // Clear any previous errors
                return;
            } catch (error) {
                console.error(`Error loading transactions from ${url}:`, error);
                continue;
            }
        }
        setTxns([]);
        setError('Unable to load recent transactions');
    }, []);

    const reload = React.useCallback(async function reload() {
        setLoading(true);
        setError('');
        
        try {
            // Try accounts/listWithAllocations first, fallback to accounts/list
            let accountsData = [];
            try {
                const accRes = await api('/accounts/listWithAllocations');
                if (accRes.ok) {
                    const data = await accRes.json();
                    if (data && data.success !== false) {
                        accountsData = data?.data || [];
                    }
                } else if (accRes.status === 401) {
                    // Don't crash on 401 - just log and continue
                    console.log('Accounts endpoint returned 401, will retry later');
                }
            } catch (error) {
                console.error('Error loading accounts with allocations:', error);
            }
            
            // Fallback to regular accounts list if needed
            if (accountsData.length === 0) {
                try {
                    const accRes = await api('/accounts/list');
                    if (accRes.ok) {
                        const data = await accRes.json();
                        if (data && data.success !== false) {
                            accountsData = data?.data || [];
                        }
                    } else if (accRes.status === 401) {
                        // Don't crash on 401 - just log and continue
                        console.log('Accounts fallback endpoint returned 401, will retry later');
                    }
                } catch (error) {
                    console.error('Error loading accounts fallback:', error);
                }
            }
            
            if (accountsData.length === 0) {
                setError('Unable to load accounts');
            }
        } catch (error) {
            console.error('Error in accounts loading:', error);
            setError('Failed to load account information');
        }
        
        try {
            await listGoals();
        } catch (error) {
            console.error('Error loading goals:', error);
            setError('Failed to load goals');
        }
        
        await loadRecent();
        setLoading(false);
    }, [loadRecent]);

    React.useEffect(() => { reload(); }, [reload]);

    return (
        <>
            <Header titleOverride={pageTitle} />
            <div className="page">
                <div className="page__inner">
                    <h1 style={{ marginBottom: '20px' }}>{welcome}!</h1>
                    <div className="card card--padded">
                        <h3 style={{ marginBottom: 8 }}>Your bank</h3>
                        <p className="muted" style={{ marginTop: 0 }}>
                            Link a real bank to import balances, or use a manual account and add income below.
                        </p>
                        <LinkBankButton onLinked={reload} />
                    </div>
                    <div className="card card--padded">
                        <AddFunds onChange={reload} />
                    </div>
                    <div className="card card--padded">
                        <QuickActions onAnyChange={loadRecent} />
                    </div>
                    <div className="card card--padded">
                        <Routines onChange={loadRecent} />
                    </div>
                    <div className="card card--padded">
                        <h3>Recent activity</h3>
                        {loading ? (
                            <div className="muted">Loading transactions...</div>
                        ) : error ? (
                            <div className="muted">{error}</div>
                        ) : txns.length === 0 ? (
                            <div className="muted">No transactions yet.</div>
                        ) : (
                            <table className="txn-table">
                                <thead>
                                    <tr>
                                        <th>Amount</th><th>Type</th><th>Account</th><th>Goal</th><th>Time</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {txns.map(t => {
                                        const amtClass = t.amount > 0 ? 'amt-pos' : (t.amount < 0 ? 'amt-neg' : '');
                                        return (
                                            <tr key={t.id}>
                                                <td className={amtClass}>{t.amount >= 0 ? '+' : '-'}${Math.abs(t.amount).toFixed(2)}</td>
                                                <td>{t.type}</td>
                                                <td>{t.accountName}</td>
                                                <td>{t.goalName}</td>
                                                <td>{new Date(t.createdAt).toLocaleString()}</td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        )}
                    </div>
                </div>
            </div>
        </>
    );
}
