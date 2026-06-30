# Running & Deploying the Savings App

The app has three parts: a React frontend, a Java backend, and MongoDB. The
easiest way to run all of them — locally or on a server — is Docker Compose.

## 1. Run the whole stack locally (one command)

Prereqls: Docker Desktop.

```bash
cp .env.example .env        # optional: add Plaid keys for bank linking
npm run docker              # = docker compose up --build
```

Open **http://localhost:4000**. The nginx frontend reverse-proxies all API calls
to the backend, so everything is one origin (no CORS, cookies just work). The web
port defaults to `4000` to avoid clashing with other local dev servers; override
it with `WEB_PORT=5000 npm run docker`.

To stop: `Ctrl-C`, then `npm run docker:down` (or `npm run docker:reset` to also
wipe the DB).

### Without Docker (dev mode)

```bash
npm run setup     # installs the runner + frontend deps
npm run dev       # backend on :1299, React dev server on :4000
```
Requires Node 18+, a Java 21 JDK (`brew install openjdk@21` — the launcher finds
it automatically, no `JAVA_HOME` needed), and a reachable MongoDB (local,
Atlas via `MONGO_URL`, or `docker compose up mongo` for just the database).

## 2. Enable bank linking (Plaid)

1. Create a free account at https://dashboard.plaid.com and copy your
   **client_id** and **Sandbox secret**.
2. Put them in `.env`:
   ```
   PLAID_CLIENT_ID=...
   PLAID_SECRET=...
   PLAID_ENV=sandbox
   PLAID_TOKEN_KEY=<openssl rand -base64 32>   # encrypts access tokens at rest
   ```
3. `docker compose up --build`. Click **Link a bank** and use Plaid's sandbox
   credentials: username `user_good`, password `pass_good`.

Balances are read-only — we never move money in the real bank. Goals/partitions
live entirely in this app, exactly as intended.

## 3. Deploy to a public URL (Railway, single service)

The **root `Dockerfile`** builds a single image where the Java backend serves both
the API and the bundled React app on one port — one origin, so the `SameSite=Lax`
session cookie just works. `railway.json` points Railway at it. Use **MongoDB Atlas**
(free) for the database.

### Step 1 — MongoDB Atlas (free)
1. Create a free **M0** cluster at https://cloud.mongodb.com.
2. Database Access → add a user (username + password).
3. Network Access → allow `0.0.0.0/0` (Railway egress IPs vary).
4. Copy the connection string → this is `MONGO_URL`
   (`mongodb+srv://user:pass@cluster.xxxx.mongodb.net/?retryWrites=true&w=majority`).

### Step 2 — Plaid keys
Plaid dashboard → Developers → Keys → copy **client_id** and the **Sandbox secret**.

### Step 3 — Railway
1. Push this repo to GitHub.
2. railway.app → **New Project → Deploy from GitHub repo** → pick this repo.
   Railway reads `railway.json` and builds the root `Dockerfile`.
3. Service → **Variables**, add:
   | Var | Value |
   |---|---|
   | `MONGO_URL` | your Atlas string |
   | `MONGO_DB` | `savings` |
   | `APP_ENV` | `production` |
   | `PLAID_CLIENT_ID` / `PLAID_SECRET` | your Plaid creds |
   | `PLAID_ENV` | `sandbox` |
   | `PLAID_TOKEN_KEY` | any 32+ char random string |

   (`PORT` is injected by Railway automatically — the backend reads it.)
4. Service → **Settings → Networking → Generate Domain**. That URL is your app.

Health check path is `/health` (already configured).

### Notes
- `APP_ENV=production` enables Secure cookies — correct on Railway (HTTPS). Don't set
  it for plain-HTTP local runs or logins will appear to fail.
- The backend connects to Mongo lazily, so boot order doesn't matter.
- The routine scheduler runs in-process, so it fires while the service is up.
- The separate `back-end/Dockerfile`, `front-end/Dockerfile`, and `docker-compose.yml`
  are for **local** multi-container runs; Railway uses the single root `Dockerfile`.
