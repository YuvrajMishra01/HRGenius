-- =====================================================================
-- HRGenius V4 — onboarding ↔ recruitment link
-- ONBOARDINGS gains a nullable APPLICATION_ID pointing at the
-- JOB_APPLICATIONS row whose candidate was converted. Nullable because
-- employees can also be onboarded without a recruitment origin.
-- =====================================================================

ALTER TABLE ONBOARDINGS ADD APPLICATION_ID NUMBER(19);

ALTER TABLE ONBOARDINGS ADD CONSTRAINT UK_ONB_APP UNIQUE (APPLICATION_ID);

ALTER TABLE ONBOARDINGS ADD CONSTRAINT FK_ONB_APP
    FOREIGN KEY (APPLICATION_ID) REFERENCES JOB_APPLICATIONS (ID);
