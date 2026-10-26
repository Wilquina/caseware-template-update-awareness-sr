# Caseware Senior Take-Home | Engagement Update Awareness (Java 21)

This is **one narrow, testable slice** of the proposed architecture: the **regional, firm-scoped update-awareness projection/evaluation**. It is not a full application or a replacement for the existing Engagement Management system. The approach reuses the event-driven derived index, version-decision semantics, and immutable diff baseline from the earlier Staff exercise, but makes firm isolation and decision history first-class.

## Run

- JDK 21 and Maven 3.9+ recommended.
- `mvn test` runs **7 JUnit Jupiter tests**.
- If Maven/dependency access is unavailable, an additional dependency-free smoke test can run with:

```bash
mkdir -p out
javac --release 21 -d out $(find src/main/java -name '*.java')
javac --release 21 -cp out -d out src/test/java/com/wq/caseware/senior/OfflineVerification.java
java -cp out com.wq.caseware.senior.OfflineVerification
```

## Boundaries / implementation choices

- `Domain` holds immutable Java records for **authoritative metadata snapshots**, `TemplateOffer`, `Principal`, `PendingUpdate`, and `DecisionAudit`; no financial/workpaper content enters this code.
- `Ports` separates domain logic from repositories and authorization, avoiding a framework or cloud dependency for this narrow exercise.
- `UpdateAwarenessService` evaluates pending state from **actual applied version** and the **latest eligible offered target**. Declining v5 on v3 hides v5 only; v7 is a new offer, and the correct summary baseline is `v3 -> v7`.
- `InMemoryAdapters` provide deterministic local testing; **they are not production security isolation**. In production use the reference `db/schema.sql` with RLS/transaction-scoped firm context, regional IAM and scoped authorizations.
- `EngagementStateStore.acceptIfNewer` and `TemplateCatalog.acceptIfNewer` ignore duplicate/out-of-order revisions. Production should implement these with `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE excluded.source_revision > current.source_revision`, and insert history in the **same transaction**.
- The read path looks up metadata and the latest offer; it **never loads a complete engagement**. Publication may trigger regional per-firm evaluation jobs to produce coverage/impact metrics, but does not require expensive per-file reloads.
- Apply remains owned by Engagement Management. Only authoritative post-apply snapshots advance `appliedVersion`; the UI should show `APPLYING` separately if a request is in flight.

## Security / tenancy (must be enforced, not inferred)

- Firm ID is part of every business key and query. The API verifies the caller's firm membership **before** querying data.
- PostgreSQL row-level security (`ENABLE` + `FORCE`) applies to both state and decision history. Runtime DB role has no `BYPASSRLS`, no ownership bypass, and no arbitrary SQL exposure. Set `app.firm_id` using `SET LOCAL` only after server-side authorization. Test a pooled-connection cross-tenant request to ensure context does not leak.
- Firm-scoped databases stay in required data-residency regions. Shared template metadata is not customer engagement data.
- A more isolated variant (separate firm DB/schema/role) may be required for stricter contracts. RLS is a deliberate tradeoff, not a blanket guarantee against privileged credential compromise.

## Production adapters deliberately omitted

EventBridge/SQS bindings, publisher outbox, PostgreSQL adapter, auth/JWT integration, replayable reconciliation job, API HTTP routing, IaC and real LLM calls are described in the design but **not shipped here**: they would hide the correctness-focused slice under infrastructure boilerplate. `db/schema.sql` is an illustrative schema, not a full deployable migration. In production, template eligibility, market branching, withdrawal and user authorization are sourced from established domain systems.

## What the tests cover

1. Newly offered target creates pending status without loading an engagement.
2. Declining v5 does not advance the applied baseline; v7 resurfaces accumulated changes.
3. Accepted/applied version stops pending status.
4. Out-of-order and duplicate engagement/publish events cannot regress the projection or duplicate audit records.
5. Same engagement ID in separate firms is isolated; cross-firm reads are forbidden.
6. Market-specific offers do not leak into other markets.
7. Incomplete/invalid snapshot contract is rejected.

The dependency-free offline test checks the central flow with 16 assertions. Integration tests still needed for RLS, DB transactions, producer outbox, consumer retries, event age, and regional deployment.

## Tradeoffs / failure modes

- **Aurora PostgreSQL vs DynamoDB:** RLS and relational audit/history are easy to demonstrate; DynamoDB remains viable with carefully designed firm-partition keys and fine-grained IAM, but GSI access must never bypass tenant isolation.
- **At-least-once events:** cheaper and simpler than attempting global exactly-once; revisions and idempotency make replay safe. The source's transaction and outbox are required to avoid silent gaps.
- **Read-time evaluation vs per-publish materialization:** fewer writes at the cost of a cheap indexed join. Firm-coverage monitoring can be produced by a periodic/per-publish evaluator.
- **Summary AI:** deterministic JSON diff facts are authoritative; optional LLM rewriting requires validation, fallback, and immutable provenance. No model can decide whether the update applies or was accepted.
- **Scale:** no engagement count or firm count is specified globally in the Senior assignment. Capacity must be measured; do not reuse the Staff exercise's 800k/4k/40-product numbers as if they were given here.
