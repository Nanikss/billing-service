# Billing Service

[![CI](https://github.com/Nanikss/billing-service/actions/workflows/ci.yml/badge.svg)](https://github.com/Nanikss/billing-service/actions/workflows/ci.yml)

A Spring Boot REST API for insurance **policy billing**: payment plans, installment invoices, payment allocation and delinquency. These are the core flows of a billing system like Guidewire BillingCenter, which I've configured and integrated professionally, rebuilt here as a small standalone service.

**Stack:** Java 17 · Spring Boot 3 (Web, Data JPA, Validation) · H2 / Postgres · JUnit 5 · MockMvc · AssertJ

## Features

- **Payment plans** (`FULL_PAY`, `QUARTERLY`, `MONTHLY`): a down payment percentage, then equal installments with a flat installment fee.
- **Exact installment math.** Amounts are in cents, and the integer-division remainder goes onto the down payment, so installments always add up to exactly the premium. This is checked with a parameterized test across plans and odd amounts.
- **Month-end safe due dates.** Each due date is computed from the effective date (Jan 31 → Feb 28 → Mar 31), not chained month to month, so dates don't drift.
- **Payment allocation.** Payments go to the oldest open invoice first (partial payments supported). Overpayments become account credit and are applied automatically to the next policy billed.
- **Delinquency job.** Accounts with an invoice unpaid past the grace period (configurable, default 10 days) become `DELINQUENT`, and the status clears once they catch up.
- **Clean API errors.** Validation and not-found errors come back as RFC 7807 `ProblemDetail` JSON.
- **Optimistic locking** (`@Version`) on accounts, so concurrent payments can't silently overwrite each other.

## API

| Method | Path | Description |
|---|---|---|
| POST | `/api/accounts` | Open a billing account |
| GET | `/api/accounts/{id}` | Account status and credit |
| POST | `/api/accounts/{id}/policies` | Bill a policy term under a payment plan, which creates the invoices |
| GET | `/api/accounts/{id}/invoices` | Invoice schedule with paid and outstanding amounts |
| POST | `/api/accounts/{id}/payments` | Receive a payment (oldest-first allocation) |
| POST | `/api/delinquency/run?asOf=YYYY-MM-DD` | Run the delinquency check |

```bash
curl -X POST localhost:8080/api/accounts -H "Content-Type: application/json" -d '{"accountName":"Acme LLC"}'
curl -X POST localhost:8080/api/accounts/1/policies -H "Content-Type: application/json" \
  -d '{"policyNumber":"POL-1001","premiumCents":1200000,"plan":"QUARTERLY","effectiveDate":"2026-01-01"}'
curl -X POST localhost:8080/api/accounts/1/payments -H "Content-Type: application/json" -d '{"amountCents":450000}'
```

## Run

```bash
mvn test              # 11 tests: unit + full API tests via MockMvc
mvn spring-boot:run   # http://localhost:8080 (in-memory H2)
```

To use Postgres, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.

## Structure

```
domain/      PaymentPlan, InstallmentScheduler (pure), BillingAccount, Invoice (JPA)
repository/  Spring Data JPA repositories
service/     BillingService: billing, allocation, delinquency (transactional)
web/         REST controller, request/response records, ProblemDetail error handling
```

Built with AI coding tools; I designed the billing rules and reviewed and tested the code.
