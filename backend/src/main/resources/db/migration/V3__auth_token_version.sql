-- =====================================================================
-- V3 — Phase 1 (auth): version counter for stateless logout + index
-- TOKEN_VERSION implements instant server-side logout: every JWT embeds
-- the version it was issued with; POST /auth/logout increments it.
-- =====================================================================
-- Parenthesized ADD (column ...) is the real-Oracle grammar; H2 in Oracle
-- mode accepts it too (a bare ADD COLUMN would fail on Oracle with ORA-01735).
ALTER TABLE USERS ADD (TOKEN_VERSION NUMBER(19) DEFAULT 0 NOT NULL);

CREATE INDEX IX_USERS_ROLE ON USERS (ROLE);
