# Vozni Park — Registration Service

A standalone Clojure microservice responsible for new employee account registration in the Vozni Park fleet management system. As an architectural rule, it is the **only** service responsible for creating `APP_USER` records in the shared MySQL database. This keeps user-account creation isolated from the main Spring Boot backend and makes the registration flow easier to test, monitor, and evolve independently.

The service runs on port `8081` alongside the Spring Boot backend and is proxied by Nginx under the `/registration/` path.

---

## Table of Contents

1. [Overview](#overview)
2. [Technology Stack](#technology-stack)
3. [Why Clojure?](#why-clojure)
4. [Project Structure](#project-structure)
5. [Registration Pipeline](#registration-pipeline)
6. [API Reference](#api-reference)
7. [Configuration](#configuration)
8. [Running Locally](#running-locally)
9. [Running with Docker](#running-with-docker)
10. [Testing](#testing)
11. [Database Tables](#database-tables)
12. [Design Decisions](#design-decisions)
13. [Architecture Notes](#architecture-notes)
14. [Production Improvements](#production-improvements)

---

## Overview

When a new employee needs access to Vozni Park, an administrator adds them to the `ZAPOSLENI` (employee) table. The employee then self-registers through this service using their work email. The service:

1. Validates that the email belongs to an active employee record
2. Generates a one-time magic link token (UUID, 2-hour expiry)
3. Sends an HTML email with the link via Mailtrap SMTP
4. On completion, creates a hashed `APP_USER` account with `LOCAL_ADMIN` role

The magic link itself proves email ownership, so no additional OTP step is required during registration. Two-factor authentication (OTP) is used separately at login time and is handled entirely by the Spring Boot backend.

---

## Technology Stack

| Component       | Library / Version              | Purpose                                      |
|-----------------|--------------------------------|----------------------------------------------|
| Language        | Clojure 1.11                   | Functional JVM language                      |
| Build Tool      | Leiningen                      | Dependency management, uberjar packaging     |
| HTTP Server     | Ring + Jetty adapter           | HTTP request/response handling               |
| Routing         | Reitit (ring-handler)          | Route definition and dispatch                |
| Middleware      | ring/ring-json, ring/ring-core | JSON body parsing, query param parsing       |
| Database        | next.jdbc + HikariCP           | MySQL connection pool and query execution    |
| Password Hashing| org.mindrot/jbcrypt 0.4        | BCrypt — compatible with Spring Boot         |
| Email           | Postal                         | SMTP email sending                           |
| Templates       | Selmer                         | Jinja2-style HTML email template rendering   |
| Logging         | clojure.tools.logging          | Structured application logging               |
| Testing         | Midje 1.10.10                  | BDD-style unit tests with mocking            |
| Runtime         | Java 21 (uberjar)              | Deployed as a standalone JAR                 |

> **BCrypt note:** `org.mindrot/jbcrypt` is used instead of `buddy-hashers` because it produces `$2a$10$...` hashes that are 100% compatible with Spring Boot's `BCryptPasswordEncoder`. `buddy-hashers` 2.x produces a different format.

---

## Why Clojure?

This project was implemented in Clojure as a small, isolated service because the registration flow is a good fit for a functional, data-oriented approach:

- Request handlers receive immutable request maps and return plain response maps.
- Business logic is expressed as a sequence of small transformations and validations.
- Side effects are kept at the edges of the system: database access, password hashing, and email sending.
- Midje's `provided` macro makes it straightforward to test handler behavior without a live database or SMTP server.
- The service can still run on the JVM and integrate easily with the rest of the Java/Spring-based Vozni Park ecosystem.

In other words, Clojure is used here not only as a language requirement, but as a practical way to keep the registration workflow small, explicit, and highly testable.

---

## Project Structure

```
vozni-park-registration-service/
├── project.clj
├── Dockerfile
├── src/
│   └── registration_service/
│       ├── core.clj         # Entry point — starts Jetty server
│       ├── routes.clj       # Reitit route definitions + middleware stack
│       ├── handlers.clj     # Request handler functions (one per endpoint)
│       ├── db.clj           # next.jdbc queries + HikariCP connection pool
│       ├── auth.clj         # UUID token generation, BCrypt hashing
│       ├── mail.clj         # Email sending via Postal + Selmer templates
│       └── config.clj       # Environment variable configuration maps
├── resources/
│   └── templates/
│       └── registration-email.html   # Magic link email (Selmer template)
└── test/
    └── registration_service/
        ├── auth_test.clj             # Tests for auth.clj (9 tests)
        └── handlers_test.clj         # Tests for handlers.clj (16 tests)
```

---

## Registration Pipeline

The flow has three steps. OTP was intentionally removed from this pipeline — clicking the magic link already proves the employee owns the email address.

```
Employee                  Service                     MySQL
   │                         │                           │
   │  POST /api/register     │                           │
   │  { email }              │                           │
   │────────────────────────>│                           │
   │                         │  SELECT FROM zaposleni    │
   │                         │──────────────────────────>│
   │                         │  { id, full_name, email } │
   │                         │<──────────────────────────│
   │                         │  INSERT registration_token│
   │                         │──────────────────────────>│
   │  200 OK (always)        │                           │
   │<────────────────────────│                           │
   │  [receives email]       │                           │
   │                         │                           │
   │  GET /api/verify?token= │                           │
   │────────────────────────>│                           │
   │                         │  SELECT valid token       │
   │                         │──────────────────────────>│
   │  200 { valid, email }   │                           │
   │<────────────────────────│                           │
   │                         │                           │
   │  POST /api/complete     │                           │
   │  { token, user, pass }  │                           │
   │────────────────────────>│                           │
   │                         │  INSERT app_user          │
   │                         │  SKIP_NEXT_OTP=1          │
   │                         │  mark token used          │
   │                         │──────────────────────────>│
   │  201 { username }       │                           │
   │<────────────────────────│                           │
```

> **`SKIP_NEXT_OTP=1`**: New accounts have this flag set at creation time. On their very first login, Spring Boot issues a JWT directly without requiring an OTP code — the magic link already proved email ownership. The flag is reset to `0` by Spring Boot after first use.

---

## API Reference

### `GET /health`

Health check endpoint. Used by Docker and load balancers.

**Response `200`:**
```json
{ "status": "UP", "service": "registration-service", "version": "0.1.0" }
```

**Example:**
```bash
curl http://localhost:8081/health
```

---

### `POST /api/register`

Initiates registration for a work email address.

**Request body:**
```json
{ "email": "marko.markovic@voznipark.rs" }
```

**Response `200`** — always returned regardless of whether the email exists (prevents email enumeration):
```json
{ "message": "If your email is registered in our system, you will receive a registration link shortly." }
```

**Response `400`** — email field missing from request body.

**Example:**
```bash
curl -X POST http://localhost:8081/api/register \
  -H "Content-Type: application/json" \
  -d '{"email":"marko.markovic@voznipark.rs"}'
```

---

### `GET /api/verify?token=UUID`

Validates a magic link token before showing the registration form.

**Response `200` — token valid:**
```json
{ "valid": true, "email": "marko.markovic@voznipark.rs", "message": "Token is valid." }
```

**Response `400` — token expired, already used, or not found:**
```json
{ "valid": false, "message": "Token is invalid or has expired." }
```

**Example:**
```bash
curl "http://localhost:8081/api/verify?token=60f4e8ca-10df-4b1a-9c3e-abc123def456"
```

---

### `POST /api/complete`

Completes registration by creating the user account.

**Request body:**
```json
{
  "token":    "60f4e8ca-10df-4b1a-9c3e-abc123def456",
  "username": "marko.markovic",
  "password": "securePassword99"
}
```

**Response `201` — account created:**
```json
{ "message": "Account created successfully.", "username": "marko.markovic" }
```

**Response `400`** — missing fields, password shorter than 8 characters, or invalid/expired token.

**Response `409`** — username already taken.

> **Important:** Mailtrap renders a clickable button in the email preview, but clicking it strips the `token` query parameter due to click tracking. Always copy the token from the **HTML Source** tab in Mailtrap when testing manually.

**Example:**
```bash
curl -X POST http://localhost:8081/api/complete \
  -H "Content-Type: application/json" \
  -d '{
    "token":"60f4e8ca-10df-4b1a-9c3e-abc123def456",
    "username":"marko.markovic",
    "password":"securePassword99"
  }'
```

---

## Configuration

All configuration is read from environment variables. Sensible defaults are provided for local development.

| Variable       | Default                  | Description                                             |
|----------------|--------------------------|---------------------------------------------------------|
| `DB_HOST`      | `localhost`              | MySQL host                                              |
| `DB_PORT`      | `3306`                   | MySQL port                                              |
| `DB_NAME`      | `vozni_park_db`          | Database name                                           |
| `DB_USERNAME`  | `root`                   | Must have INSERT privileges on `app_user`               |
| `DB_PASSWORD`  | *(empty)*                | Set via `.env` or shell                                 |
| `MAIL_HOST`    | `smtp.gmail.com`         | Override with `sandbox.smtp.mailtrap.io` for dev        |
| `MAIL_PORT`    | `587`                    | Use `2525` locally if 587 times out; `465` in Docker    |
| `MAIL_USER`    | *(empty)*                | Mailtrap sandbox SMTP username                          |
| `MAIL_PASSWORD`| *(empty)*                | Mailtrap sandbox SMTP password                          |
| `FRONTEND_URL` | `http://localhost:5173`  | Base URL for magic link construction                    |
| `PORT`         | `8081`                   | HTTP port the service listens on                        |

---

## Running Locally

**Prerequisites:** Java 21, Leiningen, XAMPP MySQL running on port 3306 with `vozni_park_db` created and Liquibase migrations applied.

Set environment variables in PowerShell, then start the service:

```powershell
$env:MAIL_HOST="sandbox.smtp.mailtrap.io"
$env:MAIL_PORT="2525"
$env:MAIL_USER="your-mailtrap-sandbox-user"
$env:MAIL_PASSWORD="your-mailtrap-sandbox-password"
$env:DB_PORT="3306"
$env:DB_NAME="vozni_park_db"

lein run
```

The service starts at `http://localhost:8081`. Verify it is running:

```
GET http://localhost:8081/health
```

**Download dependencies only (without starting the service):**

```bash
lein deps
```

**Build a standalone JAR:**

```bash
lein uberjar
# Output: target/registration-service-standalone.jar
```

---

## Running with Docker

The service is part of the full `docker-compose` stack. It starts automatically when the MySQL health check passes.

**Start the full stack:**

```bash
docker-compose up --build
```

**Rebuild only the registration service:**

```bash
docker-compose up --build registration-service
```

**View live logs:**

```bash
docker logs vozni-park-registration-service --tail=50 -f
```

**Fix a stuck registration** (token already used or expired — useful during manual testing):

```bash
docker exec -it vozni-park-mysql mysql -u root -proot vozni_park_db \
  -e "DELETE FROM registration_tokens WHERE email='user@voznipark.rs';"
```

### Dockerfile (multi-stage)

```dockerfile
# Stage 1 — build uberjar
FROM clojure:temurin-21-lein AS build
WORKDIR /app
COPY project.clj .
RUN lein deps
COPY . .
RUN lein uberjar

# Stage 2 — minimal JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*-standalone.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Testing

Tests are written with [Midje](https://github.com/marick/Midje), the standard BDD-style testing library for Clojure. Handler tests use Midje's `provided` macro to mock all database and email calls, so no live database or SMTP server is needed to run the test suite.

### Running tests

```bash
# Single pass (use in CI or before committing)
lein midje :all

# Watch mode — re-runs on every file save (use during development)
lein midje
```

### Test files

| File | Tested namespace | Tests | What is covered |
|------|-----------------|-------|-----------------|
| `auth_test.clj` | `auth.clj` | 9 | Token generation (UUID format, uniqueness), BCrypt hashing (prefix, salting), password verification |
| `handlers_test.clj` | `handlers.clj` | 16 | All four handlers: missing fields, invalid tokens, username conflicts, security (email enumeration), success paths |

### Example test (Midje style)

```clojure
(fact "returns 200 even when employee is NOT found — prevents email enumeration"
  (handlers/register-request {:body {:email "unknown@voznipark.rs"}})
  => (contains {:status 200})
  (provided
    (db/find-employee-by-email "unknown@voznipark.rs") => nil))
```

---

## Database Tables

The service interacts with three tables in the shared MySQL database.

| Table                | Access       | Description                                                                  |
|----------------------|--------------|------------------------------------------------------------------------------|
| `ZAPOSLENI`          | READ only    | Validates that the email belongs to an active employee before registering    |
| `REGISTRATION_TOKENS`| READ + WRITE | Creates tokens on register, reads on verify, marks as used on complete       |
| `APP_USER`           | WRITE only   | Inserts the new user account with `ROLE_ID=2` (LOCAL_ADMIN) and `SKIP_NEXT_OTP=1` |

> **Note:** The service currently uses root MySQL credentials because `vozni_user` does not have INSERT privileges on `app_user`. This is acceptable for an academic project and local Docker setup. In production, this should be replaced with a dedicated database user that has the minimum required privileges: read access to `ZAPOSLENI`, read/write access to `REGISTRATION_TOKENS`, and insert-only access to `APP_USER`.

The `OTP_CODES` table exists in the database but is **not** used by this service. It is owned and managed exclusively by the Spring Boot backend for its two-factor login flow.

---

## Design Decisions

### Separate registration service

Registration is separated from the main Spring Boot backend so that account creation has a clear owner. The Spring Boot backend handles authentication, authorization, OTP login, and the main business logic of the Vozni Park system, while this Clojure service owns only the employee registration pipeline.

### `LOCAL_ADMIN` role assignment

Newly registered users are created with `ROLE_ID=2` (`LOCAL_ADMIN`) because the current Vozni Park access model is organized around local administrators who manage vehicle and driver data for their organizational unit. This role can be changed or made configurable later if the system introduces more granular self-service user roles.

### No OTP during registration

The first version of the registration pipeline included both a magic link and an OTP code. This was removed because both mechanisms verify access to the same mailbox. Keeping only the magic link reduces friction while preserving the intended security property: proving ownership of the employee email address.

### Testability over infrastructure dependency

Handler tests mock all database and email calls. This keeps the Midje test suite fast and deterministic, and it allows the registration behavior to be verified without starting MySQL, Mailtrap, or the full Docker Compose stack.

---

## Architecture Notes

**Email enumeration prevention** — `POST /api/register` always returns `200`, regardless of whether the provided email exists in the `ZAPOSLENI` table. This prevents an attacker from probing which email addresses are registered in the system.

**Magic link proves email ownership** — The original pipeline included an OTP step after the magic link. This was removed because clicking the link already demonstrates that the user has access to the inbox. Requiring a second email code was redundant and added friction with no security benefit.

**BCrypt compatibility** — Passwords are hashed using `org.mindrot/jbcrypt` with cost factor 10, producing the `$2a$10$...` format. Spring Boot's `BCryptPasswordEncoder` uses the same algorithm and cost factor, so password verification at login works transparently without any special handling on either side.

**Lazy database connection pool** — The HikariCP datasource is wrapped in `(defonce ... (delay ...))`. The connection pool is created on the first actual database call and reused for the lifetime of the JVM process. This avoids connection errors at startup if MySQL is still initializing.

**UTC timestamps** — All token expiry comparisons use `UTC_TIMESTAMP()` in SQL queries rather than `NOW()`. This prevents false expiry when the MySQL server's local timezone differs from UTC (e.g. UTC+2 in Serbia would make a freshly created token appear expired by 2 hours).


---

## Production Improvements

The current implementation is suitable for an academic project and local demonstration. For a production deployment, the following improvements would be recommended:

- Create a dedicated MySQL user with least-privilege access instead of using root credentials.
- Store SMTP and database secrets in a secret manager rather than plain environment variables.
- Add rate limiting to `POST /api/register` to reduce abuse of the email-sending endpoint.
- Add structured request logging with correlation IDs across Nginx, the Clojure service, and the Spring Boot backend.
- Add integration tests that run against a disposable MySQL container.
- Make the default assigned role configurable instead of hardcoding `ROLE_ID=2`.
- Add token cleanup for expired and used registration tokens.
