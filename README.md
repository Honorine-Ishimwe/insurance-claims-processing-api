# Insurance Claims Processing API

[![CI Build & Test](https://github.com/Honorine-Ishimwe/insurance-claims-processing-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Honorine-Ishimwe/insurance-claims-processing-api/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

A production-ready, domain-driven **Java 21 / Spring Boot REST API** for health insurance claim adjudication. Members submit claims, adjusters review and adjudicate them through a Strategy-pattern rules engine and exact `BigDecimal` benefit calculator, and administrators manage users, policies, covered CPT codes, and an append-only audit trail.

---

## Table of Contents

- [Key Features](#key-features)
- [Architecture](#architecture)
- [Claim Lifecycle](#claim-lifecycle)
- [API Reference](#api-reference)
- [Quickstart](#quickstart)
- [End-to-End Walkthrough](#end-to-end-walkthrough)
- [Environment Configuration](#environment-configuration)
- [Testing](#testing)

---

## Key Features

- **Strategy-Pattern Rules Engine**: Evaluates every claim against 4 independent rules and returns *all* pass/fail reasons instead of failing on the first error:
  - `EligibilityRule` — Policy is `ACTIVE` and date of service is within `startDate..endDate` (inclusive).
  - `CoverageRule` — Procedure/CPT code exists in `covered_service` and is covered.
  - `DuplicateClaimRule` — Rejects non-denied duplicates for the same member, provider, service code, date, and billed amount.
  - `TimelyFilingRule` — Enforces the 90-day filing window (`submissionDate <= serviceDate + 90 days`).
- **Strict Claim State Machine**: Enforces `SUBMITTED → IN_REVIEW → APPROVED / DENIED → PAID` at the enum, entity, service, and database `CHECK` constraint layers. Invalid transitions return RFC 9457 `409 Conflict`.
- **Exact Financial Benefit Calculator**: Stateless `BigDecimal` (`scale=2`, `HALF_UP`) adjudication applying remaining deductible, coinsurance split, and annual maximum cap while updating policy YTD accumulators atomically in the same `@Transactional` boundary.
- **Security & RBAC**:
  - Stateless HS256 JWT authentication via Spring Security OAuth2 Resource Server.
  - Three roles: `MEMBER`, `ADJUSTER`, and `ADMIN`.
  - Immediate JWT revocation when an admin disables an account.
  - Per-IP brute-force rate limiting (`429 Too Many Requests` + `Retry-After`) on `/api/v1/auth/login` and `/register`.
  - Multi-tenant member isolation (unauthorized policy/claim lookups return `404` to prevent enumeration).
- **Production Hardening & Observability**:
  - Append-only `audit_event` table + dedicated `AUDIT` logger with `X-Request-Id` correlation on every request.
  - Spring Boot Actuator (`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/metrics`).
  - `ProductionSafetyCheck` fails fast on boot if `prod` is combined with `dev`/`test` or uses a known dev/test JWT secret.
  - Paginated list endpoints (`PageResponse` with `?page=&size=`, capped at 100) and structured ECS JSON logs in `prod`.

---

## Architecture

Organized **package-by-feature** with a clean layered flow (`Security Filter Chain → Controller → Service / Rules & Benefit Engines → JPA Repository → PostgreSQL`):

```text
src/main/java/com/claims/api/
├── admin/       # AdminController (users, policies, CPT catalog, audit log)
├── audit/       # AuditEvent entity, AuditAction enum, AuditService
├── benefit/     # Pure BigDecimal BenefitCalculator + BenefitInput / BenefitResult
├── claim/       # MemberClaimController, AdjusterClaimController, ClaimService, Claim, ClaimStatus, DTOs
├── common/      # GlobalExceptionHandler (RFC 9457), RequestIdFilter, PageResponse, PageParams
├── config/      # AppConfig (OpenAPI, Clock), ProductionSafetyCheck, BootstrapAdminRunner
├── policy/      # MemberPolicyController, PolicyService, Policy, CoveredService, DTOs
├── rules/       # ClaimRulesEngine + ClaimRule implementations (Eligibility, Coverage, Duplicate, TimelyFiling)
├── security/    # SecurityConfig, AuthController, TokenService, AuthRateLimitFilter
└── user/        # UserService, AppUser, Role, DTOs
```

---

## Claim Lifecycle

```text
   [SUBMITTED]
        │
        ▼  POST /api/v1/adjuster/claims/{id}/review
   [IN_REVIEW]
      ╱   ╲
 (pass)   (fail)
    ▼       ▼
[APPROVED] [DENIED] (terminal)
    │
    ▼  POST /api/v1/adjuster/claims/{id}/pay
  [PAID] (terminal)
```

---

## API Reference

Interactive OpenAPI 3 / Swagger UI is available in `dev` at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (disabled by default in `prod`).

### Authentication (Public, Rate Limited)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Self-register a new `MEMBER` account |
| `POST` | `/api/v1/auth/login` | Authenticate with username/password and receive a JWT |

### Member (`ROLE_MEMBER`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/me/policies` | List own policies with remaining deductible & annual maximum |
| `POST` | `/api/v1/claims` | Submit a new claim (`SUBMITTED`) |
| `GET` | `/api/v1/claims?page=0&size=20` | List own claims, newest first (paginated) |
| `GET` | `/api/v1/claims/{id}` | View a single claim and its benefit breakdown |

### Adjuster (`ROLE_ADJUSTER`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/adjuster/claims?status=SUBMITTED&page=0&size=20` | List/filter claim queue (paginated) |
| `GET` | `/api/v1/adjuster/claims/{id}` | View any claim |
| `POST` | `/api/v1/adjuster/claims/{id}/review` | Transition `SUBMITTED → IN_REVIEW` |
| `POST` | `/api/v1/adjuster/claims/{id}/adjudicate` | Run rules engine & benefit math (`APPROVED` or `DENIED`) |
| `POST` | `/api/v1/adjuster/claims/{id}/pay` | Disburse payout (`APPROVED → PAID`) |

### Administration (`ROLE_ADMIN`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/admin/users` | Create a user of any role (`MEMBER`, `ADJUSTER`, `ADMIN`) |
| `GET` | `/api/v1/admin/users?role=&page=0&size=20` | List users, optionally filtered by role |
| `GET` | `/api/v1/admin/users/{id}` | Get user details |
| `PATCH` | `/api/v1/admin/users/{id}/enabled` | Enable or disable a user (revokes active JWTs immediately) |
| `POST` | `/api/v1/admin/policies` | Issue a new policy to an existing `MEMBER` |
| `GET` | `/api/v1/admin/policies?status=&page=0&size=20` | List policies, optionally filtered by status |
| `GET` | `/api/v1/admin/policies/{policyNumber}` | Get policy details and YTD accumulators |
| `PATCH` | `/api/v1/admin/policies/{policyNumber}/status` | Update policy status (`ACTIVE`, `LAPSED`, `CANCELLED`) |
| `GET` | `/api/v1/admin/covered-services?page=0&size=20` | List the CPT service catalog |
| `PUT` | `/api/v1/admin/covered-services/{serviceCode}` | Create or update a 5-digit CPT service code |
| `GET` | `/api/v1/admin/audit-events?entityType=&entityId=&actor=` | Query the append-only audit trail |

### Operations

| Method | Endpoint | Access |
| :--- | :--- | :--- |
| `GET` | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info` | Public |
| `GET` | `/actuator/metrics` | `ROLE_ADMIN` |

---

## Quickstart

### 1. Local Development (Docker Compose)

Runs PostgreSQL 16 and the API with `SPRING_PROFILES_ACTIVE=dev` (seeds demo users and enables Swagger UI):

```bash
docker compose up --build
```

Open Swagger UI at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

#### Demo Credentials (`dev` profile only)

| Username | Password | Role | Notes |
| :--- | :--- | :--- | :--- |
| `member1` | `password` | `MEMBER` | Active policy `POL-1001` ($500 deductible, 80% coinsurance, $10,000 max) + lapsed `POL-1003` |
| `member2` | `password` | `MEMBER` | Active policy `POL-1002` (deductible met, $100 remaining annual max) |
| `adjuster1` | `password` | `ADJUSTER` | Reviews, adjudicates, and pays claims |
| `admin1` | `password` | `ADMIN` | Manages users, policies, CPT codes, and reads the audit log |

### 2. Local Development (Maven)

```bash
docker compose up postgres -d
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

### 3. Production Deployment (Docker Compose)

Production runs with `SPRING_PROFILES_ACTIVE=prod` (no demo users, Swagger disabled, ECS JSON logs, PostgreSQL internal-only):

```bash
cp .env.example .env
# Generate strong secrets and paste them into .env:
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 24   # DB_PASSWORD
```

Set `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` (12+ chars) in `.env`, then start:

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

After the first boot creates the initial admin account, remove `BOOTSTRAP_ADMIN_*` from `.env` and place a TLS-terminating reverse proxy (Nginx, Caddy, Cloudflare, or cloud load balancer) in front of port `8080`.

---

## End-to-End Walkthrough

With the local `dev` stack running:

```bash
# 1. Log in as member1
MEMBER_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"member1","password":"password"}' | jq -r .accessToken)

# 2. Submit a $1,000 claim against POL-1001
CLAIM_ID=$(curl -s -X POST http://localhost:8080/api/v1/claims \
  -H "Authorization: Bearer $MEMBER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "policyNumber": "POL-1001",
    "providerName": "Metro General Hospital",
    "serviceCode": "99213",
    "serviceDate": "2026-10-01",
    "billedAmount": 1000.00
  }' | jq -r .id)

# 3. Log in as adjuster1
ADJUSTER_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"adjuster1","password":"password"}' | jq -r .accessToken)

# 4. Start review -> Adjudicate ($500 deductible, $400 plan payout, $600 member share) -> Pay
curl -s -X POST "http://localhost:8080/api/v1/adjuster/claims/$CLAIM_ID/review" \
  -H "Authorization: Bearer $ADJUSTER_TOKEN"

curl -s -X POST "http://localhost:8080/api/v1/adjuster/claims/$CLAIM_ID/adjudicate" \
  -H "Authorization: Bearer $ADJUSTER_TOKEN" | jq

curl -s -X POST "http://localhost:8080/api/v1/adjuster/claims/$CLAIM_ID/pay" \
  -H "Authorization: Bearer $ADJUSTER_TOKEN" | jq
```

---

## Environment Configuration

See [`.env.example`](.env.example) for all supported variables. Key settings:

| Variable | Required in `prod` | Description |
| :--- | :--- | :--- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Yes | PostgreSQL connection settings |
| `JWT_SECRET` | Yes | Base64-encoded HS256 secret (≥ 32 bytes decoded) |
| `JWT_TTL_MINUTES` | No (default `60`) | Access token lifetime |
| `BOOTSTRAP_ADMIN_USERNAME` / `_PASSWORD` | First boot only | Seeds one `ADMIN` if none exists |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated allowed browser origins |
| `RATE_LIMIT_MAX_REQUESTS` / `_WINDOW_SECONDS` | No (`10` / `60`) | Per-IP auth rate limit |
| `SWAGGER_ENABLED` | No (`false` in `prod`) | Toggle OpenAPI / Swagger UI |

---

## Testing

Run the full suite of **94 automated JUnit 5 & Mockito tests** (uses in-memory H2 in PostgreSQL compatibility mode with Flyway migrations):

```bash
./mvnw clean test
```

| Test Class | Tests | Coverage |
| :--- | :---: | :--- |
| `BenefitCalculatorTest` | 23 | Deductible boundaries, coinsurance, annual max cap, `HALF_UP` rounding, reconciliation invariant |
| `ClaimStatusTest` | 21 | All 25 transition pairs in the claim state machine & terminal state checks |
| `ClaimsApiIntegrationTest` | 13 | End-to-end HTTP flows: JWT auth, RBAC, self-registration, token revocation on disable, admin APIs, pagination, audit trail, Actuator, member isolation |
| `TimelyFilingRuleTest` | 9 | 90-day boundary (day 89/90 pass, day 91 fail), leap-year dates |
| `EligibilityRuleTest` | 6 | Policy status and coverage term boundary dates |
| `ClaimServiceTest` | 5 | Transactional adjudication, accumulator updates, ownership checks |
| `CoverageRuleTest` | 3 | Covered, excluded, and unknown CPT codes |
| `DuplicateClaimRuleTest` | 3 | Duplicate detection and self-exclusion |
| `ClaimRulesEngineTest` | 3 | Multi-rule evaluation and aggregation |
| `ProductionSafetyCheckTest` | 3 | Rejects `prod`+`dev` and known public JWT secrets |
| `BootstrapAdminRunnerTest` | 3 | First-boot admin creation, idempotency, password length guard |
| `AuthRateLimitFilterTest` | 2 | Per-IP `429` throttling, `Retry-After`, and window reset |
