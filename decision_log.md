# Decision log

1. **Canonical transaction identity uses financial facts, not `message_id`.** The corpus contains SMS/email pairs for one event, while the assignment explicitly says upload IDs are evidence IDs.
2. **Evidence is merged only on account, bank event timestamp, direction, amount and normalized merchant.** This is conservative, deterministic and works without inventing an unavailable bank transaction ID.
3. **Amounts are captured from the parser's transaction clause.** A bank alert can contain an available balance or credit limit, so scanning every rupee figure is unsafe.
4. **Whole rupees are normalized to two decimals.** Real fixture messages contain both `Rs.5` and `INR 18,000`.
5. **Email alerts are independent evidence, not a separate spend.** Their time, account, direction and merchant can match an SMS exactly.
6. **MICRO is based on a UPI debit of ₹100 or less.** It remains in `ledger.json`, but is separated only in the summary.
7. **TRANSFER requires a matched opposite-direction event on another known account within five minutes with the same amount and normalized merchant.** This avoids treating every IMPS/NEFT/P2P transaction as internal.
8. **The SQL ingestion path replaces its canonical snapshot.** The legacy SQL table has no uniqueness guarantee; this gives repeat corpus ingestion stable results while preserving combined evidence.
9. **Mongo documents use a financial-event `_id` plus an evidence-ID array.** Upsert + `$addToSet` makes retries and partial backfills safe.
10. **Consistency is checked transaction-by-transaction, including evidence IDs.** Matching counts cannot detect an altered amount, category, merchant or source list.

## AI disclosure

AI assistance was used to inspect message patterns, propose parser tests and explain implementation trade-offs. All parser behavior, fixture counts and verification outputs were checked against the repository.

**Concrete correction:** an AI-generated validation workflow used `App migrate`, then `App ingest`, then `App report build/submission`. That was rejected for the Task 2 deliverables because `migrate` applies `V2__seed.sql`; the saved result therefore contained 266 rows, mixing legacy seed data with corpus data. The final submission generation instead runs `IngestService` against `fixtures/corpus-a.jsonl` with `InMemoryLedgerStore` and passes the same raw corpus to `Reports.reconciliation`. It produces 256 evidence-backed transactions and the real ₹7,500.00 discrepancy. The behavioral difference is corpus-only evidence versus seeded SQL history. This is demonstrated by `db/migration/V2__seed.sql`, `App.java`, `IngestService.java`, and the checked-in `submission/` JSON files.
