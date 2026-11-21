# Insurance Claims Processing API

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

A production-grade, domain-driven REST API for health and medical insurance claim adjudication. Built with **Java 21**, **Spring Boot**, **Spring Data JPA**, **PostgreSQL**, and **Docker**.

---

## Resume Highlights

- **Built a claims REST API** with a Strategy-pattern rules engine (`Eligibility`, `Coverage`, `DuplicateClaim`, `TimelyFiling`), strict claim lifecycle state machine (HTTP 409), paginated list endpoints, and an append-only audit trail with `X-Request-Id` correlation.
- **Implemented benefit calculations** (deductible, coinsurance, annual maximum) using precise decimal arithmetic (`BigDecimal`, `HALF_UP`), secured with Spring Security, HS256 JWT, RBAC (`MEMBER`, `ADJUSTER`, `ADMIN`), immediate account-disable token revocation, and per-IP auth rate limiting.
- **Wrote 94 JUnit 5 and Mockito tests** covering boundary edge cases, production safety checks, rate limiting, and end-to-end HTTP flows; packaged with Spring Boot Actuator, structured ECS JSON logging, multi-profile Docker Compose (`dev` vs `prod`), and GitHub Actions CI.

---

## Key Features

1. **Strategy-Pattern Rules Engine**:
   - `EligibilityRule`: Validates that the policy is `ACTIVE` and the date of service falls inside the policy coverage term (`startDate` to `endDate`, both ends inclusive).
   - `CoverageRule`: Verifies procedure / CPT service code exists in the catalog and is covered.
   - `DuplicateClaimRule`: Rejects duplicate submissions for identical member, provider, service code, date, and billed amount.
   - `TimelyFilingRule`: Enforces the 90-day filing window (`submissionDate <= serviceDate + 90 days`).
   - **Composite aggregation**: Collects and reports *all* failed rules rather than failing silently on the first one.

2. **Claim Lifecycle State Machine**:
   - `SUBMITTED` $\rightarrow$ `IN_REVIEW` $\rightarrow$ `APPROVED` or `DENIED` $\rightarrow$ `PAID`.
   - `DENIED` and `PAID` are terminal states.
   - Any invalid state transition (such as skipping review or paying a denied claim) is blocked at both the domain model and API layers with an RFC 9457 `409 Conflict` response.

3. **Financial Benefit Calculation Engine**:
   - Pure, stateless `BenefitCalculator` operating entirely on `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`.
   - Sequential adjudication pipeline:
     1. **Deductible applied**: `min(billedAmount, remainingDeductible)`
     2. **Coinsurance split**: `amountAfterDeductible * coinsuranceRate` (e.g., 80% plan / 20% member)
     3. **Annual Maximum cap**: Plan payout is capped at `remainingAnnualMaximum`; any excess flows to member responsibility.
   - **Reconciliation invariant**: `deductibleApplied + coinsuranceAmount + overMaxAmount + planPays == billedAmount`.
   - **Atomic accumulator updates**: Approving a claim updates the policy's `deductibleMet` and `benefitsPaidYtd` in the same database transaction.

4. **Security & Role-Based Access Control (RBAC)**:
   - Stateless JWT authentication via Spring Security's OAuth2 Resource Server (Nimbus HS256) with immediate token revocation when an account is disabled.
   - Per-IP brute-force rate limiting on `/api/v1/auth/login` and `/api/v1/auth/register` (`429 Too Many Requests` + `Retry-After`).
   - `ROLE_MEMBER`: Can self-register (`POST /api/v1/auth/register`), view their own policies (`GET /api/v1/me/policies`), and submit/view their own claims.
   - `ROLE_ADJUSTER`: Can access the paginated claim queue, initiate reviews, trigger rules engine adjudication, and authorize payouts.
   - `ROLE_ADMIN`: Can manage users (`/api/v1/admin/users`), issue/update policies (`/api/v1/admin/policies`), manage the CPT service catalog (`/api/v1/admin/covered-services`), query the audit trail (`/api/v1/admin/audit-events`), and read Actuator metrics.

