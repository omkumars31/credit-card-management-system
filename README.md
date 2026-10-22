# Credit Card Billing & Statement Management System

A portfolio project that models a credit-card management and billing backend using Java, Spring Boot, Spring Data JPA/Hibernate, Spring Security, Flyway, MySQL, and H2.

It covers customer and administrator accounts, credit-card lifecycle management, configurable BIN-level billing rules, statement generation, simulated payments, late-fee accounting, and audit trails.

> **Project scope:** This is an educational portfolio project. Card data is synthetic; no real card network or payment gateway is connected. It is not production-certified, externally audited, or PCI DSS compliant.

## Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Run with H2](#run-with-h2)
- [Run with MySQL](#run-with-mysql)
- [Database Migrations](#database-migrations)
- [Authentication](#authentication)
- [API Overview](#api-overview)
- [Billing Rules](#billing-rules)
- [Testing](#testing)
- [Build the JAR](#build-the-jar)
- [Troubleshooting](#troubleshooting)
- [Project Design](#project-design)
- [Known Limitations](#known-limitations)
- [Verification Status](#verification-status)

## Features

- **Customer accounts:** registration, profile management, and customer-scoped access to cards, statements, payments, and late fees.
- **Administrator operations:** manage administrators, customers, BIN billing configurations, cards, billing entries, statements, late fees, payment reversals, and audit records.
- **Card lifecycle:** `ACTIVE`, `BLOCKED`, and `CLOSED` states; closed cards cannot be reopened. Only masked card numbers are stored or returned, with a SHA-256 hash used for uniqueness.
- **BIN-level rules:** configurable billing-cycle day, payment due days, minimum-due calculation, finance-charge rate, GST, and late-fee rules. Rule changes are versioned.
- **Statement generation:** purchases, fees, refunds, finance charges, GST, minimum due, and outstanding balance calculations. Statements retain a snapshot of the rules used.
- **Simulated payments:** partial and full payments, idempotency-key handling, payment tracking, and administrator-controlled reversals.
- **Late-fee ledger:** append-only records with rule snapshots.
- **Audit trail:** records business and administrative actions without storing passwords, session IDs, or full card numbers.
- **Security:** Spring Security session authentication, BCrypt-based password encoding, CSRF protection, role-based authorization, session-fixation protection, and JSON authentication/authorization errors.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 17+ | Application language (compiled with `release 17`) |
| Spring Boot 3.3.5 | Application framework |
| Spring Web | REST API layer |
| Spring Data JPA / Hibernate | Persistence and ORM |
| Spring Security | Authentication and authorization |
| Bean Validation | Request validation |
| Flyway | Versioned database migrations |
| H2 | In-memory development and test database |
| MySQL 8.0+ | Persistent database profile |
| Maven | Build and dependency management |
| JUnit 5, Mockito, Spring Boot Test | Automated testing |

The project uses **session-based authentication**, not JWT. It does not currently include Swagger/OpenAPI, a React frontend, Docker, Lombok, MapStruct, or Testcontainers.

## Prerequisites

- JDK 17 or newer
- Maven 3.6.3 or newer (Maven 3.9.x recommended)
- Optional: MySQL 8.x for persistent local storage
- Optional: VS Code with the Extension Pack for Java, or another Java IDE

Verify your installation:

```bash
java -version
mvn -version
```

## Run with H2

H2 is the default profile, so MySQL is not required for the basic local run.

```bash
mvn spring-boot:run
```

The application uses an in-memory H2 database in MySQL compatibility mode. Data is lost when the application stops; Flyway reapplies migrations and seed data on startup.

The H2 web console is disabled.

### Optional: persist H2 data locally

macOS/Linux:

```bash
H2_URL="jdbc:h2:file:./data/ccbilling;MODE=MySQL" mvn spring-boot:run
```

PowerShell:

```powershell
$env:H2_URL="jdbc:h2:file:./data/ccbilling;MODE=MySQL"
mvn spring-boot:run
```

## Run with MySQL

1. Start MySQL and create a database plus a dedicated local user:

```sql
CREATE DATABASE ccbilling CHARACTER SET utf8mb4;
CREATE USER 'ccbilling'@'localhost' IDENTIFIED BY '<your-local-password>';
GRANT ALL PRIVILEGES ON ccbilling.* TO 'ccbilling'@'localhost';
```

2. Set the environment variables in the same terminal or IDE run configuration. Spring Boot does **not** automatically load a `.env` file.

macOS/Linux:

```bash
export SPRING_PROFILES_ACTIVE=mysql
export DB_USERNAME=ccbilling
export DB_PASSWORD='<your-local-password>'
export BOOTSTRAP_ADMIN_USERNAME=admin
export BOOTSTRAP_ADMIN_PASSWORD='<a-long-passphrase-of-at-least-12-characters>'
mvn spring-boot:run
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:DB_USERNAME="ccbilling"
$env:DB_PASSWORD="<your-local-password>"
$env:BOOTSTRAP_ADMIN_USERNAME="admin"
$env:BOOTSTRAP_ADMIN_PASSWORD="<a-long-passphrase-of-at-least-12-characters>"
mvn spring-boot:run
```

`DB_URL` can optionally override the default JDBC URL. Development-only connection flags must not be copied into a real deployment without reviewing TLS and database security settings.

For MySQL, Flyway also applies the MySQL-specific append-only triggers migration under `db/migration-mysql`. This requires the appropriate database privileges. To use only the standard migration location, configure `FLYWAY_LOCATIONS=classpath:db/migration`.

## Database Migrations

Flyway owns the database schema. Hibernate uses `ddl-auto=validate`, which validates entity mappings against the schema instead of generating or modifying tables.

Migration locations:

- `src/main/resources/db/migration/V1__initial_schema.sql` — tables, constraints, and indexes.
- `src/main/resources/db/migration/V2__seed_bin_configurations.sql` — synthetic BIN configurations.
- `src/main/resources/db/migration-mysql/V100__append_only_triggers.sql` — MySQL-only triggers for append-only tables.

Flyway records applied migrations in `flyway_schema_history`.

**Important:** Do not edit a migration that has already been applied in a shared or persistent database. Add a new versioned migration instead.

## Initial Administrator

There is no universal default password.

For the MySQL profile, set these environment variables to bootstrap an administrator:

| Variable | Purpose |
|---|---|
| `BOOTSTRAP_ADMIN_USERNAME` | Login username |
| `BOOTSTRAP_ADMIN_PASSWORD` | Password of at least 12 characters |
| `BOOTSTRAP_ADMIN_FULL_NAME` | Optional display name |

The application creates the administrator only if one does not already exist. For local H2 development, if the variables are omitted and no administrator exists, the application can generate a random password and print it once in the startup console. Set explicit values when you need repeatable local credentials.

## Start the Application

Default URL: `http://localhost:8080`

Health endpoint: `GET http://localhost:8080/api/health`

There is no frontend included. Customers can register through `POST /api/auth/register`.

## Authentication

Authentication is **session-based** using a `JSESSIONID` cookie; it is not JWT-based.

- Customer login identifier: email.
- Administrator login identifier: username.
- `GET /api/auth/csrf` provides the CSRF token.
- State-changing requests (`POST`, `PUT`, `PATCH`, and `DELETE`) require the CSRF token in the `X-CSRF-TOKEN` header and the corresponding session cookie.
- Admin endpoints require the admin role; customer endpoints are scoped to the authenticated customer.
- Failed logins return a generic credentials error.
- `POST /api/auth/logout` ends the session.
- CORS is not enabled by default.

### Example using curl

The following is a basic workflow outline; preserve the same cookie jar between calls and retrieve a current CSRF token before state-changing requests.

```bash
BASE=http://localhost:8080
JAR=$(mktemp)

curl -s -b "$JAR" -c "$JAR" "$BASE/api/auth/csrf"
```

Use the returned token in `X-CSRF-TOKEN` and keep the cookie jar for subsequent requests. See the application security configuration for the exact request and response payloads.

## API Overview

Errors use a consistent JSON structure containing timestamp, status, code, message, path, and optional field errors. Lists are paginated using parameters such as `page`, `size`, and `sort`. Money values are represented in INR with two decimal places.

### Public endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Health status |
| `GET` | `/api/auth/csrf` | Obtain a CSRF token |
| `POST` | `/api/auth/register` | Register a customer |
| `POST` | `/api/auth/login` | Log in |
| `POST` | `/api/auth/logout` | Log out |
| `GET` | `/api/auth/me` | Get the current authenticated user |

### Customer endpoints

Base path: `/api/customers/me`

| Method | Endpoint | Description |
|---|---|---|
| `GET`, `PUT` | `/` | View or update profile |
| `GET` | `/cards`, `/cards/{id}` | List or view own cards |
| `GET` | `/cards/{id}/balance` | View card balance/exposure |
| `GET` | `/cards/{id}/entries` | List card transactions |
| `GET` | `/statements` and `/statements/{id}` | View own statements |
| `POST` | `/payments` | Pay a statement (requires `Idempotency-Key`) |
| `GET` | `/payments`, `/payments/{id}` | View own payments |
| `GET`, `POST` | `/payment-methods` | List or add simulated payment methods |
| `DELETE` | `/payment-methods/{id}` | Deactivate a payment method |
| `GET` | `/late-fees` | View own late-fee records |

### Administrator endpoints

Base path: `/api/admin`

| Method | Endpoint | Description |
|---|---|---|
| `GET`, `POST` | `/administrators` | List or create administrators |
| `PATCH` | `/administrators/{id}/enabled` | Enable/disable an administrator |
| `GET` | `/customers` | Search/list customers |
| `GET` | `/customers/{id}` | View a customer |
| `PATCH` | `/customers/{id}/status` | Change customer status |
| `GET`, `POST` | `/bin-configurations` | List or create BIN rules |
| `GET`, `PUT` | `/bin-configurations/{id}` | View or update BIN rules |
| `POST`, `GET` | `/cards` | Issue cards or list cards |
| `GET` | `/cards/{id}`, `/cards/{id}/balance` | View card or balance |
| `PATCH` | `/cards/{id}/status` | Change card status |
| `PUT` | `/cards/{id}/credit-limit` | Update credit limit |
| `POST`, `GET` | `/cards/{id}/entries` | Post or list billing entries |
| `POST` | `/statements/generate` | Generate a statement |
| `GET` | `/statements`, `/statements/{id}` | List or view statements |
| `POST` | `/statements/{id}/late-fee` | Assess a late fee |
| `GET` | `/late-fees` | Search late-fee records |
| `GET` | `/payments` | Search payments |
| `POST` | `/payments/{id}/reverse` | Reverse a payment |
| `GET` | `/audit-logs` | Search audit records |

Typical status codes include `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, and `422`.

## Billing Rules

All monetary values use `BigDecimal`, are represented in INR, and are rounded to two decimal places using `HALF_UP`. Percentage values support up to four decimal places. The current business date is based on UTC.

Core formulas:

```text
financeCharge      = round2(max(0, previousBalance) * monthlyRate / 100)
gstOnFinanceCharge = round2(financeCharge * gstRate / 100)
totalAmountDue     = previousBalance + purchases + fees
                     + financeCharge + gstOnFinanceCharge - credits
```

Minimum due:

```text
0                                  when totalAmountDue <= 0
totalAmountDue                     when totalAmountDue <= minDueFloor
min(totalAmountDue,
    max(minDueFloor,
        round2(totalAmountDue * minDuePercentage / 100))) otherwise
```

Late fee:

```text
0 when unpaid minimum due <= 0 or total amount due < late-fee threshold
otherwise min(cap, max(floor, round2(unpaid minimum due * late-fee percentage / 100)))
```

GST is added to the late fee. The late-fee ledger stores the assessed rule snapshot.

These formulas represent the project's documented assumptions and are intended for learning; actual issuer rules vary by jurisdiction and product.

## Testing

Run the complete test suite:

```bash
mvn test
```

Run the billing calculator tests:

```bash
mvn -Dtest=BillingCalculatorTest test
```

Package and verify:

```bash
mvn verify
```

The test suite is designed to cover billing calculations, rule validation, card-number masking/hashing, payment idempotency and reversal, statement generation, late fees, security controls, persistence constraints, and end-to-end workflows.

H2 is not identical to MySQL. MySQL-specific behavior such as triggers, collation, and locking should be tested against a real MySQL instance before relying on it.

## Build the JAR

```bash
mvn clean package
java -jar target/credit-card-billing-system-1.0.0.jar
```

For the MySQL profile:

```bash
SPRING_PROFILES_ACTIVE=mysql java -jar target/credit-card-billing-system-1.0.0.jar
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
java -jar target\credit-card-billing-system-1.0.0.jar
```

Confirm the actual JAR name under `target/` if your Maven project version differs.

## Troubleshooting

| Problem | Suggested check |
|---|---|
| `release version 17 not supported` | Use JDK 17+ and verify `JAVA_HOME`. |
| Port 8080 is occupied | Set `SERVER_PORT=8081` or stop the other process. |
| MySQL access denied | Verify MySQL is running and credentials are exported in the same shell. |
| Migration checksum mismatch | Do not edit an applied migration; restore it and create a new versioned migration. |
| Schema validation failure | Compare entity mappings with the Flyway-created schema. |
| `403` on a state-changing request | Refresh the CSRF token and send it with the same session cookie. |
| `401` after inactivity | The session may have expired; log in again. |
| Bootstrap admin password rejected | Use a password of at least 12 characters. |
| Concurrent modification conflict | Retry only where appropriate; reuse the same payment `Idempotency-Key` for a payment retry. |

## Project Design

The backend follows a layered architecture:

```text
HTTP Request
     |
     v
Controller (HTTP + DTOs)
     |
     v
Service (business rules, transactions, authorization)
     |
     v
Repository (persistence)
     |
     v
Database (H2 / MySQL)
```

Key design decisions:

- Controllers remain thin and exchange DTOs rather than serializing JPA entities.
- Services enforce business rules and transaction boundaries.
- Constructor injection is used for dependencies.
- Flyway is the schema source of truth; Hibernate validates mappings.
- Database constraints support application-level validation.
- Payments, allocations, statement balance changes, and audit records are committed within a transaction.
- Idempotency keys and unique constraints prevent duplicate processing.
- Optimistic locking and row locks protect sensitive concurrent updates.
- Audit logs, late-fee ledger records, and payment allocations are designed to be append-only.

## Known Limitations

- No real payment gateway or card-network integration.
- No frontend is included.
- Authentication is session-based, not JWT.
- Late-fee assessment is administrator-triggered; no scheduler is included.
- No overpayment/credit-refund handling or multi-statement payment allocation.
- Single-currency INR assumptions.
- No built-in rate limiting or account lockout.
- HTTPS/TLS must be provided by the deployment environment.
- This project is not production-certified and makes no PCI DSS compliance claim.

## Verification Status

The project documentation reports the following verification status for the package this README describes:

- Selected pure-Java billing utilities compiled with `javac` using release 17, and 33 associated JUnit tests passed.
- Flyway SQL statements were exercised statement-by-statement against H2 in MySQL compatibility mode, with selected constraints checked.
- A syntax pass over the Java source files found no syntax errors.
- **The full Maven compile/test suite, Spring context startup, remaining framework-dependent tests, MySQL profile/triggers, and live REST calls were not run in that environment.**

Run `mvn test` and test the MySQL profile locally before treating the project as verified.

**Built as a learning project to explore backend engineering concepts used in financial applications.**
