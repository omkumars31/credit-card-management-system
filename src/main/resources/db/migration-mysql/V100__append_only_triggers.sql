-- MySQL-only hardening: make the audit log, late-fee ledger and payment allocations append-only at the
-- database level. Requires the TRIGGER privilege (and, when binary logging is enabled, either SUPER or
-- log_bin_trust_function_creators=1). To skip this migration set FLYWAY_LOCATIONS=classpath:db/migration
-- Not applied on H2 (see README, "Append-only enforcement").

CREATE TRIGGER trg_audit_logs_no_update BEFORE UPDATE ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append-only';

CREATE TRIGGER trg_audit_logs_no_delete BEFORE DELETE ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append-only';

CREATE TRIGGER trg_late_fee_ledger_no_update BEFORE UPDATE ON late_fee_ledger FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'late_fee_ledger is append-only';

CREATE TRIGGER trg_late_fee_ledger_no_delete BEFORE DELETE ON late_fee_ledger FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'late_fee_ledger is append-only';

CREATE TRIGGER trg_payment_allocations_no_update BEFORE UPDATE ON payment_allocations FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'payment_allocations is append-only';

CREATE TRIGGER trg_payment_allocations_no_delete BEFORE DELETE ON payment_allocations FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'payment_allocations is append-only';
