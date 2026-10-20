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
