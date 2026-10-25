# Credit Card Billing & Statement Management System

A secure, session-authenticated **full-stack** credit card management and billing application: a REST backend built with **Java, Spring Boot, Spring Data JPA/Hibernate, Spring Security, Flyway, MySQL and H2**, plus a bundled **web UI** (plain HTML/CSS/JavaScript served by Spring Boot itself: no Node, no build step, no framework).
It models a realistic financial backend for a portfolio: customers and administrators, card lifecycle, BIN-level billing rules, statement generation, finance charges, GST, late fees, simulated payments with idempotency, an append-only late-fee ledger and audit trail.

> **Scope and honesty notes**
> * All card data is **synthetic**. No card-network connectivity, no real payment gateway, and **no PCI DSS claim** of any kind.
> * The application has not been production-certified, deployed or externally audited.
> * See [Verification status](#verification-status) for exactly what was and was not executed before this ZIP was produced.

## Contents
0. [Web UI](#web-ui) · 1. [Features](#1-features) · 2. [Versions](#2-exact-technology-versions) · 3. [Prerequisites](#3-prerequisites) · 4. [Open in VS Code](#4-extract-and-open-in-vs-code) · 5. [Run with H2](#5-run-with-h2-no-mysql-needed) · 6. [Run with MySQL](#6-run-with-mysql) · 7. [Flyway](#7-how-flyway-migrations-work) · 8. [Initial admin](#8-initial-administrator) · 9. [Start / URL](#9-start-the-application) · 10. [Auth guide](#10-authentication-and-authorization) · 11. [API reference](#11-rest-api) · 12. [Business rules & formulas](#12-business-rules-formulas-and-assumptions) · 13. [Tests](#13-tests) · 14. [Build JAR](#14-build-the-executable-jar) · 15. [Troubleshooting](#15-troubleshooting) · 16. [GitHub](#16-push-to-github) · 17. [Design notes](#17-design-notes-and-limitations) · [Verification status](#verification-status)

---

## Web UI
Start the application and open **http://localhost:8080**. One process serves both the UI and the API (same origin, so no CORS and the session cookie just works).

| Role | What you can do |
|---|---|
| **Customer** (register on the sign-in page) | Overview with card tiles and credit usage; card details with balance and transactions; statements with a full breakdown (previous balance, purchases, fees, finance charge, GST, credits, minimum due) and the billing-rule snapshot; **pay** a statement (minimum / full / custom amount) with an automatic `Idempotency-Key`; payment history; manage payment methods; late-fee ledger; profile |
| **Administrator** (`admin` + the password printed in the console, or your `BOOTSTRAP_ADMIN_*`) | Overview and recent activity; customers (enable/disable); **BIN rules** (create/edit, rule versions); **cards** (issue, block/unblock/close, change limit); post **transactions** (purchase, fee, refund); **generate statements**; **assess late fees**; payments (reverse with a reason); late-fee ledger; **audit log** with filters; administrator accounts |

First-run walkthrough: register a customer → sign in as administrator → *Cards* → *Issue card* → open the card → *Post transaction* a few times → *Generate statement* (the period-end date defaults to the latest billing day, which must have passed) → sign in as the customer → *Statements* → *Pay*.

Implementation notes: vanilla ES modules in `src/main/resources/static` (`js/api.js` handles the session cookie, the CSRF token with one automatic retry, and uniform errors; all data is inserted as text, never as HTML). A strict Content-Security-Policy (`script-src 'self'`, no inline scripts or styles, `frame-ancestors 'none'`) is sent with every response. The UI only hides what a role cannot use; **every rule is still enforced by the server**.

---

## 1. Features
* **Customers**: self-registration, profile, isolation (a customer can only ever see their own cards, entries, statements, payments, late fees).
* **Administrators**: separate accounts and role; manage customers, BIN configurations, cards, billing entries, statements, late fees, payment reversals, audit log.
* **Cards**: ACTIVE / BLOCKED / CLOSED lifecycle (CLOSED is terminal), credit limit, only a **masked** number is stored/returned (`411111******1234`); SHA-256 hash for uniqueness; full number is never persisted or logged.
* **BIN configuration**: billing cycle day, due days, minimum-due rule, finance charge rate, GST rate, late-fee rule; validated (including cross-field conflicts) and versioned (`ruleVersion`).
* **Billing**: simulated transaction feed (purchases, fees, refunds), statement generation per card/period with finance charge, GST, minimum due; duplicate-safe; historical statements keep a **snapshot of the rules** used.
* **Payments**: simulated; partial/full; idempotent (`Idempotency-Key`); atomic with statement balance, allocation and audit; administrator reversal policy.
* **Late-fee ledger**: append-only, one assessment per statement, stores the rule snapshot.
* **Audit log**: append-only record of business and administrative operations (never passwords, session ids or full card numbers).
* **Security**: Spring Security session login, delegating password encoder (BCrypt), CSRF protection, role checks at URL level **and** in services, session-fixation protection, hardened session cookie, JSON 401/403, no CORS by default.

## 2. Exact technology versions
| Component | Version |
|---|---|
| Java | 17 or newer (compiled with `release 17`) |
| Build tool | Maven 3.6.3+ (3.9.x recommended) |
| Spring Boot | **3.3.5** (parent POM) |
| Spring Framework / Security / Data JPA | 6.1.x / 6.3.x / 3.3.x (managed by Boot 3.3.5) |
| Hibernate ORM | 6.5.x (managed; 6.5.3.Final in Boot 3.3.5) |
| Flyway (`flyway-core`, `flyway-mysql`) | 10.10.x (managed) |
| H2 | 2.2.x (managed; 2.2.224 in Boot 3.3.5) |
| MySQL server / Connector/J | MySQL 8.0+ / Connector/J 8.3.x (managed) |
| JUnit Jupiter / Mockito | 5.10.x / 5.11.x (managed via `spring-boot-starter-test`) |

Only Boot's dependency management is used (no hand-pinned versions). Run `mvn dependency:tree` to see the exact resolved patch versions. Dependencies: `starter-web`, `starter-data-jpa`, `starter-security`, `starter-validation` (Bean Validation for request DTOs), `flyway-core`, `flyway-mysql` (required by Flyway 10 for MySQL), `mysql-connector-j`, `h2`, `starter-test`, `spring-security-test`. No Lombok, JWT, MapStruct, Testcontainers, Actuator, Swagger or Docker. The UI is plain HTML/CSS/JavaScript with no framework, no package manager and no build step.

## 3. Prerequisites
* JDK 17+ (`java -version`) and Maven 3.6.3+ (`mvn -version`). The project has no Maven wrapper, so Maven must be installed.
* Optional: MySQL 8.x for the persistent profile.
* VS Code with the **Extension Pack for Java** (optional but recommended).

## 4. Extract and open in VS Code
```bash
unzip credit-card-billing-system.zip
cd credit-card-billing-system
code .
```
Windows PowerShell:
```powershell
Expand-Archive .\credit-card-billing-system.zip -DestinationPath .
cd .\credit-card-billing-system
code .
```
VS Code detects `pom.xml` and imports it as a Maven project. You can run `CcBillingApplication` from the editor ("Run" above `main`) or use the terminal commands below.

## 5. Run with H2 (no MySQL needed)
H2 is the **default profile** (`spring.profiles.active=h2`).
```bash
mvn spring-boot:run
```
* Database: in-memory H2 in MySQL compatibility mode (`jdbc:h2:mem:ccbilling;MODE=MySQL;DB_CLOSE_DELAY=-1`).
* **Lifecycle**: all data (including users) is lost when the application stops; Flyway recreates the schema and seed data at every start.
* To keep data between restarts use a file database (the `data/` folder is git-ignored):
  * macOS/Linux: `H2_URL="jdbc:h2:file:./data/ccbilling;MODE=MySQL" mvn spring-boot:run`
  * PowerShell: `$env:H2_URL="jdbc:h2:file:./data/ccbilling;MODE=MySQL"; mvn spring-boot:run`
* The H2 web console is disabled.

## 6. Run with MySQL
1. Create a database and a dedicated user (choose your own password):
```sql
CREATE DATABASE ccbilling CHARACTER SET utf8mb4;
CREATE USER 'ccbilling'@'localhost' IDENTIFIED BY '<your-local-password>';
GRANT ALL PRIVILEGES ON ccbilling.* TO 'ccbilling'@'localhost';
```
2. Export configuration (see `.env.example`; Spring Boot does **not** read `.env` files itself, so export the variables in your shell or IDE run configuration):
```bash
export SPRING_PROFILES_ACTIVE=mysql
export DB_USERNAME=ccbilling
export DB_PASSWORD='<your-local-password>'
export BOOTSTRAP_ADMIN_USERNAME=admin
export BOOTSTRAP_ADMIN_PASSWORD='<a long passphrase, 12+ characters>'
mvn spring-boot:run
```
PowerShell:
```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"; $env:DB_USERNAME="ccbilling"; $env:DB_PASSWORD="<your-local-password>"
$env:BOOTSTRAP_ADMIN_USERNAME="admin"; $env:BOOTSTRAP_ADMIN_PASSWORD="<a long passphrase, 12+ characters>"
mvn spring-boot:run
```
3. Optional `DB_URL` overrides the default `jdbc:mysql://localhost:3306/ccbilling?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true` (those two flags are for **local development only**; use TLS in any real environment).

**MySQL-only append-only triggers.** With the `mysql` profile Flyway also applies `db/migration-mysql/V100__append_only_triggers.sql`, which blocks UPDATE/DELETE on `audit_logs`, `late_fee_ledger` and `payment_allocations`. This needs the `TRIGGER` privilege and, when binary logging is on, `SUPER` or `log_bin_trust_function_creators=1`. To skip it: `FLYWAY_LOCATIONS=classpath:db/migration`.

**Optional database-permission hardening (MySQL).** Run the application once with the privileged user so Flyway can migrate, then run it with a restricted runtime user and `SPRING_FLYWAY_ENABLED=false`:
```sql
CREATE USER 'ccbilling_app'@'localhost' IDENTIFIED BY '<runtime-password>';
GRANT SELECT, INSERT, UPDATE, DELETE ON ccbilling.customers TO 'ccbilling_app'@'localhost';
-- repeat for: administrators, bin_configurations, credit_cards, statements, billing_entries, payment_methods, payments
GRANT SELECT, INSERT ON ccbilling.audit_logs TO 'ccbilling_app'@'localhost';
GRANT SELECT, INSERT ON ccbilling.late_fee_ledger TO 'ccbilling_app'@'localhost';
GRANT SELECT, INSERT ON ccbilling.payment_allocations TO 'ccbilling_app'@'localhost';
```
(`billing_entries` needs UPDATE because `statement_id` is set when an entry is billed.)

## 7. How Flyway migrations work
* Flyway owns the schema. Hibernate runs with `ddl-auto=validate`: it only checks that the entity mappings match the migrated schema and never changes it.
* `src/main/resources/db/migration`: `V1__initial_schema.sql` (all tables, constraints, indexes), `V2__seed_bin_configurations.sql` (two synthetic BINs: `411111`, `520000`).
* `src/main/resources/db/migration-mysql`: MySQL-only `V100__append_only_triggers.sql` (kept in a **sibling** folder because Flyway scans sub-folders recursively).
* The same SQL runs on MySQL and on H2 (MODE=MySQL). Applied migrations are recorded in `flyway_schema_history`. **Never edit an applied migration**; add a new `V3__...sql` instead.

## 8. Initial administrator
There is no default password. On startup `AdminBootstrap` creates an administrator **only if it does not exist yet**, from environment variables:

| Variable | Meaning |
|---|---|
| `BOOTSTRAP_ADMIN_USERNAME` | admin login name |
| `BOOTSTRAP_ADMIN_PASSWORD` | at least 12 characters (startup fails otherwise) |
| `BOOTSTRAP_ADMIN_FULL_NAME` | optional display name |

Local-development convenience (H2 profile only): if none are set and no administrator exists, a random 20-character password is generated and printed **once** in the console banner (`username: admin`). With the `mysql` profile nothing is generated; if no variables are set no administrator is created. More administrators can be created via `POST /api/admin/administrators`.

## 9. Start the application
```bash
mvn spring-boot:run
```
* URL: **http://localhost:8080** opens the web UI (change the port with `SERVER_PORT`). Health: `GET http://localhost:8080/api/health`.
* Customers can register in the UI or at `POST /api/auth/register`. See [Web UI](#web-ui).

## 10. Authentication and authorization
* **Session-based** (`JSESSIONID` cookie: HttpOnly, SameSite=Strict, `Secure` when `SESSION_COOKIE_SECURE=true`, 30 min timeout). No JWT, no OAuth.
* Two user types log in at the same endpoint with `userType` = `CUSTOMER` (identifier = email) or `ADMIN` (identifier = username).
* **CSRF protection is on.** Every state-changing request (`POST/PUT/PATCH/DELETE`, including login and register) needs the token from `GET /api/auth/csrf`, sent in the `X-CSRF-TOKEN` header, together with the session cookie. Get a fresh token after logging in.
* Roles: `/api/admin/**` requires `ROLE_ADMIN`; `/api/customers/me/**` requires `ROLE_CUSTOMER`; admin services additionally carry `@PreAuthorize("hasRole('ADMIN')")`. Customer data is always looked up by the customer id from the session (never from the URL), so another customer's ids return `404`.
* Failed logins return the same generic `401 INVALID_CREDENTIALS` for unknown users and wrong passwords. Logout: `POST /api/auth/logout` → `204`. CORS: not configured, so cross-origin browser calls are blocked.

**bash (curl) session helper**
```bash
BASE=http://localhost:8080; JAR=$(mktemp)
csrf() { curl -s -b "$JAR" -c "$JAR" $BASE/api/auth/csrf | sed -E 's/.*"token":"([^"]+)".*/\1/'; }
api()  { curl -s -b "$JAR" -c "$JAR" -H "X-CSRF-TOKEN: $(csrf)" -H 'Content-Type: application/json' "$@"; }

api -X POST $BASE/api/auth/register -d '{"fullName":"Asha Rao","email":"asha@example.com","phone":"+919876543210","password":"Sup3rSecretPass"}'
api -X POST $BASE/api/auth/login    -d '{"userType":"CUSTOMER","identifier":"asha@example.com","password":"Sup3rSecretPass"}'
api $BASE/api/auth/me
api -X POST $BASE/api/auth/logout
```
**PowerShell**
```powershell
$base = "http://localhost:8080"
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -SessionVariable s
$h = @{ $csrf.headerName = $csrf.token }
Invoke-RestMethod "$base/api/auth/login" -Method Post -WebSession $s -Headers $h -ContentType "application/json" `
  -Body '{"userType":"ADMIN","identifier":"admin","password":"<your admin password>"}'
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -WebSession $s; $h = @{ $csrf.headerName = $csrf.token }
Invoke-RestMethod "$base/api/admin/customers" -WebSession $s
```
Use separate cookie jars for an administrator and a customer when following the workflow below.

## 11. REST API
Errors always look like `{"timestamp","status","code","message","path","fieldErrors"?}`. Lists are paginated: `?page=0&size=20&sort=field,desc` (max size 100) and return `{"content":[…],"page","size","totalElements","totalPages"}`. Money is a JSON number with 2 decimals (INR); dates are ISO-8601 (UTC).

### Public
| Method & path | Description |
|---|---|
| `GET /api/health` | `{"status":"UP"}` |
| `GET /api/auth/csrf` | `{"headerName":"X-CSRF-TOKEN","parameterName":"_csrf","token":"…"}` |
| `POST /api/auth/register` | create customer (`201`, `409` duplicate email, `400` validation) |
| `POST /api/auth/login` | session login (`200`, generic `401`) |
| `POST /api/auth/logout` | `204` |
| `GET /api/auth/me` | current user (authenticated) |

### Customer (`/api/customers/me`)
| Method & path | Description |
|---|---|
| `GET` / `PUT` `/api/customers/me` | profile (`fullName`, `phone`) |
| `GET …/cards`, `GET …/cards/{id}`, `GET …/cards/{id}/balance` | own cards; balance/exposure |
| `GET …/cards/{id}/entries` | own transactions (paged) |
| `GET …/statements?cardId=&status=`, `GET …/statements/{id}` | own statements (the "bills") |
| `POST …/payments` (header `Idempotency-Key`) | pay a statement (`201`; replay → `200` + `Idempotent-Replay: true`) |
| `GET …/payments?statementId=&status=`, `GET …/payments/{id}` | own payments |
| `GET/POST …/payment-methods`, `DELETE …/payment-methods/{id}` | simulated instruments (identifier stored masked; DELETE = deactivate) |
| `GET …/late-fees` | own late-fee ledger rows |

### Administrator (`/api/admin`)
| Method & path | Description |
|---|---|
| `POST/GET /administrators`, `PATCH /administrators/{id}/enabled` | administrator accounts (cannot disable yourself) |
| `GET /customers?email=&status=`, `GET /customers/{id}`, `PATCH /customers/{id}/status` | customers |
| `POST/GET /bin-configurations`, `GET/PUT /bin-configurations/{id}` | BIN billing rules |
| `POST /cards`, `GET /cards?customerId=&status=`, `GET /cards/{id}`, `PATCH /cards/{id}/status`, `PUT /cards/{id}/credit-limit`, `GET /cards/{id}/balance` | card lifecycle |
| `POST/GET /cards/{id}/entries` | post/list purchases, fees, refunds |
| `POST /statements/generate`, `GET /statements?cardId=&status=`, `GET /statements/{id}` | statements |
| `POST /statements/{id}/late-fee`, `GET /late-fees?statementId=&cardId=` | late-fee assessment and ledger |
| `GET /payments?customerId=&statementId=&status=`, `POST /payments/{id}/reverse` | payments |
| `GET /audit-logs?action=&entityType=&entityId=&actorType=&actorId=&from=&to=` | audit trail (`from`/`to` ISO date-time) |

Status codes: `201` created, `200` ok, `204` logout, `400` validation/malformed, `401` unauthenticated/bad credentials, `403` forbidden/CSRF, `404` not found (also for other customers' ids), `409` duplicate/conflict, `422` business-rule violation (e.g. `CREDIT_LIMIT_EXCEEDED`, `PAYMENT_EXCEEDS_OUTSTANDING`).

### Example workflow (JSON)
**1. Admin creates a BIN configuration** `POST /api/admin/bin-configurations`
```json
{"binCode":"412345","name":"Gold","billingCycleDay":15,"paymentDueDays":20,
 "minDuePercentage":5,"minDueFloor":200,"financeChargeMonthlyRate":3.5,"gstRate":18,
 "lateFeePercentage":10,"lateFeeFloor":100,"lateFeeCap":1000,"lateFeeThreshold":500}
```
→ `201` `{"id":3,"binCode":"412345","name":"Gold","rules":{…same values…},"ruleVersion":1,…}`

**2. Admin issues a card** `POST /api/admin/cards` `{"customerId":1,"binConfigurationId":3,"creditLimit":100000.00}`
→ `201` `{"id":1,"customerId":1,"binConfigurationId":3,"binCode":"412345","maskedNumber":"412345******8812","creditLimit":100000.00,"status":"ACTIVE","expiresOn":"2031-10-31",…}`

**3. Admin posts transactions** `POST /api/admin/cards/1/entries`
`{"entryType":"PURCHASE","amount":10000.00,"description":"Electronics","transactionDate":"2026-02-20","referenceId":"p1"}` (`entryType`: `PURCHASE|FEE|REFUND`; `referenceId` makes the call idempotent per card)

**4. Admin generates the statement** `POST /api/admin/statements/generate` `{"cardId":1,"periodEnd":"2026-03-15"}`
→ `201`
```json
{"id":1,"cardId":1,"maskedCardNumber":"412345******8812","periodStart":"2026-02-16","periodEnd":"2026-03-15",
 "dueDate":"2026-04-04","previousBalance":0.00,"purchasesTotal":12500.50,"creditsTotal":500.50,"feesTotal":0.00,
 "financeCharge":0.00,"gstOnFinanceCharge":0.00,"totalAmountDue":12000.00,"minimumAmountDue":600.00,
 "paidAmount":0.00,"outstandingAmount":12000.00,"paymentStatus":"UNPAID","ruleVersion":1,"rulesApplied":{…}}
```
Repeating the call returns `409 STATEMENT_ALREADY_EXISTS`.

**5. Customer adds a payment method and pays** `POST /api/customers/me/payment-methods`
`{"type":"UPI","displayName":"My UPI","identifier":"asha@okbank"}` → `{"id":1,…,"maskedIdentifier":"********bank","active":true}`

`POST /api/customers/me/payments` with header `Idempotency-Key: pay-2026-03-0001` and body `{"statementId":1,"paymentMethodId":1,"amount":300.00}`
→ `201` `{"id":1,"statementId":1,"paymentMethodId":1,"amount":300.00,"status":"COMPLETED",…}`; the statement becomes `PARTIALLY_PAID`, outstanding `11700.00`. Same key + same body → `200` with the same payment; same key + different body → `409 IDEMPOTENCY_KEY_REUSED`.

**6. Late fee** (after the due date) `POST /api/admin/statements/1/late-fee`
→ `201` `{"feeAmount":100.00,"gstRate":18.0000,"gstAmount":18.00,"totalAmount":118.00,"unpaidMinimumDue":300.00,"ruleVersion":1,"ruleSnapshot":"ruleVersion=1;cycleDay=15;…","status":"POSTED",…}`

**7. Next statement** (`periodEnd` 2026-04-15) carries `previousBalance` 11700.00, `financeCharge` 409.50, `gstOnFinanceCharge` 73.71, the late fee in `feesTotal` (118.00), +1000.00 purchases → `totalAmountDue` **13301.21**, `minimumAmountDue` **665.06**.

**8. Reversal** `POST /api/admin/payments/{id}/reverse` `{"reason":"Bounced"}` → payment `REVERSED`, statement balance restored.

## 12. Business rules, formulas and assumptions
**Currency/rounding.** INR; all money is `BigDecimal`, scale 2, `HALF_UP`, rounded once per derived amount; percentages have up to 4 decimals. Business "today" is the **UTC** date.

**Formulas** (implemented in `BillingCalculator`, unit-tested at the boundaries):
```
financeCharge      = round2( max(0, previousBalance) × financeChargeMonthlyRate / 100 )
gstOnFinanceCharge = round2( financeCharge × gstRate / 100 )
totalAmountDue     = previousBalance + purchases + fees + financeCharge + gstOnFinanceCharge − credits
minimumAmountDue   = 0                                    if totalAmountDue ≤ 0
                   = totalAmountDue                       if totalAmountDue ≤ minDueFloor
                   = min(totalAmountDue, max(minDueFloor, round2(total × minDuePercentage / 100)))   otherwise
lateFee            = 0                                    if unpaidMinimumDue ≤ 0 or totalAmountDue < lateFeeThreshold
                   = min(lateFeeCap, max(lateFeeFloor, round2(unpaidMinimumDue × lateFeePercentage / 100)))
gstOnLateFee       = round2( lateFee × gstRate / 100 ),  late fee posted to the card = lateFee + gstOnLateFee
```
**Assumptions** (not specified in the brief):
* **Billing period.** A BIN closes the period on `billingCycleDay` (1–28, so every month has it). `periodEnd` must be that day, not in the future, after the latest existing statement of the card, and not before the card was opened. `periodStart` = day after the previous statement's end (first statement: one month earlier + 1 day). Due date = `periodEnd + paymentDueDays` (≤ 27 so it precedes the next statement).
* **Eligible entries.** All unbilled entries with `transactionDate ≤ periodEnd` (so late-posted items are not lost). They are marked with the statement id.
* **Cumulative statements.** `previousBalance` is the outstanding amount (total − payments) of the previous statement at generation time, so finance charge applies to what is still unpaid (compounding on earlier charges). A credit balance (negative total) is carried forward and earns no finance charge.
* **Payments** apply only to the card's **latest** statement, must not exceed its outstanding amount (no overpayment/credit handling), and are completed immediately (simulated).
* **Late fee** is assessed on request by an administrator (no scheduler) once `today > dueDate` and the net payments completed **on or before** the due date (reversed payments excluded) are below the minimum due. It uses the **statement's rule snapshot**, not the current BIN rules. No waiver endpoint; a waiver would be a new offsetting ledger row (not implemented).
* **Reversal policy.** Administrator only, once per payment, only while its statement is still the latest; creates a negative allocation row and restores the balance. Late fees already assessed are not reversed automatically.
* **Credit limit** check applies to purchases: `amount ≤ limit − max(0, latestStatementOutstanding + unbilledNet)`. Blocked cards accept fees/refunds, not purchases; closed cards accept nothing; a card can be closed only with zero outstanding and unbilled balance.
* **BIN rules** can change at any time (`ruleVersion` increments only when a value changes); existing statements are never recalculated. The BIN code is immutable.

## 13. Tests
```bash
mvn test            # unit + integration tests (JUnit 5, Mockito, Spring Boot Test on H2)
mvn -Dtest=BillingCalculatorTest test
mvn verify          # tests + packaging
```
Test inventory (148 `@Test` methods):
* `billing` (33): formulas and boundaries, rule validation, Luhn/masking/hash, card transitions (pure Java).
* `service` + `config` (79): Mockito tests for payments (partial/full, idempotent replay, key reuse, ownership, supersession, reversal), statements (first/second statement figures, duplicates, ordering, rule snapshot), late fees, billing entries, cards, BIN configuration, customers/administrators, bootstrap.
* `integration` (36): `ApiSecurityIntegrationTest` (401/403/CSRF, the real browser-style CSRF token flow, role separation, generic login failures, logout, disabled users, service-layer `@PreAuthorize`), `StaticFrontendIntegrationTest` (UI is public, API stays protected, CSP header), `PersistenceIntegrationTest` (Flyway, unique/FK/check constraints, queries, immutability), `BillingWorkflowIntegrationTest` (full lifecycle through MockMvc with exact expected amounts).

All tests run offline on in-memory H2 with a controllable clock; no payment gateway or external service is needed. **H2 is not MySQL**: collation/case-sensitivity, lock timeouts and trigger support differ; the MySQL append-only triggers are therefore not exercised by the suite.

## 14. Build the executable JAR
```bash
mvn clean package            # add -DskipTests to skip tests
java -jar target/credit-card-billing-system-1.0.0.jar
SPRING_PROFILES_ACTIVE=mysql java -jar target/credit-card-billing-system-1.0.0.jar
```
PowerShell: `$env:SPRING_PROFILES_ACTIVE="mysql"; java -jar target\credit-card-billing-system-1.0.0.jar`

## 15. Troubleshooting
| Symptom | Fix |
|---|---|
| `release version 17 not supported` / `UnsupportedClassVersionError` | Use JDK 17+ (`JAVA_HOME`). |
| `Port 8080 was already in use` | `SERVER_PORT=8081` (PowerShell: `$env:SERVER_PORT=8081`). |
| `Access denied for user` / `Communications link failure` (MySQL) | Check MySQL is running, `DB_USERNAME`/`DB_PASSWORD`, and that the env vars are exported in the same shell. |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` in `DB_URL` for local MySQL 8 (or configure TLS). |
| `Migration checksum mismatch` | An applied migration was edited. Restore it and add a new `V3__…` file; for a throw-away dev DB drop and recreate the schema. |
| `Schema-validation: wrong column type / missing column` | Entity and migration drifted; fix the migration (new version) or the mapping. |
| MySQL error 1419 creating triggers | Grant `SUPER` to the migration user or `SET GLOBAL log_bin_trust_function_creators = 1;`, or skip with `FLYWAY_LOCATIONS=classpath:db/migration`. |
| `403 FORBIDDEN` on POST/PUT/PATCH | Missing/old CSRF token or session cookie: call `GET /api/auth/csrf` with the same cookie jar and send `X-CSRF-TOKEN`. |
| `401` after some time | Session expired (30 min) – log in again (the UI returns you to the sign-in page). |
| Blank page or unstyled UI | Hard-refresh (Cmd/Ctrl+Shift+R) and check the browser console. The CSP blocks inline scripts/styles by design; do not add them. |
| UI changes not visible after editing `static/` files | Restart `mvn spring-boot:run` (or rebuild), then hard-refresh. |
| `Initial administrator … password must be at least 12 characters` | Use a longer `BOOTSTRAP_ADMIN_PASSWORD`. |
| H2 admin password not shown | It is printed once at first start only; set `BOOTSTRAP_ADMIN_*` explicitly. |
| `409 CONFLICT` with `CONCURRENT_MODIFICATION` | Two writers collided; retry the request (payments are safe to retry with the same `Idempotency-Key`). |

## 16. Push to GitHub
Create an empty repository on GitHub yourself, copy its URL and replace the placeholder. Nothing in this project pushes or uses your credentials.
```bash
git init
git add .
git commit -m "Initial commit: Credit Card Billing and Statement Management System"
git branch -M main
git remote add origin <YOUR_GITHUB_REPOSITORY_URL>
git push -u origin main
```
`.gitignore` already excludes `target/`, IDE files, `.env*` (except `.env.example`), local `data/`/`*.mv.db` files and logs. No LICENSE file is included; add one of your choice before publishing if you want others to reuse the code.

## 17. Design notes and limitations
* **Frontend**: static files under `src/main/resources/static` (`index.html`, `css/app.css`, `js/*.js`), served by Spring Boot and permitted without login in `SecurityConfig`; everything under `/api` stays protected. There is no bundler: ES modules load directly in the browser. Because the UI is same-origin, CORS stays disabled.
* **Layers**: controllers (thin, DTOs only) → services (rules, transactions, authorization, audit) → repositories → entities. Constructor injection everywhere; entities are never serialized.
* **Concurrency**: card rows are locked (`SELECT … FOR UPDATE`) for entry posting and statement generation; statement rows for payments, reversals and late fees; unique constraints back every duplicate-protection rule (statement per card/period, idempotency key per customer, late fee per statement/event, entry reference per card). Optimistic `@Version` columns guard ordinary updates.
* **Transactions**: payment row + allocation + statement balance + audit row commit or roll back together (audit writes join the caller's transaction).
* **Append-only**: audit log, late-fee ledger and payment allocations have no update/delete methods in their repositories, are `@Immutable` in Hibernate and (MySQL) protected by triggers and optional grants. Limitations: on H2 there are no triggers; `JpaSpecificationExecutor` still declares `delete(Specification)` (never called); a database administrator can always alter data.
* **Statement snapshot columns** are written once (`updatable=false`) but the database itself does not forbid updates to them.
* **Known simplifications**: manual late-fee assessment, no overpayment/credit refunds, single currency, no multi-statement allocation, `Idempotency-Key` required only for payments, no rate limiting or account lockout, HTTP only unless you add TLS in front.

## Verification status
| Check | Result |
|---|---|
| Backend: `mvn clean test` on the 143 backend tests (run by the project owner on JDK 21 / Maven 3.10) | 142 passed; the one failure was a quoted-identifier bug in a test query (`flyway_schema_history`), fixed in this version |
| Backend: application start-up with the H2 profile (Flyway, Hibernate `validate`, Spring Security, Tomcat) | Started successfully on the owner's machine (log reviewed) |
| Backend changes made **after** that run (static-resource rules + CSP in `SecurityConfig`, 5 new tests) | **Not run yet.** They compile at syntax level only; run `mvn clean test` |
| Frontend: 7 JavaScript modules loaded in a simulated browser (jsdom, Node 22) against an in-memory fake of the API; **30 behavioural checks** (sign-in/registration, every customer and administrator page, statement and payment dialogs, `Idempotency-Key`, CSRF refresh/retry, role redirects, error display, expired session, no HTML injection) | **30 / 30 passed** after fixing two real bugs it found (a wrong import and a use-before-define) |
| Frontend in a real browser against the real server | **Not run.** Do the first-run walkthrough in [Web UI](#web-ui) and report anything odd |
| MySQL profile and the `V100` triggers | Not run / untested |

The simulated-browser test lives outside the project (it needs Node), so the ZIP stays Java/Maven only.
