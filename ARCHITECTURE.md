# Architecture

This document describes how the Savings App is put together: the request
lifecycle, the backend layers, the savings-envelope money model that keeps
balances correct under concurrency, the data model, the frontend, and how the
pieces are deployed.

For setup and usage, see the [README](README.md).

---

## 1. System overview

The app is a two-tier system:

- **Backend** — Java 21 service built directly on the JDK's
  `com.sun.net.httpserver.HttpServer` (no Spring / no servlet container). It
  exposes a JSON REST API, persists to MongoDB, and — in production — also
  serves the compiled React bundle so the whole app runs from a single origin.
- **Frontend** — a Create React App single-page app (React 18 + React Router 6)
  that talks to the API with `fetch` and an `HttpOnly` session cookie.

```
                       ┌─────────────────────────────────────────────┐
                       │                Browser (SPA)                 │
                       │   React 18 · React Router 6 · api/client.js  │
                       └───────────────┬─────────────────────────────┘
                                       │  fetch(credentials: 'include')
                                       │  cookie: auth=<session token>
                                       ▼
        ┌──────────────────────────────────────────────────────────────────┐
        │                     Java HTTP server (Server.java)                 │
        │                                                                    │
        │   CORS / OPTIONS ─► static-vs-API split ─► auth gate ─► routing    │
        │                                                   │                │
        │     serveStatic()  (SPA, prod)        HandlerFactory.getHandler()  │
        │                                                   │                │
        │                          ┌────────────────────────┼─────────────┐ │
        │                          ▼                        ▼              ▼ │
        │                     Handlers              MoneyService     RoutineScheduler
        │                 (auth/accounts/             (atomic           (monthly
        │                  goals/routines/         money movement)    auto-saves)  │
        │                  plaid/...)                      │                │
        │                          └────────────┬─────────┘                │
        │                                       ▼                          │
        │                                  DAO layer (BaseDao + per-type)  │
        └───────────────────────────────────────┬──────────────────────────┘
                                                 ▼
                                   MongoDB  (Atlas or local / docker)
```

---

## 2. Request lifecycle

Every request hits a single `HttpHandler` registered on the `/` context
(`Server.GenericHandler`). It runs the same pipeline each time:

1. **CORS / preflight** — `OPTIONS` returns `204` with the configured
   `APP_ORIGIN`. Credentialed CORS headers (`Allow-Credentials: true`,
   `Vary: Origin`) are added to every response so the cookie flows in dev where
   the SPA and API are on different ports.
2. **Static vs. API split** — `isApiPath(path)` checks the path against a fixed
   list of API prefixes. Anything that is *not* an API path is treated as a
   static asset and served by `serveStatic()` (used by the single-image deploy).
3. **Body read** — the request body is read honoring `Content-Length`, falling
   back to `readAllBytes()`.
4. **Routing + auth gate** — `route()` short-circuits `/health`, then enforces
   authentication for any path not in `PUBLIC_PATHS` via `AuthFilter`, then
   dispatches through `HandlerFactory`.
5. **Response** — the handler returns an `HttpResponseBuilder`; the server
   serializes the body with Gson, sets `Content-Type`, status, and any
   handler-supplied headers (e.g. `Set-Cookie`), and writes the bytes.
6. **Failure isolation** — any uncaught `Throwable` is converted to a `500` JSON
   response instead of dropping the connection.

### Static file serving (single-origin deploy)

`serveStatic()` resolves the requested path under `STATIC_DIR`, **normalizes and
guards against path traversal** (`file.startsWith(base)`), and falls back to
`index.html` for unknown routes so client-side routing works. Content types are
mapped by extension. When `STATIC_DIR` is unset (pure-API dev mode) it returns a
JSON `404`.

---

## 3. Backend layers

