# ServiceDesk

Full-stack support and ticket management platform built with Java, Spring Boot, and vanilla JavaScript.

> 🚧 Status: In Development. The authentication layer works end to end. The ticket workflow has not been built yet.

## About

Support requests usually end up scattered across chat threads and email, and nobody can say who owns a request or whether it is late. ServiceDesk is my attempt at a small, self-hosted alternative: people sign in, report an issue, and support staff pick it up and work it through to resolution.

I'm building it in stages, backend first, mostly to learn how a real service fits together: authentication, database migrations, security, and deployment. So far I have finished the account system (registration, login, JWT, login throttling, audit logging) and a first frontend shell. The ticket features come next. This README describes what exists today, and I'll keep it updated as the project grows.

## 🚧 Project Status

### Currently Working
- Registration with server-side validation and BCrypt password hashing. Every new account is a `REQUESTER`.
- Login that verifies credentials and returns a signed JWT (HS256, 60-minute lifetime) plus the user profile.
- Login throttling backed by MySQL, per email and per IP, answering `429` with a `Retry-After` header.
- Audit log rows for registration and for login success, failure, and throttling.
- Flyway migrations for users, teams, audit logs, and the throttle table. Hibernate runs in `validate` mode.
- One JSON error format for every failure.
- Frontend: register page, login page, route protection, logout, and a dashboard shell that shows the signed-in user's profile and session countdown.
- Deployment pieces are in place: Vercel (frontend), Render (Dockerized backend), Railway (MySQL). The backend starts and connects to the database.

### In Progress
- Finishing the production frontend-to-API connection (Vercel to Render). I'm still debugging and verifying this, so don't treat the hosted version as finished.
- Authenticating the `Authorization: Bearer` token on protected endpoints. Tokens are issued today but not yet checked by the backend, so every endpoint other than register and login currently answers `401`.

### Planned
- `GET /api/auth/me` and role-based access control (roles are loaded from the database on each request, not stored in the token).
- Admin endpoints to manage users and teams, and a first-admin bootstrap from environment variables.
- Tickets: categories, creation, status workflow, comments, and assignment to agents or teams.
- SLA deadlines with a scheduled job that flags and escalates overdue tickets.
- Notifications, a real dashboard, and a screen for browsing the audit log.
- CI that runs the test suite on every push.

## Features

### Authentication
- [x] Register with name, email, and password
- [x] BCrypt password hashing (cost factor configurable, default 12)
- [x] Login that returns a JWT and the user profile
- [x] Same error for wrong password, unknown email, and inactive account
- [ ] Bearer-token authentication on protected endpoints
- [ ] `GET /api/auth/me`
- [ ] Server-side logout or token revocation (logout is client-side only for now: the frontend discards the token)

### Ticket / Support Workflow
- [ ] Create and view tickets
- [ ] Categories, priorities, and status workflow
- [ ] Assignment to agents and teams
- [ ] SLA tracking and escalation
- [ ] Comments and notifications

The `ADMIN`, `AGENT`, and `REQUESTER` roles and the `teams` table exist in the code and schema, but nothing in the API creates agents or admins yet.

