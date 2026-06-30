# Savings App

A full-stack personal-savings application built around a **savings-envelope
model**: money lives in accounts, and you "set aside" portions of it into named
goals without ever moving it out of the account. Every balance operation is
atomic and concurrency-safe, so usable funds and goal allocations can never drift
out of sync.

The backend is a Java 21 service built directly on the JDK HTTP server (no Spring,
no servlet container); the frontend is a React 18 single-page app. In production
the Java server also serves the compiled React bundle, so the whole app runs from
a single origin.

> Architecture deep-dive: **[ARCHITECTURE.md](ARCHITECTURE.md)**.

---

## Highlights

- **Envelope budgeting** — accounts track `balance` and `sumAllocated`; goals
  hold their slice of the allocation. `usable = balance − sumAllocated` is what
  you can freely spend or set aside.
- **Atomic, race-free money movement** — all transfers go through a single
  `MoneyService` that uses MongoDB conditional updates (guard + mutation in one
  operation) and compensates on partial failure, preserving the invariant
  `sumAllocated == Σ(goal.allocatedAmount)`.
- **Recurring auto-saves** — a background scheduler applies monthly "set aside"
  routines and safely skips (then retries) when an account lacks usable funds.
- **Bank linking via Plaid** — optionally link a real bank to sync balances;
  access tokens are encrypted at rest.
- **Secure sessions** — `HttpOnly`, `SameSite=Lax` cookie sessions with
  PBKDF2-HMAC-SHA256 password hashing and transparent upgrade of legacy hashes.
- **One-command dev and single-image deploy** — run both tiers together locally;
  ship as one container that serves API + SPA on the same origin.

---

## Tech stack

| Area | Choices |
|------|---------|
| Frontend | React 18 (Create React App), React Router 6, `fetch`, cookie auth |
| Backend | Java 21, `com.sun.net.httpserver` HTTP server, Gson, MongoDB Java driver |
| Database | MongoDB (local, Docker, or Atlas) |
| Auth | `HttpOnly` session cookie + PBKDF2-HMAC-SHA256 password hashing |
| Integrations | Plaid (sandbox by default) |
| Tests | Backend: TestNG + Mockito · Frontend: Jest + React Testing Library |
| Deploy | Docker (single-image or docker-compose), Railway |

---

## Repository layout

```
.
├── back-end/                      # Java 21 API + static server
│   ├── pom.xml
│   └── src/main/java/
│       ├── server/                # HTTP server, routing, static serving
│       ├── handler/               # one class per endpoint (accounts/ goals/ routines/ plaid/)
│       ├── service/               # MoneyService, RoutineScheduler
│       ├── security/              # PasswordUtil (PBKDF2), TokenUtil, CryptoUtil
│       ├── plaid/                 # Plaid client + account sync
│       ├── dao/                   # BaseDao + per-collection DAOs, MongoConnection
│       ├── dto/                   # document ↔ object mapping
│       ├── request/ response/     # request parsing, response building
│       └── src/test/java/         # TestNG + Mockito suite
├── front-end/                     # React single-page app
│   └── src/
│       ├── index.js               # router (Login / Home / Goals / Accounts)
│       ├── pages/ components/      # Accounts, Goals, QuickActions, AddFunds, Routines, ...
│       └── api/client.js          # fetch wrapper (credentials + JSON)
├── Dockerfile                     # single-image build (Java serves API + SPA)
├── docker-compose.yml             # Mongo + backend + nginx frontend
├── railway.json                   # Railway deploy config
├── ARCHITECTURE.md
└── package.json                   # root runner (concurrently)
```

---

## Quick start (development)

**Prerequisites**

- Node 18+ and npm
- Java 21 (JDK) and Maven
- MongoDB reachable at `mongodb://localhost:27017` (or set `MONGO_URL`)

**Run both tiers with one command** (from the repo root):

```bash
npm run setup    # first time only: installs the runner + frontend deps
npm start        # starts the Java backend (:1299) and the React dev server (:3000)
```

