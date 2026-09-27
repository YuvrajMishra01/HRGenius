-- =====================================================================
-- HRGenius V6 — DB-enforced uniqueness for performance review periods
-- Phase 20: the service already rejects a duplicate employee+period
-- review (409 "one review per employee per period"), but only at the
-- application layer — two concurrent creates could both pass the check.
-- Closing that race window at the schema level mirrors every other
-- uniqueness rule in this schema (UK_PAY_EMP_PERIOD, UK_ATT_EMP_DATE,
-- UK_ONB_EMPLOYEE, UK_ONB_APP).
--
-- REVIEW_PERIOD stays nullable: rows without a period are exempt from
-- uniqueness (Oracle and H2 unique constraints ignore NULL keys), and
-- the two V2 seed rows use distinct periods, so this is a safe backfill.
-- =====================================================================

ALTER TABLE PERFORMANCE_REVIEWS ADD CONSTRAINT UK_PR_EMP_PERIOD
    UNIQUE (EMPLOYEE_ID, REVIEW_PERIOD);
