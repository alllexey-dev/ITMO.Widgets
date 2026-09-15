# 0002 Flyway V1 is immutable; schema changes are new migrations

**Decision (2026-09-15).** `V1__initial_postgresql_schema.sql` is never edited
again. Every schema change, including on development, is a new versioned
migration; `V2__friendships.sql` was the first. Applied checksums are never
repaired, automatic baseline and clean stay disabled, Hibernate only validates.

**Why.** V1 was applied to the persistent development database on 2026-09-09.
A short-lived permission to keep editing V1 before the 2.1 release produced a
local V1 that no longer matched the deployed one; the development cluster was
recreated on 2026-09-15 to align them, and the permission was revoked.

**Consequence.** A mistake in an applied migration is corrected by a later one.
Before any schema-affecting deployment, back up and verify restore.