`npm start` uses [concurrently](https://www.npmjs.com/package/concurrently) to run:

- `start:backend` → `mvn -f back-end/pom.xml compile exec:java` (runs `server.Server` on `:1299`)
- `start:frontend` → the CRA dev server on `:3000`

CRA's `proxy` setting (`front-end/package.json` → `http://localhost:1299`)
forwards API calls to the backend, so the two run as one origin in dev. Press
`Ctrl-C` once to stop both.

**Run the tiers separately** (alternative):

```bash
# Backend — from back-end/
mvn -q compile exec:java

# Frontend — from front-end/
npm install && npm start      # http://localhost:3000
```

**Full stack in Docker** (includes MongoDB):

```bash
cp .env.example .env          # fill in Plaid keys only if you want bank linking
docker compose up --build     # then open http://localhost:3000
```

---

## Configuration

All values are optional for the core app; Plaid values are only needed for real
bank linking. Copy `.env.example` to `.env`.

| Variable | Default | Purpose |
|----------|---------|---------|
| `PORT` | `1299` | Backend HTTP port |
| `MONGO_URL` | `mongodb://localhost:27017` | MongoDB connection string |
| `MONGO_DB` | `savings` | Database name |
| `APP_ORIGIN` | `http://localhost:3000` | Allowed CORS origin for the SPA (dev) |
| `APP_ENV` | _(unset)_ | Set to `production` to add the `Secure` cookie flag (HTTPS) |
| `STATIC_DIR` | _(unset)_ | Directory of the built SPA; set in the single-image deploy |
| `PLAID_CLIENT_ID` | _(unset)_ | Plaid client id |
| `PLAID_SECRET` | _(unset)_ | Plaid secret |
| `PLAID_ENV` | `sandbox` | Plaid environment |
| `PLAID_TOKEN_KEY` | _(unset)_ | 32-byte key (base64) to encrypt Plaid tokens at rest |

---

## API reference

All endpoints exchange JSON. Every path except the public auth/health routes
requires the session cookie (sent automatically by the browser with
`credentials: 'include'`). A standard response is
`{ "status": true, "data": ..., "message": ... }`.

### Auth

| Method | Path | Body | Notes |
|--------|------|------|-------|
| POST | `/createUser` | `{ userName, password }` | Create an account |
| POST | `/login` | `{ userName, password }` | Sets the `auth` session cookie |
| POST | `/logout` | — | Clears the session cookie |
| GET | `/auth/whoami` | — | Returns the current session identity |

### Accounts

| Method | Path | Body | Notes |
|--------|------|------|-------|
| POST | `/accounts/create` | `{ name, initialBalance? }` | Create a savings account |
| GET | `/accounts/list` | — | Basic account list |
| GET | `/accounts/listWithAllocations` | — | Accounts with per-goal allocation breakdown |
| POST | `/accounts/addFunds` | `{ accountId, amount }` | Record income (manual accounts only) |
| POST | `/accounts/transfer` | `{ fromAccountId, toAccountId, amount, fromGoalId?, toGoalId? }` | Atomic transfer between accounts; optionally re-homes a goal allocation |

### Goals

| Method | Path | Body | Notes |
|--------|------|------|-------|
| POST | `/goals/create` | `{ accountName, goalName, targetAmount? }` | Create a savings goal under an account |
| GET | `/goals/list` | — | Goals for the current user |
| POST | `/goals/contribute` | `{ goalId, amount }` | Set aside usable money into a goal |
| POST | `/goals/transfer` | `{ accountId, goalId, amount, transferToGoal }` | Move money between goals in an account |
| POST | `/goals/delete` | `{ goalId }` | Delete a goal |

### Routines (recurring auto-save)

| Method | Path | Body |
|--------|------|------|
| POST | `/routines/create` | `{ accountId, goalId, amount, dayOfMonth }` |
| GET | `/routines/list` | — |
| POST | `/routines/run` | `{ routineId }` |
| POST | `/routines/delete` | `{ routineId }` |

### Plaid (bank linking)

| Method | Path | Notes |
|--------|------|-------|
| POST | `/plaid/create_link_token` | Start a Plaid Link session |
| POST | `/plaid/exchange_public_token` | Exchange the public token after linking |
| POST | `/plaid/refresh` | Re-sync linked account balances |

---

## The money model in one paragraph

An account holds real money (`balance`) and a running total of money earmarked
into goals (`sumAllocated`). What you can act on is `usable = balance −
sumAllocated`. "Setting money aside" raises `sumAllocated` and a goal's
`allocatedAmount` together; releasing reverses it. Because every change is an
atomic conditional update in MongoDB — and two-document operations roll back the
first step if the second fails — the system always satisfies
`sumAllocated == Σ(goal.allocatedAmount)`, even under concurrent requests. See
[ARCHITECTURE.md](ARCHITECTURE.md) for the full treatment.

---

## Testing

```bash
# Backend (TestNG + Mockito) — from back-end/
mvn test

# Frontend (Jest + React Testing Library) — from front-end/
npm test
```

The backend suite mocks the Mongo collections, so it runs without a live
database. `MoneyService` is covered for the happy path, insufficient-funds
rejection, and cross-account goal re-homing (which exercises the invariant).

---

## Deployment

**Single-image (Railway / any container host)** — the root `Dockerfile` is a
multi-stage build that compiles the React bundle, packages the backend as a fat
jar, and runs the jar with `STATIC_DIR` pointing at the bundle. The Java server
then serves both the API and the SPA on one origin (no CORS or proxy needed).
`railway.json` builds this Dockerfile and health-checks `/health`.

**Multi-container (local full stack)** — `docker compose up --build` runs
MongoDB, the backend, and an nginx-served frontend together.

When serving over HTTPS, set `APP_ENV=production` so the session cookie is marked
`Secure`.

---

## Security notes

- Passwords are stored as salted **PBKDF2-HMAC-SHA256** (120k iterations); legacy
  unsalted SHA-256 hashes are accepted once and re-hashed on next login.
- Sessions are opaque, unguessable tokens stored server-side with a 24-hour TTL.
- The session cookie is `HttpOnly` and `SameSite=Lax` (CSRF defense), plus
  `Secure` in production.
- Static file serving normalizes paths and guards against directory traversal.

---

## Troubleshooting

- **401 on protected calls** — make sure requests include `credentials:
  'include'` and that you're hitting the Java backend (`:1299` in dev), not
  another server. The CRA proxy must point at `http://localhost:1299`.
- **Backend exits on boot with a Mongo error** — it fails fast (5s) when MongoDB
  is unreachable; check `MONGO_URL` and that the database is running.
- **Cookie not set in production** — confirm you're on HTTPS and that
  `APP_ENV=production` is set so the `Secure` flag is applied.