5. **Operations & Production Hardening**:
   - **No hardcoded secrets in base/prod config**: `ProductionSafetyCheck` fails fast on startup if `prod` is combined with `dev`/`test` or uses a publicly known key.
   - **Separated demo seed data**: `db/demo/R__demo_data.sql` only runs under the `dev`/`test` profiles; `prod` runs only schema + reference CPT catalog migrations.
   - **Observability**: Spring Boot Actuator (`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/metrics`), `X-Request-Id` correlation header, ECS JSON structured logging in `prod`, and a database-backed `audit_event` table.
   - **Clean API & Error Handling**: Bean Validation, RFC 9457 `ProblemDetail` responses, `PageResponse` pagination (`?page=&size=`), and OpenAPI 3 / Swagger UI (disabled by default in `prod`).

---

## Architecture & Design Decisions

### 1. Why the Strategy Pattern for the Rules Engine?
- **Open-Closed Principle (OCP)**: Adjudication rules in health insurance evolve constantly (e.g., pre-authorization requirements, out-of-network penalties, fraud scoring). With the Strategy pattern, each rule is an independent, single-responsibility Spring bean implementing `ClaimRule`. Adding a new rule requires authoring a single class without touching existing rules or the engine runner.
- **Testability**: Each rule strategy is independently testable in isolation using plain unit tests without booting up Spring or mocking complex service webs.
- **Auditability & Full Diagnostics**: Rather than short-circuiting with nested `if/else` checks, the engine iterates through all configured strategies and produces a unified `RulesEvaluation` containing the individual pass/fail status and explanatory reason for every rule.

### 2. Why `BigDecimal` for Benefit Calculations?
- **Avoid Binary Floating-Point Errors**: Standard `double` and `float` types use IEEE 754 floating-point representation, which cannot accurately represent decimal fractions (e.g., `0.1 + 0.2 = 0.30000000000000004`). In insurance and financial systems, fractional cent discrepancies accumulate into balance errors and regulatory violations.
- **Controlled Rounding**: All monetary figures are maintained at scale 2 using `RoundingMode.HALF_UP`. Rounding is performed once on the plan share, and the member's responsibility is computed by exact subtraction (`billedAmount - planPays`), guaranteeing that the sum of the components always reconciles to the exact penny.
- **Pure Function Architecture**: `BenefitCalculator` takes a plain record (`BenefitInput`) and returns a plain record (`BenefitResult`). It has zero repository or database dependencies, enabling instantaneous unit testing of complex boundary scenarios.

### 3. Why Finite State Transitions on the Claim Lifecycle?
- **Fraud & Double-Payment Prevention**: Permitting arbitrary status changes allows illegal operations—such as paying an unreviewed claim, re-paying a settled claim, or approving an already denied claim.
- **Defense in Depth**: The transition rules are defined in the `ClaimStatus` enum (`allowedNext()`), checked inside the `Claim` domain entity (`transitionTo()`), guarded inside `ClaimService`, and enforced via database `CHECK` constraints. Any violation produces an informative `409 Conflict` error indicating the current status, requested status, and permitted next states.

### 4. Real-time Policy Accumulator Updates
- Insurance policies operate over a plan year. Each approved claim reduces the member's remaining annual deductible and claims against their annual maximum.
- In `ClaimService.adjudicate()`, the claim status transition, benefit assignment, and `Policy` accumulator increments (`deductibleMet += deductibleApplied`, `benefitsPaidYtd += planPaid`) execute inside a single `@Transactional` boundary, preventing race conditions and stale accumulator reads on subsequent claims.

### 5. Multi-Tenant Member Isolation
- A member cannot view, modify, or submit claims against a policy owned by another user.
- If a member attempts to access an unauthorized policy or claim ID, the API returns `404 Not Found` (rather than `403 Forbidden`) to prevent enumeration attacks and avoid leaking the existence of other members' policies.

---

## Domain State Machine

```
   [SUBMITTED]
        |
        v  (Adjuster starts review)
   [IN_REVIEW]
      /     \
(pass)       (fail)
    v         v
[APPROVED]   [DENIED] (Terminal)
    |
    v  (Disburse payout)
  [PAID] (Terminal)
```

---

## Seed Data & Demo Credentials (`dev` profile only)

When running with `SPRING_PROFILES_ACTIVE=dev` (the default in `docker-compose.yml`), Flyway seeds demo accounts (password for all is `password`):

| Username | Password | Role | Description |
| :--- | :--- | :--- | :--- |
| `member1` | `password` | `MEMBER` | Insured member with active policy `POL-1001` ($500 deductible, $10,000 max) |
| `member2` | `password` | `MEMBER` | Insured member with policy `POL-1002` ($100 remaining annual max) |
| `adjuster1` | `password` | `ADJUSTER` | Claims adjuster who reviews and adjudicates submitted claims |
| `admin1` | `password` | `ADMIN` | Administrator who manages users, policies, CPT codes and reads the audit log |

