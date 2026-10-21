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

## Stage 08 — Parking Invoices and Billing History

- Added `invoices` through V6 without changing earlier migrations. A unique
  visit reference and PostgreSQL checks enforce one `ISSUED` KGS invoice per
  visit, consistent non-negative durations, rates and amounts, and valid
  timestamp ordering. The restrictive foreign key protects billing history.
- Kept pricing in `ParkingPricingService`. It now exposes the configured zone
  and high-precision day/night components while still deriving the authoritative
  two-decimal total from the combined numerator with one `HALF_UP` rounding.
- Made exit completion and invoice insertion one transaction. Invoice
  persistence failure rolls back the exit; a projection-based lock target
  prevents stale managed visit state during concurrent exits, and the database
  unique constraint remains the final protection against duplicate billing.
- Snapshotted the actual interval, plate, spot, zone, durations, rates, cost
  components, total, and currency. Issued amounts are consequently independent
  of later configuration changes, and invoice numbers are stable derivatives
  of database IDs rather than a separate sequence.
- Added secured, owner-scoped DTO APIs for invoice history, invoice details,
  and invoice lookup by visit. Missing and foreign invoices are indistinguishable
  to callers, list loading fetches the required visit in one query, and GET
  requests never create financial data.
- Made the existing visit-cost endpoint prefer the persisted invoice snapshot.
  Pre-V6 completed visits without snapshots may still be quoted using current
  rates, but are not backfilled or represented as historically issued invoices.
- Added focused PostgreSQL and MockMvc tests for snapshots, ownership, stable
  ordering, legacy visits, constraints, rollback, idempotent issue handling,
  and two independently transacted concurrent exits without timing sleeps.

## Stage 09 — Reservation Automation and Email Reminders

- Centralized the confirmed-booking entry deadline as
  `min(endAt, startAt + 15 minutes)`. The same policy is used by barrier entry,
  availability, hold creation, history refresh, and scheduled expiration, so
  the exact boundary has one consistent half-open interpretation.
- Added bounded PostgreSQL no-show cleanup with `FOR UPDATE SKIP LOCKED` and
  status-guarded updates. Entry preserves the established spot, vehicle, then
  reservation lock order and rereads locked state before creating a visit.
- Kept logical expiration independent from scheduler timing. An overdue
  `ACTIVE` booking stops blocking availability immediately, while hold creation
  persists its `EXPIRED` state before relying on the existing exclusion
  constraints. No-show expiration creates no visit, invoice, or charge.
- Replaced the separate hold scheduler with one small configurable automation
  coordinator. Temporary-hold rules are unchanged; the coordinator invokes
  hold cleanup, no-show cleanup, and reminder processing every 30 seconds by
  default.
- Added a V7 PostgreSQL notification outbox with one
  `RESERVATION_ENDING_SOON` row per reservation, constrained processing states,
  attempt/error metadata, and indexes for safe claim and retry work.
- Split reminder eligibility, transactional outbox state, message formatting,
  and SMTP transport into focused components. SMTP calls happen outside the
  database transaction and eligibility is rechecked immediately before send.
- Made mail opt-in with validated environment configuration and finite network
  timeouts. Tests and CI use an in-memory fake sender; no external mail service
  or credential is required.
- Documented the unavoidable SMTP acknowledgement gap: database uniqueness and
  row claiming prevent normal concurrent duplicates, but a process failure
  after SMTP acceptance and before the `SENT` commit can still cause a retry.

## Stage 10 — Authenticated Real-time Parking Updates

- Added a native `/ws/parking` endpoint with STOMP and Spring's in-memory simple
  broker. Authorized clients subscribe only to `/topic/parking/changes`; no
  SockJS fallback, external broker, or parallel state store was introduced.
- Reused the REST `JwtDecoder` in an inbound channel interceptor. JWT is
  required in the STOMP `CONNECT` header, while anonymous subscriptions,
  unknown destinations, client `SEND` commands, invalid signatures, expired
  tokens, and unapproved Origins are rejected.
