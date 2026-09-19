# ledger-sync

Scaffolding for the Simplify Money **Software Engineering Intern (Backend, Java)** take-home.

Read this file completely before you write any code. Then read
`fixtures/corpus-a.jsonl` — not all 500 lines, but enough of them that you stop
being surprised.

> **Do not open a pull request here.** Work in your own fork and submit by email.
> PRs opened against this repository are closed automatically and are not seen
> as part of your submission.

---

## What this service is for

Simplify Money tells a user where their money went. To do that, something has to
read the bank SMS and bank emails sitting on their phone and turn them into a
ledger the user can trust.

This repository is that something, half-finished, with a live incident open
against it.

---

## What you are being asked to do, exactly

**Input:** `fixtures/corpus-a.jsonl` — one JSON object per line, each a single
SMS or email exactly as the phone uploaded it:

```json
{"message_id":"m-00004-9c11ae","channel":"sms","sender":"AD-HDFCBK-S",
 "received_at":"2026-07-04T07:19:00+05:30","device_id":"dev-3f1a90c47b21",
 "body":"Rs.5 debited from a/c **4821 on 04-07-26 at 07:19 to UPI/WATER CAN. Avl Bal: Rs.92,213.10. Not you? Call 18002586161"}
```

**Output:** three JSON files, written by `report <dir>`.

### 1. `ledger.json` — one entry per real transaction

```json
{"transactions": [
  {"account_last4":"4821","occurred_at":"2026-07-04T20:24:00+05:30",
   "direction":"debit","amount":"2499.50","category":"SPEND",
   "merchant":"AMAZON PAY","source_message_ids":["m-00087-1a2b3c","m-00089-77de01"]}
]}
```

`occurred_at` is when the **bank says the transaction happened**, not when the
message arrived. `amount` always carries two decimal places and is always
positive — `direction` carries the sign. `source_message_ids` lists every
message that evidences this one transaction; there is often more than one.

### 2. `summary.json` — per-account totals

```json
{"accounts": {
  "4821": {"spend":"87068.38","income":"101340.83",
           "micro_count":52,"micro_total":"2357.51",
           "transferred_out":"25000.00","transferred_in":"6000.00"}
}}
```

### 3. `reconciliation.json` — anything your ledger cannot account for

```json
{"discrepancies": [
  {"account_last4":"4821","occurred_at":"...","amount":"...","note":"..."}
]}
```

We are not telling you how to find these, or whether there are any. Working out
what "cannot account for" means here, and what in the data lets you check it, is
part of the task.

---

## The four categories

Every transaction gets exactly one.

| Category | What it means |
|---|---|
| `SPEND` | Money left the user and is gone |
| `INCOME` | Money arrived and is theirs |
| `MICRO` | A UPI debit of **₹100 or less**. Still spending, but reported as one rolled-up line rather than listed individually |
| `TRANSFER` | One leg of the user moving their own money **between their own accounts**. Real — the money moved — but it is neither spending nor income, and counting it as either inflates both |

`micro_total` is the sum of `MICRO`. `spend` is the sum of `SPEND` and does
**not** include `MICRO` or `TRANSFER`. `income` likewise excludes `TRANSFER`.

---

## Your checkpoint

`fixtures/corpus-a-totals.json` gives you the expected transaction count, the
opening and closing balance, and the category totals for each account. No
row-level answers. Use it to check yourself.

If your numbers do not match it, **say so and say why.** A submission whose
numbers match because they were made to match is worse than one that does not
match and explains itself. We can tell the difference, and we check.

---

## Where the code is now

```
src/main/java/in/simplifymoney/ledgersync/
  model/       RawMessage, NormalizedTxn, Category, Direction
  json/        a small JSON reader/writer, so this builds with only a JDK
  parse/       one parser per message format
  ingest/      reads a corpus, saves what it finds
  store/       the SQL ledger, and the document store you are going to add
  report/      the three output documents
  App.java     migrate | ingest | report
  SelfCheck.java
```

Run it:

```bash
./verify.sh                      # compile + run the pipeline, no network needed
./gradlew test                   # the test suite (needs network once, for JUnit)
./gradlew run --args="migrate"
./gradlew run --args="ingest fixtures/corpus-a.jsonl"
./gradlew run --args="report submission/"
```

`./verify.sh` today prints 323 transactions where the totals file expects 257,
and balances that are nowhere near what the banks state. That is the starting
point, not a bug you have hit.

---

## What is missing, in the order we would do it

1. **`EmailParser` is a stub.** Every email in the corpus is currently dropped.
2. **`IciciSmsParser` reads one of the ICICI formats.** There is at least one
   more in the corpus, falling straight through.
3. **Nothing deduplicates.** `IngestService` saves one transaction per message.
   One transaction is not one message.
4. **Categories are decided from the direction alone.** No `MICRO`, no
   `TRANSFER`.
5. **`Reports.summary` adds up whatever it is given.** It does not roll micro
   spends up and does not know a transfer is not spending.
6. **`Reports.reconciliation` is not written.**
7. **`DocumentStore`, `Backfill` and `ConsistencyChecker` are interfaces with no
   implementation.** See below.
8. **`incident/INC-2026-09-11.md` is open.** Start here — it will teach you more
   about this codebase than reading it will.

---

## The document store

The ledger is moving off SQL onto a document store. **DynamoDB preferred,
MongoDB fine** — your choice, and say why. It must run from your
`docker compose up`.

`DocumentStore` declares the only three queries this service makes:

1. one account's transactions for one month, newest first
2. running totals per category for an account
3. given a message id, which transaction did it produce

