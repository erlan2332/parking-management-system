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

## Stage 03 — JWT Authentication and Driver API

- Added Spring Security with the OAuth2 Resource Server JWT support instead of
  a custom authentication filter or custom cryptography.
- Configured stateless request processing. Only registration and login POST
  endpoints are public; every other route and HTTP method requires a valid
  Bearer token.
- Added BCrypt password hashing with a ten-character minimum and an explicit
  72-byte UTF-8 limit matching BCrypt's input constraint.
- Added HS256 access tokens containing user ID subject, issuer, issued time,
  and expiration. Runtime startup fails when `JWT_SECRET_BASE64` is missing,
  malformed, or shorter than 256 bits.
- Added profile and vehicle REST DTOs so persistence entities and password
  hashes never form part of HTTP responses. Vehicle ownership always comes
  from the validated JWT subject, never from request data.
- Reused `VehicleService` and `PlateNormalizer`; the only service extension is
  an owner-scoped vehicle lookup that returns the same 404 for missing and
  foreign vehicles.
- Added uniform validation, authentication, authorization, not-found, and
  conflict responses without exposing SQL or framework details.
- Added unit tests plus PostgreSQL/MockMvc integration tests with active
  security filters, including concurrent registration and invalid JWT cases.

## Stage 04 — Parking Spots and Availability

- Mapped the existing `parking_spots` table without changing Flyway V1-V3 and
  added a repository query that returns the seeded codes in natural order:
  `A1`-`A10`, then `B1`-`B10`.
- Kept operational activity separate from occupancy. Active spots currently
  resolve to `FREE`, inactive spots to `UNAVAILABLE`; `RESERVED` and
  `OCCUPIED` remain computed future states backed by real reservations and
  visits rather than new database columns or placeholder data.
- Added secured DTO-based endpoints for listing spots, reading one spot, and
  checking all spots for a requested interval. No persistence entity is
  exposed by the HTTP layer.
- Modelled request boundaries with `OffsetDateTime` and compare their instants,
  so offsets are handled correctly. Intervals are validated as non-empty
  half-open ranges `[startAt, endAt)`.
- Availability currently reflects the only relevant persisted fact:
  `active`. The service owns this rule so reservation overlap and active-visit
  checks can be added there in later stages without changing the API shape.
- Added unit, PostgreSQL-backed persistence/Flyway, and secured MockMvc tests,
  while retaining the existing uniform API error format.

## CI-01 — Backend Smoke Tests

- Added one GitHub Actions job for pull requests to `dev-parking` and pushes
  after merge. It uses Temurin Java 21, the project Gradle Wrapper, and Gradle
  dependency caching without adding a build matrix or deployment steps.
- Chose an ephemeral PostgreSQL 17 service container with a health check so CI
  exercises the same Flyway migrations, Hibernate validation, constraints, and
  SQL behavior as local integration tests instead of substituting H2.
- Added a dedicated `smokeTest` Gradle task that selects 12 existing critical
  scenarios with the JUnit `smoke` tag and fails when no tests are discovered.
  The standard `test` and `build` tasks still run the complete suite.
- Kept CI intentionally lightweight: it assembles the Spring Boot JAR, checks
  authentication, authorization, vehicles, parking spots, and availability,
  and uploads only test reports when a failure occurs.

## Stage 05 — Timed Holds and Reservations

- Chose one `reservations` table for temporary holds and confirmed bookings so
  confirmation changes one row from `HELD` to `ACTIVE` without a release gap or
  a second insert. Historical terminal states stay in the same table.
- Added PostgreSQL `btree_gist` exclusion constraints over half-open
  `tstzrange(start_at, end_at, '[)')` values. Separate constraints make the
  database authoritative against overlapping active intervals for both a spot
  and a vehicle, including requests that race between Java pre-checks.
