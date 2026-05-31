## Banking Application - Spending and Savings Goal Tracker

# Banking App

A full-stack demo banking app with a custom Java HTTP server + MongoDB backend and a React frontend.
Features include authentication via signed cookie, deposits/withdrawals, transaction history, and Goals (savings targets & spending limits).

# Tech Stack

Frontend: React (CRA), fetch API, cookie auth (credentials: 'include')

Backend: Java 21, custom HTTP server, Gson, MongoDB Java driver

Database: MongoDB

Auth: HttpOnly cookie auth=<random session token> (SameSite=Lax in dev; SameSite=Lax + Secure in prod). Passwords are stored salted with PBKDF2-HMAC-SHA256.

Ports: Frontend dev on 3001 (via CRA), Backend on 1299 (proxy: http://localhost:1299)

Project Structure
front-end/
  src/
    App.js
    pages/
      Goals.js
    Home.js
    Login.js
    api.js
    Home.css
  package.json   // "proxy": "http://localhost:1299"

back-end/
  src/main/java/
    server/Server.java                // minimal HTTP server
    request/CustomParser.java         // HTTP + Cookie parsing
    response/HttpResponseBuilder.java
    response/RestApiAppResponse.java
    handler/
      HandlerFactory.java
      AuthFilter.java
      LoginHandler.java
      CreateUserHandler.java
      CreateDepositHandler.java
      WithdrawHandler.java
      GetTransactionsHandler.java
      // Goals & Spend (MVP)
      goals/
        CreateGoalHandler.java
        ListGoalsHandler.java
        ContributeGoalHandler.java
        DeleteGoalHandler.java
      spend/
        LogSpendHandler.java
    dao/
      BaseDao.java
      UserDao.java
      TransactionDao.java
      AuthDao.java
      GoalDao.java
      SpendDao.java
    dto/
      UserDto.java
      TransactionDto.java
      GoalDto.java
      SpendDto.java

# Quick Start (Dev)

Prereqs:

- Node 18+ and npm
- Java 21 (or compatible JDK) and Maven
- MongoDB running locally (default mongodb://localhost:27017)

## Run everything with one command (recommended)

From the repository root, install once and then start both tiers together:

```
npm run setup    # installs the runner + the frontend dependencies (first time only)
npm start        # starts the Java backend (port 1299) AND the React frontend (port 3000) together
```

`npm start` uses [concurrently](https://www.npmjs.com/package/concurrently) to launch:

- `start:backend` → `mvn -f back-end/pom.xml compile exec:java` (runs `server.Server` on 1299)
- `start:frontend` → `npm --prefix front-end start` (CRA dev server, proxies API calls to 1299)

Press Ctrl-C once to stop both. The frontend's `proxy` setting in `front-end/package.json`
forwards `/login`, `/accounts/...`, etc. to the backend, so the two run as one origin in dev.

## Run the tiers separately (alternative)

Backend only — from `back-end/`:

```
mvn -q compile exec:java        # or: mvn clean package, then run server.Server from your IDE
```

Frontend only — from `front-end/`:

```
npm install
npm start                       # CRA dev server on http://localhost:3000
```

`front-end/package.json` must contain:

"proxy": "http://localhost:1299"

Environment & Config
Cookies (Dev vs Prod)

Dev (HTTP localhost): SameSite=Lax; HttpOnly

Prod (HTTPS): SameSite=None; Secure; HttpOnly

Set an environment flag in backend (optional):

APP_ENV=production   # enables Secure cookie flags

Optional .env (Frontend)

Not required, but you may store non-secret UI settings here.

Core Endpoints

All endpoints consume/produce JSON and require the auth cookie unless noted.

Auth

POST /createUser → { userName, password }
Creates user and sets auth cookie.

POST /login → { userName, password }
Validates and sets auth cookie.

POST /logout → clears cookie (if implemented).

Accounts / Transactions

GET /getTransactions → { data: Transaction[] }

POST /createDeposit → { amount }

POST /withdraw → { amount }

(Optional aliases) /transactions, /balance if you added them.

Goals (MVP)

POST /goals/create
Body:

// Savings goal
{ "type":"savings", "name":"Emergency Fund", "targetAmount": 1000, "dueDateMillis": 1754000000000 }

// Spending limit (tracked per month)
{ "type":"spending", "name":"Food Budget", "category":"Food", "targetAmount": 300 }


GET /goals/list
Returns an array of:

{
  "goal": { /* GoalDto */ },
  "progressAmount": 120.00,
  "percent": 40.0,
  "periodLabel": "Aug 2025" // or "All time" for savings
}


POST /goals/contribute (savings only)
{ "goalId":"<id>", "amount": 50.00, "note":"paycheck" }

POST /goals/delete
{ "goalId":"<id>" }

POST /spend/log (spending goals)
{ "category":"Food", "amount": 12.75 }

Frontend Usage

All authenticated fetch calls must include:

fetch('/endpoint', {
  method: 'POST',                 // or GET
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(payload),  // omit for GET
  credentials: 'include'
});



Common Issues & Fixes
401 Unauthorized on deposit/withdraw

Wrong backend: If response headers show x-powered-by: Express, you’re hitting a Node server by mistake.

Kill anything on 3001 that isn’t CRA:

netstat -ano | findstr :3001
tasklist /FI "PID eq <PID>"
taskkill /PID <PID> /F


Backend must be Java on 1299; CRA proxies there.
