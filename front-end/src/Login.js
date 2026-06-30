import React, { useState } from "react";
import "./App.css";

export default function Login() {
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const [msg, setMsg] = useState({ type: "", text: "" });

  async function post(path, payload) {
    setPending(true);
    setMsg({ type: "", text: "" });

    try {
      // Add a timeout to catch hanging requests
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 10000);

      const res = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        credentials: 'include', // always include credentials
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      // some endpoints may not return JSON; fall back safely
      let data = {};
      try {
        const responseText = await res.text();
        if (responseText.trim()) data = JSON.parse(responseText);
      } catch {
        data = {};
      }

      if (data.success === false) {
        return { ok: false, data, status: res.status, error: data.message || "Request failed" };
      }
      if (res.ok) {
        return { ok: true, data };
      }
      return { ok: false, data, status: res.status };
    } catch (e) {
      if (e.name === 'AbortError') {
        return { ok: false, data: null, error: "Request timed out" };
      }
      return { ok: false, data: null, error: e?.message || "Network error" };
    } finally {
      setPending(false);
    }
  }

  async function onCreate() {
    if (!userName || !password) {
      setMsg({ type: "error", text: "Enter a username and password" });
      return;
    }

    const r = await post("/createUser", { userName, password });

    if (r.ok) {
      try { localStorage.setItem('userName', userName); } catch {}
      setMsg({ type: "success", text: r.data?.message || "Account created" });
      setPassword("");
    } else {
      setMsg({ type: "error", text: r.data?.message || r.error || "Could not create account" });
    }
  }

  async function onLogin() {
    if (!userName || !password) {
      setMsg({ type: "error", text: "Enter a username and password" });
      return;
    }

    const r = await post("/login", { userName, password });

    if (r.ok) {
      // remember who is logged in for later use then hard redirect
      try { localStorage.setItem('userName', userName); } catch {}

      // Redirect immediately without showing success message
      try {
        window.location.href = '/home';
      } catch {
        // Fallback: try using window.location.replace
        try {
          window.location.replace('/home');
        } catch {
          setMsg({ type: "error", text: "Login successful but redirect failed. Please navigate to /home manually." });
        }
      }
    } else {
      setMsg({ type: "error", text: r.data?.message || r.error || "Login failed. Check your credentials." });
    }
  }

  return (
    <div className="auth-wrapper">
      <div className="card card--auth">
        <h1 style={{ textAlign: 'center' }}>Savings App</h1>
        <p className="sub" style={{ textAlign: 'center' }}>Sign in or create your account to start tracking your savings today!</p>

        <div className="field">
          <label className="label">Username</label>
          <input
            className="input"
            value={userName}
            onChange={(e) => setUserName(e.target.value)}
            placeholder="e.g. Ranjiv"
            autoComplete="username"
            disabled={pending}
          />
        </div>

        <div className="field">
          <label className="label">Password</label>
          <input
            className="input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            autoComplete="current-password"
            disabled={pending}
          />
        </div>

        {msg.text && (
          <div className={`alert ${msg.type === "success" ? "success" : "error"}`}>
            {msg.text}
          </div>
        )}

        <div className="actions">
          <button className="button" onClick={onLogin} disabled={pending}>
            {pending ? "Please wait…" : "Login"}
          </button>
          <button className="button ghost" onClick={onCreate} disabled={pending}>
            {pending ? "Please wait…" : "Create Account"}
          </button>
        </div>

        <div className="helper">By continuing, you agree to the terms.</div>
      </div>
    </div>
  );
}