Design your documents so the engine serves these directly. We are not going to
tell you what a document should look like — that decision is the exercise.

For each of the three, **report how many items the engine examined versus how
many it returned, at 100,000 transactions.** DynamoDB gives you `ScannedCount`
and `Count`; MongoDB gives you `totalDocsExamined` and `nReturned`. Put the six
numbers in your README.

Then:

- **`Backfill`** moves what is already in SQL across. Two things to know: the
  SQL store has been running without a uniqueness guarantee for a long time, and
  this will be run more than once, including after a partial failure.
- **`ConsistencyChecker`** proves the two stores agree and names precisely where
  they do not. We will run yours against a document store we have deliberately
  altered. It has to find what we changed. A checker that compares row counts
  will not.

---

## Rules

- `model/NormalizedTxn.java`, `model/Category.java` and
  `src/test/.../NormalizedTxnContractTest.java` are **frozen**. Do not edit
  them. Everything behind them is yours.
- Java. Any framework, or none — say why in your decision log.
- Real commit history. Not one squashed commit.
- If something in here is wrong or unclear, **email us**. Guessing when you
  could have asked is a worse signal than asking.

`talent.acquisition@simplifymoney.in`

---

## Implementation notes

### Run locally

The documented verification walkthrough completes in under five minutes once the
Mongo image and Gradle dependencies have been downloaded locally. First-time
downloads depend on the local network connection. The design decisions behind
the implementation are recorded in [decision_log.md](decision_log.md).

The dependency-free smoke check remains available:

```bash
./verify.sh
```

For the full SQL and MongoDB-backed build, use Java 21 and Gradle:

```bash
docker compose up -d mongo
gradle test
gradle run --args="migrate"
gradle run --args="ingest fixtures/corpus-a.jsonl"
gradle run --args="report submission/"
gradle run --args="backfill"
gradle run --args="check"
```

`verify.sh` intentionally compiles the parser/ledger smoke path without network dependencies. Gradle compiles the MongoDB adapter using the declared MongoDB driver.

### Pipeline and evidence identity

`HdfcSmsParser`, `IciciSmsParser` and `EmailParser` capture an amount from the transaction clause itself. They never use a later available balance or card limit as the transaction amount. Both decimal and whole-rupee amounts are normalized to exactly two decimal places.

`IngestService` groups parsed evidence by account, bank timestamp, direction, amount and normalized merchant. An SMS and email describing the same event become one `NormalizedTxn` with every upload ID in `source_message_ids`. Re-running a corpus or reprocessing overlapping evidence produces the same canonical ledger snapshot.

### Categories and reports

- `MICRO`: UPI debit up to and including ₹100.
- `TRANSFER`: matching debit and credit on different known accounts, with equal amount, normalized merchant and a five-minute bank-time window.
- `SPEND` and `INCOME`: all other debits and credits.

`summary.json` keeps MICRO out of `spend`, keeps TRANSFER out of spend/income, and reports transfer inflow/outflow separately. `reconciliation.json` never fabricates a transaction: when the report receives the corpus, it uses parsed stated-balance evidence to report only unexplained balance movement. Credit-card available limits are not treated as bank-balance evidence.

### Incident

The root cause, blast-radius rule and five-line operational response are recorded in [`incident/INC-2026-09-11-response.md`](incident/INC-2026-09-11-response.md). The regression test is `IncidentRegressionTest`.

### Document model

MongoDB is used because its document shape directly represents one canonical transaction with many evidence IDs:

```text
_id = deterministic financial-event key
account_last4, month, occurred_at, direction, amount, category, merchant
source_message_ids: [all SMS/email uploads that evidence the event]
```

Indexes serve the only access patterns directly:

1. `(account_last4, month, occurred_at desc)` — account month, newest first.
2. `(account_last4, category)` — category-total aggregation scope.
3. `source_message_ids` — message-to-transaction lookup.

`MongoDocumentStore` uses upsert plus `$addToSet`, so retrying a save adds evidence but cannot duplicate a financial event. `Backfill` deduplicates legacy SQL rows by the same key and may be rerun after partial failure. `ConsistencyChecker` compares full canonical content, not just counts.

### Query measurements at 100k

Measured locally on MongoDB 8.0.32 using an isolated `ledger_benchmark.transactions` collection containing exactly 100,000 synthetic canonical transaction documents. The benchmark used the same three indexes created by `MongoDocumentStore`; figures are MongoDB `executionStats.totalDocsExamined` and `nReturned`, not estimates.

| Query | Examined | Returned |
|---|---:|---:|
| Q1 account/month newest first | 84 | 84 |
| Q2 account category totals | 1,000 | 4 |
| Q3 message ID lookup | 1 | 1 |

Q1 used account `A000` and month `2026-01` (84 matching documents). Q2 aggregated all 1,000 documents for that account into four category totals. Q3 looked up one evidence message ID. The results demonstrate bounded index-backed access; they are benchmark inputs, not a claim about every possible production account distribution.

### Known limitations / remaining verification

- The seed migration intentionally inserts legacy rows. Corpus-only totals should be checked with `SelfCheck`; a SQL database after `migrate` also contains that historical seed data for backfill testing.
- The corpus has 256 evidence-backed transactions while `corpus-a-totals.json` says 257. The ledger reports the actual unexplained ₹7,500.00 movement on account `4821`; it deliberately does not invent a 257th transaction.
- The checked-in `submission/` JSON files are corpus-only artifacts. The SQL `App` workflow is retained for migration/backfill testing and includes V2 legacy seed rows, so it must not be used as the Task 2 corpus submission result.