- Added a minimal interval-independent event containing only reason, spot ID,
  spot code, and occurrence time. Clients treat it as an invalidation hint and
  reload their own interval through the existing REST availability endpoint.
- Publish internal events inside business transactions and bridge them to STOMP
  with an `AFTER_COMMIT` listener. Rollbacks emit nothing, while broker errors
  are isolated from already committed reservation, visit, invoice, and cleanup
  work.
- Integrated actual hold creation/release/confirmation/expiration,
  cancellation, no-show, walk-in or reserved entry, and invoice-backed exit.
  Idempotent calls and repeated scheduler passes do not emit false changes.
- Reworked hold and no-show cleanup to select bounded, locked reservation IDs
  before entity transitions. This retains PostgreSQL `SKIP LOCKED`, preserves
  lock ordering, and provides the exact affected spot for each committed event.
- Added real random-port WebSocket tests with two STOMP clients plus controlled
  JWT, Origin, forbidden destination/SEND, lifecycle, rollback, and reconnect
  security scenarios. No external broker or timing sleeps are required.
- Documented the simple broker's single-instance, non-durable delivery model:
  missed messages are not replayed, so reconnecting clients must resynchronize
  from PostgreSQL through REST.

## Stage 11 — Backend Hardening and Clean PostgreSQL Verification

- Audited the complete REST surface, DTO boundaries, unified error mapping,
  owner-scoped queries, JWT decoder, STOMP interceptor, reservation and visit
  locks, invoice immutability, scheduler paths, mail outbox, and Java Time
  rules. Existing PostgreSQL and integration coverage already exercises the
  required hold, no-show, walk-in, concurrent exit, reminder, and WebSocket
  races, so those modules were not rewritten.
- Found one frontend-blocking integration defect: Spring Security had no REST
  CORS configuration even though the WebSocket endpoint validated Origins.
  Added a dedicated, validated REST origin list, allowed only the existing
  `GET`, `POST`, and preflight methods plus `Authorization`/`Content-Type`, and
  kept wildcard origins forbidden. Two MockMvc preflight tests and one
  property-validation test reproduce and protect the fix.