---

## Quickstart

### Option A: Local Development via Docker Compose (Recommended)

1. Start PostgreSQL and the API (`dev` profile):
   ```bash
   docker compose up --build
   ```
2. Open Swagger UI in your browser:
   [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Option B: Production Deployment via Docker Compose

1. Copy `.env.example` to `.env` and fill in real secrets (`JWT_SECRET`, `DB_PASSWORD`, `BOOTSTRAP_ADMIN_PASSWORD`).
2. Start the production stack (`prod` profile, no demo users, Postgres internal-only):
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env up -d --build
   ```
3. After the first boot creates the bootstrap admin, remove `BOOTSTRAP_ADMIN_*` from `.env` and place a TLS-terminating reverse proxy (Nginx, Caddy, Cloudflare, or cloud load balancer) in front of port `8080`.

### Option C: Run Locally with Maven

1. Start PostgreSQL (e.g., `docker compose up postgres -d`).
2. Run the application with the `dev` profile:
   ```bash
   SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
   ```

### Running Tests

Execute the 94 unit and integration tests:
```bash
./mvnw clean test
```

---

## API Walkthrough

### 1. Authenticate as a Member
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"member1","password":"password"}'
```
*Copy the `accessToken` from the JSON response.*

### 2. Submit a Claim
```bash
curl -s -X POST http://localhost:8080/api/v1/claims \
  -H "Authorization: Bearer <MEMBER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "policyNumber": "POL-1001",
    "providerName": "Metro General Hospital",
    "serviceCode": "99213",
    "serviceDate": "2026-10-01",
    "billedAmount": 1000.00
  }'
```

### 3. Authenticate as an Adjuster
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"adjuster1","password":"password"}'
```

### 4. Move Claim to IN_REVIEW
```bash
curl -s -X POST http://localhost:8080/api/v1/adjuster/claims/1/review \
  -H "Authorization: Bearer <ADJUSTER_TOKEN>"
```

### 5. Adjudicate Claim (Rules Engine + Payment Math)
```bash
curl -s -X POST http://localhost:8080/api/v1/adjuster/claims/1/adjudicate \
  -H "Authorization: Bearer <ADJUSTER_TOKEN>"
```
*Response shows all 4 passing rule evaluations and the benefit breakdown ($500 deductible applied, $400 plan payout, $600 member responsibility).*

### 6. Disburse Payment
```bash
curl -s -X POST http://localhost:8080/api/v1/adjuster/claims/1/pay \
  -H "Authorization: Bearer <ADJUSTER_TOKEN>"
```

---

## Test Suite Coverage

The test suite contains **94 automated tests**:
- **`ClaimStatusTest` (21 tests)**: Parameterized test verifying all 25 pairs in the status transition matrix; terminal state checks.
- **`BenefitCalculatorTest` (23 tests)**: Deductible boundaries, partial deductible, annual maximum cap, fractional cent rounding, and mathematical reconciliation invariant.
- **`EligibilityRuleTest` (6 tests)**: Active status, incident on first day, incident on exact last day of coverage, incident day after expiration.
- **`TimelyFilingRuleTest` (9 tests)**: Window boundary checks (day 89, day 90 pass, day 91 fail), leap-year calculations.
- **`CoverageRuleTest` (3 tests)**: Covered code, excluded code, unknown code.
- **`DuplicateClaimRuleTest` (3 tests)**: Detection of identical claims; self-exclusion check.
- **`ClaimRulesEngineTest` (3 tests)**: Multi-rule aggregation and ordering.
- **`ClaimServiceTest` (5 tests)**: Unit testing service transactions, accumulator updates, and permission guards.
- **`AuthRateLimitFilterTest` (2 tests)**: Per-IP window throttling, `429` + `Retry-After`, and window reset.
- **`ProductionSafetyCheckTest` (3 tests)**: Fail-fast rejection of `prod`+`dev` or known public JWT secrets.
- **`BootstrapAdminRunnerTest` (3 tests)**: First-boot admin creation, idempotency, and password length guard.
- **`ClaimsApiIntegrationTest` (13 tests)**: End-to-end HTTP integration tests covering JWT auth, self-registration, RBAC, immediate token revocation on user disable, admin policy/catalog/audit endpoints, pagination, Actuator probes/metrics, member isolation, and error payloads.