### Backend
- [x] Layered structure: controller, service, repository
- [x] Centralized exception handling with a consistent `ApiError` JSON body
- [x] Request validation with Bean Validation
- [x] A single UTC `Clock` bean, so time-dependent logic can be tested
- [x] Backend packaged with Docker
- [x] Unit and integration tests (see [Running the tests](#running-the-tests))

### Frontend
- [x] Plain HTML, CSS, and ES modules: no framework and no build step
- [x] One API client that attaches the bearer token and handles `400`, `401`, `403`, `409`, and `429`
- [x] Hash-based routing with guarded pages
- [x] Responsive layout with a dark mode
- [x] Dashboard shell (profile and session info only; it is not connected to any ticket data)

### Security
- [x] Passwords are only stored as BCrypt hashes
- [x] Stateless authentication: no server session, CSRF disabled because tokens travel in a header
- [x] Deny-by-default rules: only register and login are public
- [x] Login throttling that stores only SHA-256 hashes of emails and IPs
- [x] The app refuses to start without a `JWT_SECRET` of at least 32 bytes
- [x] No credentials in the repository: configuration comes from environment variables
- [ ] Role-based authorization on endpoints
- [ ] CORS configuration (not needed today: the deployed frontend reaches the API through a same-origin proxy, see [Architecture](#architecture))

### Database
- [x] MySQL schema managed by Flyway migrations
- [x] JPA entities checked against the migrated schema at startup
- [x] Separate database for tests, so they never touch development data
- [ ] Ticket tables (not designed yet)

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (backend), JavaScript ES modules (frontend) |
| Backend framework | Spring Boot 4.1.1, Spring MVC |
| Security | Spring Security, JWT (HS256) via Spring's OAuth2 JOSE / Nimbus support, BCrypt |
| Database | MySQL |
| ORM | Spring Data JPA with Hibernate |
| Migrations | Flyway |
| Frontend | HTML, CSS, vanilla JavaScript (no React, no Vite) |
| Build | Maven (Maven Wrapper included) |
| Testing | JUnit 5, Spring Boot Test, MockMvc, Mockito |
| Containerization | Docker (backend) |
| Deployment | Vercel (frontend), Render (backend), Railway (MySQL) |

## Architecture

ServiceDesk is a **modular monolith**: one deployable Spring Boot application, split into packages by feature (`auth`, `user`, `team`, `audit`, `throttle`, `security`, `exception`). Inside it the code is layered, and each layer only talks to the one below it.

```
Browser
   ↓
Frontend (HTML / CSS / JS)
   ↓  fetch()
REST API
   ↓
Controller      reads the request, validates it, returns JSON
   ↓
Service         business rules: hashing, throttling, token issuing, audit
   ↓
Repository      Spring Data JPA queries
   ↓
MySQL
```

- **Controller** (`AuthController`): maps HTTP to method calls. It holds no business logic.
- **Service** (`AuthService`, `UserService`, `LoginThrottleService`, `JwtService`, `AuditService`): where the rules live. Services own transactions.
- **Repository** (`UserRepository`, `LoginThrottleRepository`, ...): the only code that talks to the database.

### Current deployment

```
Browser → Vercel (static frontend) → Render (Spring Boot, Docker) → Railway MySQL
```

The browser only ever talks to Vercel. Requests to `/api/*` are rewritten by Vercel to the backend on Render (`frontend/vercel.json`), so the browser sees a single origin and CORS never comes into play. This is the setup I'm currently verifying (see [Project Status](#-project-status)).

## Project Structure

```
servicedesk/
├── .env.example                   # names of the environment variables (no real values)
├── .gitignore
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd             # Maven Wrapper
│   └── src/
│       ├── main/
│       │   ├── java/com/servicedesk/
│       │   │   ├── ServicedeskApplication.java
│       │   │   ├── auth/          # register and login: controller, service, DTOs
│       │   │   ├── user/          # User entity, Role, repository, UserService
│       │   │   ├── team/          # Team entity and repository
│       │   │   ├── audit/         # audit log entity, repository, AuditService
│       │   │   ├── throttle/      # login throttling (entity, repository, service, properties)
│       │   │   ├── security/      # SecurityConfig, JWT config, properties, and service
│       │   │   ├── exception/     # ApiError, custom exceptions, global handler
│       │   │   └── config/        # TimeConfig (UTC clock)
│       │   └── resources/
│       │       ├── application.properties
│       │       └── db/migration/  # V1 users/teams, V2 audit_logs, V3 login_throttle
│       └── test/                  # unit and integration tests
└── frontend/
    ├── index.html
    ├── css/styles.css
    ├── js/
    │   ├── app.js                 # wires auth, API client, and router together
    │   ├── api.js                 # the single API client
    │   ├── auth.js                # auth state (sessionStorage)
    │   ├── router.js, ui.js, errors.js, config.js
    │   └── pages/                 # login, register, dashboard
    ├── dev-server.mjs             # local static server with an /api proxy
    ├── package.json
    └── vercel.json                # production /api rewrite and security headers
```

## Backend Details

- **Java 21 + Spring Boot**: the REST API runs on Spring MVC. Records are used for request and response DTOs, so nothing sensitive can leak through an entity by accident.
- **Spring Security**: a stateless filter chain with CSRF off and deny-by-default rules. Only `POST /api/auth/register` and `POST /api/auth/login` are public.
- **JWT**: issued at login with Spring's Nimbus-based encoder and signed with HS256. The token holds only `iss`, `sub` (the user id), `iat`, `exp`, and `jti`. The role and active flag are meant to be read from the database on every request, so a role change or deactivation takes effect immediately (this check arrives with bearer authentication).
- **Spring Data JPA / Hibernate**: `User`, `Team`, `AuditLog`, and `LoginThrottle` entities. Enum columns are stored as `VARCHAR`.
- **MySQL**: the only datastore. Login throttling lives here too, rather than in memory, so counters survive restarts.
- **Flyway**: owns the schema. Migrations are never edited after they have been applied; changes go in a new `V<n>` file.
- **Maven**: the wrapper in `backend/` builds the app. The Dockerfile uses it to produce the JAR.

## Authentication Flow

```
Registration                 ✅ working
   ↓
Password hashing (BCrypt)    ✅ working
   ↓
Database (users table)       ✅ working
   ↓
Login                        ✅ working
   ↓
Credential verification      ✅ working (throttled per email and per IP)
   ↓
JWT generation               ✅ working
   ↓
Authenticated API requests   🚧 not yet: the backend does not validate bearer tokens
```

1. `POST /api/auth/register` creates a `REQUESTER`. Any `role` sent by the client is ignored.
2. `POST /api/auth/login` checks the throttle first, then compares the password. A wrong password, an unknown email, and an inactive account all return the same `401`. For an unknown email, a dummy BCrypt comparison still runs so response time doesn't reveal which accounts exist.
3. On success the API returns the token and the user. The frontend keeps both in `sessionStorage` and sends `Authorization: Bearer <token>` on authenticated calls.
4. Validating that header on the server, and loading the user's role from the database per request, is the next piece of backend work.

## Database

- **MySQL** with a dedicated application user, and a separate `servicedesk_test` database for tests.
- **Tables so far**: `teams`, `users`, `audit_logs`, `login_throttle`.
- **JPA entities** map those tables. `spring.jpa.hibernate.ddl-auto=validate` makes the app fail at startup if an entity and the schema disagree, so mismatches show up immediately.
- **Flyway migrations** in `backend/src/main/resources/db/migration/`:
    - `V1__create_teams_and_users.sql`
    - `V2__create_audit_logs.sql`
    - `V3__create_login_throttle.sql`
- **Repositories** are Spring Data interfaces. The throttle repository adds a locking query and an atomic upsert so concurrent failed logins are counted correctly.
- **Validation** happens twice: Bean Validation on request DTOs, and database constraints (unique email, foreign keys) as the final guard.

## Environment Variables

The backend reads these from the environment. Never commit real credentials or secrets to GitHub.

| Variable | Required | Purpose |
|---|---|---|
| `DB_URL` | In production | JDBC URL of the MySQL database. Defaults to a local `servicedesk` database. |
| `DB_USERNAME` | In production | Database user. Defaults to `servicedesk_app`. |
| `DB_PASSWORD` | Yes | Database password. No default. |
| `JWT_SECRET` | Yes | HS256 signing secret, at least 32 bytes. No default: the app won't start without it. |

```bash
DB_URL=jdbc:mysql://localhost:3306/servicedesk
DB_USERNAME=your_username
DB_PASSWORD=your_password
JWT_SECRET=your_long_secret
```

Optional tuning (Spring's relaxed binding also accepts them as upper-case environment variables, for example `APP_SECURITY_THROTTLE_LOGIN_LOCK_MINUTES`):

| Property | Default |
|---|---|
| `app.security.bcrypt-strength` | `12` |
| `app.security.jwt.expiration-minutes` | `60` |
| `app.security.throttle.login-max-failures-per-email` | `10` |
| `app.security.throttle.login-max-failures-per-ip` | `30` |
| `app.security.throttle.login-window-minutes` / `login-lock-minutes` | `15` / `15` |

The frontend needs no environment variables. The API base URL is the `api-base-url` meta tag in `frontend/index.html` (`/api`).

## Local Setup

### Prerequisites
- Java 21
- MySQL (a recent 8.x or newer)
- Git
- Node.js (only for the small frontend dev server)

Maven is not needed separately: the repository includes the Maven Wrapper.

### 1. Clone
```bash
git clone https://github.com/singhaman2353-ux/servicedesk.git
cd servicedesk
```

### 2. Create the databases
In a MySQL shell as an admin user. Replace the placeholder password with your own:
```sql
CREATE DATABASE servicedesk CHARACTER SET utf8mb4;
CREATE DATABASE servicedesk_test CHARACTER SET utf8mb4;
CREATE USER 'servicedesk_app'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON servicedesk.* TO 'servicedesk_app'@'localhost';
GRANT ALL PRIVILEGES ON servicedesk_test.* TO 'servicedesk_app'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Set the environment variables
Generate a secret with `openssl rand -base64 48`, or in PowerShell:
`[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))`

Bash:
```bash
export DB_PASSWORD='the-password-you-chose'
export JWT_SECRET='the-generated-secret'
```
PowerShell (use single quotes):
```powershell
$env:DB_PASSWORD='the-password-you-chose'
$env:JWT_SECRET='the-generated-secret'
```
`DB_URL` and `DB_USERNAME` fall back to the local defaults above.

### 4. Run the backend
```bash
cd backend
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```
Flyway creates the tables on first start. The API listens on http://localhost:8080.

### 5. Run the frontend
In a second terminal:
```bash
cd frontend
node dev-server.mjs
```
Open http://localhost:5173. The dev server serves the files and proxies `/api/*` to `http://localhost:8080`. Set `API_TARGET` if your backend runs elsewhere.

### 6. Try the API directly
```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com","password":"ChangeMe12345"}'

curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"ChangeMe12345"}'
```
(`ChangeMe12345` is just an example. Passwords need 10 to 72 characters, with at least one letter and one digit.)

### Running the tests
The tests use the `servicedesk_test` database created above and need `DB_PASSWORD` set:
```bash
cd backend
./mvnw test
```

### Running the backend in Docker (optional)
```bash
cd backend
docker build -t servicedesk-api .
docker run -p 8080:8080 \
  -e DB_URL='jdbc:mysql://host.docker.internal:3306/servicedesk' \
  -e DB_USERNAME='servicedesk_app' \
  -e DB_PASSWORD='the-password-you-chose' \
  -e JWT_SECRET='the-generated-secret' \
  servicedesk-api
```

## API Reference

Only two endpoints exist today. Every other path currently returns `401`.

### `POST /api/auth/register`
```json
{ "name": "Test User", "email": "test@example.com", "password": "ChangeMe12345" }
```
**201** returns the new user (`id`, `name`, `email`, `role`, `active`, `teamId`, `teamName`, `createdAt`). Errors: `400` validation, `409` email already registered.

### `POST /api/auth/login`
```json
{ "email": "test@example.com", "password": "ChangeMe12345" }
```
**200** returns `accessToken`, `tokenType` (`Bearer`), `expiresAt`, `expiresInSeconds`, and `user`. Errors: `400` validation, `401` invalid credentials, `429` too many attempts (see the `Retry-After` header, in seconds).

### Error format
```json
{
  "timestamp": "2026-10-05T10:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Invalid request data",
  "path": "/api/auth/register",
  "fieldErrors": [{ "field": "password", "message": "..." }]
}
```

## Deployment

The current setup, with no credentials shown:

| Part | Host | Notes |
|---|---|---|
| Frontend | Vercel | Project root directory is `frontend`, with no build step. `frontend/vercel.json` rewrites `/api/*` to the backend's public URL. |
| Backend | Render | Built from `backend/Dockerfile`. Needs `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` set in the service's environment. |
| Database | Railway MySQL | Reached from the backend through `DB_URL`. |

Free tiers can put an idle backend to sleep, so the first request after a quiet period may be slow.

## Known Limitations

- No ticket features yet. The dashboard is a shell.
- The backend issues tokens but does not yet validate them on protected endpoints.
- Logout only clears the token in the browser.
- No CORS configuration: the frontend must reach the API through the same-origin proxy.
- Login throttling is per IP as well as per email. Behind a proxy, many users can share one IP, so the per-IP limit may need raising in production.
- No CI pipeline yet.

## Notes From Building It

- Hibernate 7 can expect a native MySQL `ENUM` for Java enums. Mapping them as `VARCHAR` explicitly keeps `ddl-auto=validate` happy.
- Failed-login counters and audit rows are written in their own transactions. If they shared the request's transaction, a failed request would roll back the evidence of the failure.
- Same-origin proxying through Vercel removed the need for CORS on the backend, at the cost of one extra hop.

## Author

Built by [singhaman2353-ux](https://github.com/singhaman2353-ux). Feedback and issues are welcome.