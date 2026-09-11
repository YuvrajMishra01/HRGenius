-- =====================================================================
-- V3 — Phase 1 (auth): version counter for stateless logout + index
-- TOKEN_VERSION implements instant server-side logout: every JWT embeds
-- the version it was issued with; POST /auth/logout increments it.
-- =====================================================================
ALTER TABLE USERS ADD COLUMN TOKEN_VERSION NUMBER(19) DEFAULT 0 NOT NULL;

CREATE INDEX IX_USERS_ROLE ON USERS (ROLE);
