-- PostgreSQL/Aurora reference schema; conceptual production adapter, not executed by in-memory demo.
-- Use a DB role without SUPERUSER/BYPASSRLS/table ownership. Set app.firm_id only
-- after authenticating firm membership, with SET LOCAL in a transaction.
CREATE TABLE engagement_update_state (
  firm_id text NOT NULL,
  engagement_id text NOT NULL,
  template_id text NOT NULL,
  market text NOT NULL,
  applied_version text NOT NULL,
  decided_target_version text,
  decision text NOT NULL CHECK (decision IN ('NONE','DECLINED','APPLIED')),
  source_revision bigint NOT NULL CHECK (source_revision >= 0),
  PRIMARY KEY (firm_id, engagement_id)
);
CREATE INDEX engagement_template_by_firm
  ON engagement_update_state (firm_id, template_id, market);

CREATE TABLE engagement_decision_history (
  firm_id text NOT NULL,
  engagement_id text NOT NULL,
  source_revision bigint NOT NULL,
  event_id text NOT NULL,
  decided_target_version text NOT NULL,
  decision text NOT NULL CHECK (decision IN ('DECLINED','APPLIED')),
  PRIMARY KEY (firm_id, engagement_id, source_revision),
  FOREIGN KEY (firm_id, engagement_id) REFERENCES engagement_update_state (firm_id, engagement_id)
);
CREATE TABLE template_offer (
  template_id text NOT NULL,
  market text NOT NULL,
  target_version text NOT NULL,
  catalog_revision bigint NOT NULL,
  PRIMARY KEY (template_id, market)
);

ALTER TABLE engagement_update_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE engagement_update_state FORCE ROW LEVEL SECURITY;
CREATE POLICY firm_scope_state ON engagement_update_state
  USING (firm_id = current_setting('app.firm_id', true))
  WITH CHECK (firm_id = current_setting('app.firm_id', true));

ALTER TABLE engagement_decision_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE engagement_decision_history FORCE ROW LEVEL SECURITY;
CREATE POLICY firm_scope_history ON engagement_decision_history
  USING (firm_id = current_setting('app.firm_id', true))
  WITH CHECK (firm_id = current_setting('app.firm_id', true));

-- Critical on every pooled connection/transaction:
-- BEGIN;
-- SELECT set_config('app.firm_id', '<authorized firm>', true);
-- SELECT ... FROM engagement_update_state WHERE firm_id = '<authorized firm>';
-- COMMIT;
-- NEVER accept app.firm_id directly from an unvalidated request or allow client SQL.
-- Note: source event revision update + decision-history insert must be atomic in one
-- database transaction, with revision-gated UPSERT using WHERE excluded.source_revision > current.source_revision.