| Layer | Package | Responsibility |
|-------|---------|----------------|
| HTTP server | `server` | Socket lifecycle, CORS, static serving, routing, error isolation |
| Request | `request` | `ParsedRequest`, header/cookie parsing (`CustomParser`) |
| Response | `response` | `HttpResponseBuilder`, `RestApiAppResponse`, `CustomHttpResponse` |
| Routing | `handler.HandlerFactory` | Path → handler mapping (incl. compatibility aliases) |
| Auth | `handler.AuthFilter`, `handler.CookieUtil` | Session validation, cookie construction |
| Handlers | `handler.*` | One class per endpoint; parse → validate → delegate → respond |
| Services | `service` | `MoneyService` (money movement), `RoutineScheduler` (recurring saves) |
| Security | `security` | `PasswordUtil` (PBKDF2), `TokenUtil`, `CryptoUtil` |
| Plaid | `plaid` | Bank-linking client, config, account sync |
| DAO | `dao` | `BaseDao<T>` + per-collection DAOs; `MongoConnection` |
| DTO | `dto` | Documents ↔ typed objects; `TransactionType` enum |

### Authentication

- Login verifies the password against a stored **PBKDF2-HMAC-SHA256** hash
  (`pbkdf2$<iterations>$<salt>$<hash>`, 120k iterations). Legacy unsalted
  SHA-256 hashes are still accepted and **transparently re-hashed** on the next
  successful login.
- On success the server mints an opaque, unguessable session token
  (`TokenUtil`), stores it in the `Auth` collection with a 24-hour expiry, and
  returns it as an `HttpOnly` cookie.
- `CookieUtil` always sets `SameSite=Lax` (the primary CSRF defense for a
  cookie session) and adds `Secure` when `APP_ENV=production`.
- `AuthFilter` looks the token up on every protected request and rejects
  missing/expired sessions with `401`.

### Handlers

Handlers implement `BaseHandler` (`HttpResponseBuilder handleRequest(ParsedRequest)`).
The convention is: authenticate, parse + validate the JSON body, delegate domain
logic to a service or DAO, and map results (or a `MoneyService.MoneyException`)
to an HTTP status. The frontend-facing surface is grouped under
`accounts/`, `goals/`, `routines/`, and `plaid/`.

---

## 4. The savings-envelope money model

This is the core of the domain. Money is tracked with a **partition (envelope)**
model rather than a single mutable balance.

An **Account** holds:

- `balance` — the real money in the account.
- `sumAllocated` — how much of that money is earmarked into goals.
- **`usable = balance − sumAllocated`** — money free to spend or set aside.

A **Goal** holds `allocatedAmount` — its slice of the parent account's
`sumAllocated`. The system maintains one invariant at all times:

> **`account.sumAllocated == Σ(goal.allocatedAmount)`** for that account.

### Atomicity and concurrency safety

All money movement goes through `MoneyService`, which never reads-modifies-writes
a balance in application code. Instead it uses **MongoDB conditional updates**
that combine the guard and the mutation in a single atomic `updateOne`:

- `tryReserve(account, amount)` — raises `sumAllocated` **only if**
  `usable ≥ amount`. Returns whether it succeeded.
- `releaseReservation(account, amount)` — lowers `sumAllocated` (clamped at 0).
- `tryDebitUsable(account, amount)` — lowers `balance` **only if**
  `usable ≥ amount` (used for account-to-account transfers).
- `incAllocated` / `tryDecAllocated(goal, amount)` — adjust a goal's allocation.

Because the guard and write are one operation, concurrent requests can never
overdraw usable funds or a goal's allocation.

### Two-document operations compensate on failure

Operations that touch two documents (an account and a goal, or two accounts)
apply the steps in order and **roll back the first step if the second fails**, so
the invariant holds even on partial failure. Example — `allocate()`:

1. `tryReserve` on the account (atomic guard).
2. `incAllocated` on the goal; if it throws, `releaseReservation` compensates.
3. Log the transaction.