- Removed implicit runtime and test database connection defaults. Application
  startup now requires `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; integration
  tests require the separate `TEST_DB_*` variables. This makes an accidental
  connection to an unrelated local database less likely and produces an
  immediate configuration error when required settings are absent.
- Verified a genuinely empty PostgreSQL 14 database owned by a temporary,
  non-superuser application role. Flyway installed the trusted `btree_gist`
  extension, applied V1-V7, created all seven domain tables plus the expected
  constraints and partial indexes, seeded exactly 20 spots, and passed
  Hibernate `ddl-auto=validate`. Restarting retained seven migration rows and
  20 spots without reapplying seed data.
- Exercised the clean instance through real HTTP: registration, login, bearer
  JWT, vehicle creation, 20-spot listing, interval availability, hold creation
  and confirmation, reservation history, walk-in entry, double-entry refusal,
  exit, repeated-exit refusal, visit history, invoice history, and stable
  repeated invoice reads. Negative checks returned the established
  400/401/404/409 contracts and approved REST preflight headers.
- Re-ran the complete PostgreSQL suite: 159 tests passed with no failures,
  errors, or skips. The smoke subset remains 22/22 and still covers real JWT,
  concurrency, invoice, no-show, fake-mail, and two-client STOMP scenarios;
  the CI workflow itself did not need modification.
- Expanded the employer-facing setup guide with exact PostgreSQL ownership and
  `btree_gist` requirements, mandatory runtime/test variables, REST and
  WebSocket Origin separation, registration/login examples, clean migration
  verification, and concrete startup troubleshooting.
- Remaining limitations are intentional: no refresh token, external message
  broker, durable WebSocket history, exactly-once SMTP guarantee, payment
  processing, or frontend. Within the documented single-backend architecture,
  the server contracts are ready for the React client.

## Stage 11-R — Backend Architecture Refactoring

- Normalized every feature into responsibility-focused packages such as
  `controller`, `dto`, `mapper`, `service`, `repository`, `entity`, `model`,
  and `config`. Cross-cutting scheduling remains in `automation`; no empty
  layers or one-implementation service interfaces were added.
- Added Lombok only for mechanical boilerplate: constructor injection, JPA
  getters and protected no-argument constructors, and simple configuration
  accessors. Explicit constructors remain where JWT and pricing configuration
  validation protects invariants.
- Added dedicated response mappers for auth, users, vehicles, parking spots,
  reservations, barrier operations, visits, and invoices. Response DTOs no
  longer import JPA entities, and controllers no longer call DTO factories.
- Simplified `AuthService` to return domain results and moved visit-cost
  selection from the controller into `ParkingVisitService`.
- Kept `BarrierService` as the transaction-owning facade while moving entry
  and exit orchestration into focused collaborators. Entry still uses
  `noRollbackFor` for persisted no-show transitions; exit and invoice creation
  remain one transaction with the established spot-to-vehicle lock order.
- Extracted hold and no-show expiration into
  `ReservationExpirationService`. Scheduler and request-time cleanup now share
  one implementation without introducing independent nested transactions.
  Reservation status methods apply one consistent source-state guard.
- Removed repository and service methods with no production callers. Active
  spot occupancy now uses an ID projection instead of loading complete visit
  entities, while PostgreSQL exclusion constraints, partial unique indexes,
  pessimistic locks, and Flyway V1-V7 remain unchanged.
- Replaced six duplicated mutable clock implementations with one test fixture
  and added coverage for sub-second tariff-boundary allocation. The final
  PostgreSQL-backed build passed 160/160 tests; the unchanged smoke selection
  passed 22/22, including JWT, concurrency, invoices, reminders, and STOMP.
- REST endpoints, JSON fields, status codes, JWT rules, WebSocket endpoint and
  topic, event reasons, tariffs, rounding, reservation timing, and historical
  invoice semantics were preserved.

## Stages 12-15 — Complete React Frontend and Full-Stack Integration

- Created a strict React, TypeScript, Vite, and Tailwind application around the
  existing Backend contracts. Feature modules own auth, profile, vehicles,
  parking, reservations, realtime, barrier, visits, and invoices; shared code
  is limited to the typed API client, formatting, layout, and reusable UI.
- Added session-scoped JWT authentication, protected routes, unified Russian
  API errors, responsive navigation, loading/empty/error states, and targeted
  TanStack Query keys instead of duplicating server state in React Context.
- Implemented interval-aware 20-spot parking, server-backed three-minute holds,
  countdown from `holdExpiresAt`, confirmation/release, reservation history,
  and cancellation confirmation. The selected interval is part of the
  availability query key and conflicts trigger a fresh REST read.
- Connected STOMP with JWT in `CONNECT`, automatic reconnect, teardown on
  logout, and REST invalidation after every parking event. A real two-session
  browser check observed both hold creation and release without reloading.
- Added the barrier simulator, active/completed visit history, automatic invoice
  discovery, and saved day/night tariff details. Vehicle status compares the
  Backend's normalized plate identity, avoiding stale entry controls when the
  display plate contains spaces or separators.
- Verified the layout at desktop and 390-pixel mobile widths. Fixed the invoice
  detail's intrinsic grid sizing so the document has no horizontal overflow,
  while keeping readable tariff columns and touch-sized controls.
- Added focused Vitest/Testing Library coverage plus a separate Frontend CI
  workflow. Updated both READMEs with real full-stack startup, user journeys,
  WebSocket verification, tests, ports, and explicit product limitations.