- Standardized hold creation lock order as parking spot followed by vehicle.
  Before conflict checks, the transaction marks matching overdue holds as
  `HOLD_EXPIRED`; therefore a newly available interval can be acquired even if
  scheduled cleanup has not run yet.
- Kept time server-owned through an injected `Clock`. A deadline is the earlier
  of three minutes after creation and the requested start, is persisted in
  PostgreSQL, and cannot be supplied or extended by the client. Confirmation at
  the deadline or after the interval begins is rejected.
- Added a 20-second scheduled bulk cleanup that changes only overdue `HELD`
  rows. Repeated cleanup is safe and cannot expire `ACTIVE` reservations.
- Extended availability with `HELD` and `RESERVED` while retaining interval
  semantics. Blockers are loaded in one query for all spots, and future holds
  do not alter the current-instant spot list.
- Added owner-scoped secured hold, confirmation, release, history, and
  cancellation endpoints. Repeated confirmation, release, and cancellation of
  the already reached successful state are idempotent.
- Added focused unit, PostgreSQL integration, secured MockMvc, controlled-clock,
  and real concurrent-transaction tests. WebSocket broadcasting remains a
  later-stage concern; clients currently refresh state through REST.

## Stage 06 — Barrier Simulator and Parking Visits

- Added `parking_visits` as the source of truth for physical occupancy. Partial
  PostgreSQL unique indexes enforce one active visit per vehicle and spot, and
  a unique reservation reference prevents booking reuse even if an application
  pre-check races.
- Kept mutation lock order consistent with reservations: parking spot, vehicle,
  then the related reservation or visit. Walk-in allocation reads candidates
  in natural order, locks each candidate row, and rechecks visits and booking
  obligations after lock acquisition.
- Applied a conservative open-ended walk-in policy. A spot with any effective
  hold, active reservation, or checked-in reservation is not assigned because
  the walk-in exit time is unknown. The same rule rejects walk-in entry for a
  vehicle with a future obligation.
- Reservation entry is allowed only in the half-open window beginning at
  `startAt` and ending at the earlier of `endAt` and `startAt + 15 minutes`.
  Entry atomically changes `ACTIVE` to `CHECKED_IN` and links the new visit.
- Exit records precise server time without rounding. It completes the visit and
  changes a linked reservation from `CHECKED_IN` to `COMPLETED`; pricing and
  invoice generation remain separate future work.
- Extended parking state calculation with active visits. Physical occupancy has
  priority over reservation display and conservatively blocks every future
  interval until an actual exit is recorded. Queries load all active visits in
  one pass rather than per parking spot.
- Added controlled-clock, database-constraint, ownership, secured HTTP, and
  independently transacted concurrency tests, including walk-in versus hold
  creation on the same spot.

## Stage 07 — Day and Night Parking Pricing

- Added an immutable, validated pricing configuration with an explicit parking
  zone, day boundaries, and non-negative `BigDecimal` rates. Environment
  variables can override every value without coupling pricing to database or
  security configuration.
- Kept the calculator independent of HTTP and persistence. It accepts actual
  entry and exit `Instant` values and returns a reusable cost breakdown with
  day/night seconds, rates, component costs, total cost, and `KGS` currency.
- Split each visit at local tariff boundaries in `Asia/Bishkek` using
  half-open intervals. The algorithm handles both tariff transitions,
  midnight, multiple calendar days, and input timestamps with different UTC
  offsets without consulting the host system time zone.
- Calculate costs from whole seconds with `BigDecimal`. Component numerators
  are accumulated before division, and the combined total is rounded once to
  two decimal places using `HALF_UP`, avoiding per-segment minute rounding.
- Added an owner-scoped secured cost endpoint for completed visits. Foreign
  visits use the same 404 as missing visits, while an active visit returns a
  conflict because no final exit time exists.
- Deliberately left the quote transient. Until invoices snapshot rates and the
  amount in a later stage, recalculating historical visits uses the current
  application configuration.