`MoneyService` exposes `allocate`, `release`, `transferBetweenGoals`, and
`transferBetweenAccounts` (the last re-homes a goal allocation across accounts
while keeping both accounts' invariants intact).

### Recurring auto-saves

`RoutineScheduler` runs on a daemon `ScheduledExecutorService` (shortly after
boot, then every 5 minutes). It finds due routines, applies each via
`MoneyService.allocate`, and advances the schedule to the next month. If an
account lacks usable funds when a routine fires, it is recorded as skipped and
retried next cycle — it never overdraws.

---

## 5. Data model (MongoDB collections)

| Collection | DTO | Key fields |
|------------|-----|-----------|
| `Account` | `AccountDto` | `userName`, `name`, `balance`, `sumAllocated`, `source` (`manual`/`plaid`), `version` |
| `Goal` | `GoalDto` | `userName`, `accountId`, `name`, `type`, `allocatedAmount`, `targetAmount` |
| `Transaction` | `TransactionDto` | `userId`, `transactionType`, `amount`, `accountId`, goal refs, timestamp |
| `Routine` | `RoutineDto` | `userName`, `accountId`, `goalId`, `amount`, `dayOfMonth`, next-run + last-status |
| `Auth` | `AuthDto` | `userName`, `hash` (session token), `expireTime` |
| User store | `UserDto` | `userName`, salted password hash (legacy `balance`/`debt` fields) |
| `PlaidItem` | `PlaidItemDto` | linked-bank item + encrypted access token |
| `Spend` | `SpendDto` | spending-goal activity |

`MongoConnection` reads `MONGO_URL` / `MONGO_DB`, configures TLS for Atlas
shared-tier connections, and fails fast (5s server-selection timeout) on an
unreachable database. DAOs extend `BaseDao<T>` and convert documents through each
DTO's `toDocument` / `fromDocument`.

---

## 6. Frontend architecture

- **Entry** — `index.js` builds a `createBrowserRouter` with routes `/` (Login),
  `/home`, `/goals`, `/accounts`. The three app routes are wrapped in
  `ProtectedRoute`.
- **API access** — `api/client.js` is a thin `fetch` wrapper that always sends
  `credentials: 'include'` and JSON-encodes object bodies (case-insensitive on
  the `Content-Type` header). All data calls go through it.
- **Pages** — `Accounts` (account list with stacked goal-allocation bars),
  `Goals` (create/list goals), `Home`.
- **Components** — `QuickActions` (set aside / release / transfer between goals),
  `AddFunds` (record income into a manual account), `Routines` (monthly
  auto-save rules), `LinkBankButton` (Plaid Link), `Header`, `GoalCard`,
  `InlineNotice`.
- **Session UX** — `ProtectedRoute` gates on a `userName` flag in
  `localStorage`; the actual authority is the `HttpOnly` cookie, re-checked
  server-side on every request and via `/auth/whoami`.

---

## 7. Deployment topology

The app supports two shapes, both driven by Docker:

**Single-origin image (production / Railway)** — root `Dockerfile` is a
multi-stage build: it compiles the React bundle, packages the backend as a
fat jar, then runs the jar with `STATIC_DIR` pointing at the bundle. The Java
server serves both the API and the SPA on **one port and origin**, so the
session cookie "just works" with no CORS or proxy concerns. `railway.json`
points at this Dockerfile and health-checks `/health`.

**Multi-container (local full stack)** — `docker-compose.yml` runs MongoDB, the
backend, and an nginx-served frontend separately (frontend on `:4000`).

**Dev** — `npm start` at the repo root uses `concurrently` to run the backend
(`:1299`) and the CRA dev server (`:4000`) together; CRA's `proxy` setting
forwards API calls to the backend so the two behave as one origin.

---

## 8. Testing

- **Backend** — TestNG + Mockito. Handler and service tests inject mocked DAOs /
  Mongo collections (`CollectionTestTools`), so the suite runs with no live
  database. `MoneyService` is covered for the happy path, insufficient-funds
  rejection, and the cross-account goal re-homing that exercises the invariant.
  Run with `mvn test` from `back-end/`.
- **Frontend** — Jest + React Testing Library. Covers the login screen smoke
  test and the `api()` client (body encoding across header casings, credentials,
  path normalization). Run with `npm test` from `front-end/`.
