# Savings App — Complete Setup & Deployment Guide

This walks you from "code on GitHub" to "live app on the internet with bank linking."
You do **not** need Docker for the Railway deploy — Railway builds the image in the cloud.

Three things you'll create (all free): a **MongoDB Atlas** database, **Plaid** sandbox
keys, and a **Railway** project. Estimated time: ~25 minutes.

---

## Part 0 — Get the code onto `main`

The work is on branch `savings-app-deploy`. Merge it:

1. Go to https://github.com/Ranj04/Savings-App
2. Open the Pull Request (banner near the top, or the "Pull requests" tab).
3. **Merge pull request → Confirm merge.**

(Or skip merging and just deploy the `savings-app-deploy` branch in Part 3.)

---

## Part 1 — MongoDB Atlas (the database)

1. Go to https://www.mongodb.com/cloud/atlas/register and sign up (free).
2. **Create a cluster** → choose **M0 (Free)** → pick any cloud/region near you → **Create**.
3. **Database Access** (left sidebar) → **Add New Database User**:
   - Authentication: Password.
   - Username + Password — **write these down** (avoid special characters like `@ / :` in the password to keep the URL simple).
   - Built-in role: **Read and write to any database** → **Add User**.
4. **Network Access** (left sidebar) → **Add IP Address** → **Allow Access From Anywhere** (`0.0.0.0/0`) → **Confirm**. (Railway's outbound IPs vary, so this is required.)
5. **Database** (left sidebar) → **Connect** → **Drivers** → copy the connection string. It looks like:
   ```
   mongodb+srv://USERNAME:PASSWORD@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
   ```
   Replace `USERNAME`/`PASSWORD` with the user you created. **Save this — it's your `MONGO_URL`.**

---

## Part 2 — Plaid (bank linking)

1. Go to https://dashboard.plaid.com/signup and create a free account.
2. You start in **Sandbox** automatically (fake banks, no real money, no approval needed).
3. Left sidebar → **Developers → Keys**.
4. Copy two values:
   - **client_id**
   - **Sandbox secret** (the secret in the "Sandbox" row)
   **Save both.**

That's all Plaid needs. (No redirect URI required for sandbox linking.)

---

## Part 3 — Deploy on Railway

1. Go to https://railway.app and log in.
2. **New Project → Deploy from GitHub repo.**
   - If prompted, authorize Railway to access your GitHub and select **Savings-App**.
3. Railway reads `railway.json` and builds the root `Dockerfile` automatically — a single
   service that serves both the API and the website. Let the first build run (it may fail
   to start until you add the variables below — that's expected).
4. Open the service → **Variables** tab → add these (New Variable for each):

   | Variable | Value |
   |---|---|
   | `MONGO_URL` | your Atlas connection string from Part 1 |
   | `MONGO_DB` | `savings` |
   | `APP_ENV` | `production` |
   | `PLAID_CLIENT_ID` | your client_id from Part 2 |
   | `PLAID_SECRET` | your Sandbox secret from Part 2 |
   | `PLAID_ENV` | `sandbox` |
   | `PLAID_TOKEN_KEY` | any random 32+ character string (e.g. mash the keyboard) |

   **Do not** add `PORT` — Railway sets it automatically and the app reads it.
5. Railway redeploys automatically after you add variables. Wait for **"Success / Active."**
6. Service → **Settings → Networking → Generate Domain.** Pick the suggested port if asked.
   You now have a URL like `https://savings-app-production.up.railway.app`. **That's your app.**

> Deploying the branch instead of main? Service → **Settings → Source** → set branch to
> `savings-app-deploy`.

---

## Part 4 — Try it

1. Open your Railway URL.
2. **Create account** (any username/password).
3. **Add income** — make a manual account (e.g. "Checking") and add $2,000.
4. **Link a bank** — click it, choose any sandbox bank, log in with **`user_good`** /
   **`pass_good`**. Balances import automatically.
5. **Create goals** (Hawaii, New Car…), **set money aside**, and add a **monthly routine**.
   You'll see usable vs set-aside update live.

---

## Optional — Run locally with Docker

Only needed if you want to test in containers on your machine. Docker Desktop is installed
but needs its backend finished:

1. **PowerShell as Administrator** → `wsl --install`
2. **Reboot.**
3. Launch **Docker Desktop** once; accept terms; wait for "Engine running."
4. In the repo root:
   ```
   copy .env.example .env      # add Plaid keys if you want linking
   docker compose up --build
   ```
5. Open http://localhost:3000.

## Optional — Run locally without Docker

You already have Java 21, Maven, Node, and MongoDB. From the repo root:
```
npm run setup     # one time
npm start         # backend on :1299, frontend on :3000 (or 3001 if 3000 is busy)
```

---

## Troubleshooting

- **Login seems to do nothing in production:** make sure `APP_ENV=production` is set (it
  enables Secure cookies, required over HTTPS) and you're using the `https://` Railway URL.
- **"Bank linking is not configured":** `PLAID_CLIENT_ID` / `PLAID_SECRET` aren't set on the
  service, or the deploy didn't pick them up — re-check Variables and redeploy.
- **Can't connect to the database / 500s on every call:** the Atlas user/password in
  `MONGO_URL` is wrong, or Network Access doesn't include `0.0.0.0/0`.
- **Build fails on Railway:** confirm it's building the **root** `Dockerfile` (it should, via
  `railway.json`). Check the build logs for the failing stage.
- **Health check:** the service exposes `/health` (returns `ok`) — Railway uses it to confirm
  the app is up.
