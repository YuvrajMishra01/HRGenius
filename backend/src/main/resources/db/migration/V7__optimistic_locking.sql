-- Phase 21: optimistic locking. JPA-managed @Version columns turn the
-- read-modify-write status transitions (leave decide/cancel, payroll
-- PROCESSED/PAID, performance rate/acknowledge) into guarded atomic updates:
-- the loser of a concurrent race gets a fresh ObjectOptimisticLocking-
-- FailureException instead of silently overwriting the winner's state.
-- Numeric identity-style columns; rows start at 0.
-- Parenthesized ADD (column ...) is the real-Oracle grammar; H2 in Oracle
-- mode accepts it too (a bare ADD COLUMN would fail on Oracle with ORA-01735).
ALTER TABLE LEAVE_REQUESTS      ADD (VERSION NUMBER(19) DEFAULT 0 NOT NULL);
ALTER TABLE PAYROLLS            ADD (VERSION NUMBER(19) DEFAULT 0 NOT NULL);
ALTER TABLE PERFORMANCE_REVIEWS ADD (VERSION NUMBER(19) DEFAULT 0 NOT NULL);
