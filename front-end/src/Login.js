import React, { useState } from "react";
import "./App.css";

export default function Login() {
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const [msg, setMsg] = useState({ type: "", text: "" });

  async function post(path, payload, opts = {}) {
    setPending(true);
    setMsg({ type: "", text: "" });
    
    console.log(`Making ${path} request with:`, payload);
    
    try {
      // Add a timeout to catch hanging requests
      const controller = new AbortController();
      const timeoutId = setTimeout(() => {
        console.log(`${path} request timed out after 10 seconds`);
        controller.abort();
      }, 10000);
      
      const res = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        credentials: 'include', // always include credentials
        signal: controller.signal,
      });
      
      clearTimeout(timeoutId);
      console.log(`${path} response status:`, res.status);
      console.log(`${path} response headers:`, Object.fromEntries(res.headers.entries()));
      
      // some endpoints may not return JSON; fall back safely
      let data = {};
      try {
        const responseText = await res.text();
        console.log(`${path} raw response text:`, responseText);
        
        if (responseText.trim()) {
          data = JSON.parse(responseText);
          console.log(`${path} parsed response data:`, data);
        } else {
          console.log(`${path} response is empty`);
        }
      } catch (jsonError) {
        console.log(`${path} response is not JSON:`, jsonError);
        data = {};
      }
      
      // Check for success: false in response data
      if (data.success === false) {
        console.log(`${path} returned success: false`);
        return { ok: false, data, status: res.status, error: data.message || "Request failed" };
      }
      
      if (res.ok) {
        console.log(`${path} request successful`);
        return { ok: true, data };
      }
      
      console.log(`${path} request failed with status:`, res.status);
      return { ok: false, data, status: res.status };
    } catch (e) {
      if (e.name === 'AbortError') {
        console.error(`${path} request was aborted (timeout)`);
        return { ok: false, data: null, error: "Request timed out" };
      }
      console.error(`${path} request error:`, e);
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
    
    console.log('Creating account for:', userName);
    const r = await post("/createUser", { userName, password });
    console.log('Create account response:', r);
    
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
    
    console.log('Attempting login for:', userName);
    const r = await post("/login", { userName, password });
    console.log('Login response:', r);
    
    if (r.ok) {
      // remember who is logged in for later use then hard redirect
      try { localStorage.setItem('userName', userName); } catch {}
      
      // Redirect immediately without showing success message
      try {
        console.log('Redirecting to /home...');
        window.location.href = '/home';
      } catch (redirectError) {
        console.error('Redirect failed:', redirectError);
        // Fallback: try using window.location.replace
        try {
          window.location.replace('/home');
        } catch (fallbackError) {
          console.error('Fallback redirect also failed:', fallbackError);
          setMsg({ type: "error", text: "Login successful but redirect failed. Please navigate to /home manually." });
        }
      }
    } else {
      console.error('Login failed:', r);
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
