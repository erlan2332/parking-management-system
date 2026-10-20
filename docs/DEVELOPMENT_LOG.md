# Development Log

## Stage 01 — PostgreSQL and Flyway

- Configured the backend to read PostgreSQL connection settings from
  `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
- Chose `spring.jpa.hibernate.ddl-auto=validate`: Flyway owns schema changes,
  while Hibernate detects mapping/schema mismatches without mutating data.
- Added a PostgreSQL migration for `parking_spots` with a generated primary
  key, a unique non-blank code, and a non-null active flag.
- Added a separate seed migration for spots `A1`-`A10` and `B1`-`B10`.
  Flyway records applied migrations, and the unique code constraint plus
  `ON CONFLICT DO NOTHING` prevents duplicate seed rows.
- Kept occupancy out of `parking_spots`; later stages will derive it from
  reservations and active visits.
- Did not add domain classes or extra layers at this stage because there is no
  business behavior to model yet. This keeps the foundation focused and avoids
  premature abstractions while preserving the existing package structure.

## Stage 02 — Users and Vehicles

- Added `users` and `vehicles` through a new V3 migration; the already applied
  V1 and V2 migrations remain unchanged.
- Enforced case-insensitive email uniqueness with a PostgreSQL expression
  index on `LOWER(email)` and global vehicle identity with a unique normalized
  plate constraint.
- Modelled only the required unidirectional `VehicleEntity` to `UserEntity`
  association. It is lazy and has no remove cascade, avoiding unnecessary
  collection loading and accidental historical-data deletion.
- Kept plate normalization in a stateless component so creation and future
  lookup use identical rules without coupling those rules to persistence.
- Added a transactional vehicle service. A pre-check provides a clear normal
  duplicate response, while the database constraint remains authoritative for
  concurrent inserts.
- Added unit tests for normalization and service rules plus PostgreSQL-backed
  integration tests using a separate `TEST_DB_*` connection.
