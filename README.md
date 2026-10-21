# Parking Management System

Full-stack paid parking management system.

## Tech Stack

- Backend: Java 21, Spring Boot, Gradle
- Frontend: React, TypeScript (planned)
- Database: PostgreSQL, Flyway
- Security: Spring Security, OAuth2 Resource Server, JWT (HS256)
- Infrastructure: GitHub Actions with PostgreSQL service containers

## Project Structure

- parking-backend/ - Spring Boot backend
- parking-frontend/ - React frontend (planned)

## Current Status

The Spring Boot backend is configured to use a local PostgreSQL database.
Flyway manages parking spots, users, vehicles, and reservations. Drivers can
register, log in, receive a 60-minute JWT access token, manage their vehicles,
hold a parking spot for three minutes, and confirm or cancel a reservation
through a secured REST API. The barrier simulator supports reservation and
walk-in entry, physical exit, and visit history. Exit atomically completes the
visit and issues an invoice that preserves the applied day/night rates and
amount. Drivers can review their invoice history through secured REST APIs.
Confirmed reservations expire automatically after a missed entry window, and
checked-in drivers can receive one ending-soon email reminder when SMTP is
enabled. Authenticated STOMP clients receive real-time parking availability
change notifications and then refresh their selected interval through REST.
The frontend is not implemented yet.

## Planned Features

- React + TypeScript driver interface
- Payment processing (invoices already exist; payments do not)

## Backend Architecture

Backend code is organized by business module (`auth`, `reservation`,
`barrier`, `invoice`, and so on), then by responsibility inside each module:
`controller`, `dto`, `mapper`, `service`, `repository`, `entity`, `model`,
`config`, or another narrowly named technical role. A package is created only
when that responsibility exists; empty architectural layers are avoided.

- Controllers define HTTP boundaries, delegate use cases to services, and map
  domain results to response DTOs.
- DTOs define JSON contracts and validation only. They never depend on JPA
  entities.
- Mappers own entity/domain-to-response conversion without repositories,
  transactions, or business decisions.
- Services own use cases and transaction boundaries. Service interfaces are
  introduced only when multiple implementations or a real substitution
  boundary exists.
- Entities protect persistent state and valid status transitions. Repositories
  retain PostgreSQL-specific locking and projection queries.

Lombok is used for safe mechanical boilerplate: constructor injection,
entity getters, protected JPA no-argument constructors, and simple property
accessors. Constructors that validate configuration or establish invariants
remain explicit. The barrier facade deliberately owns the entry and exit
transactions; its focused collaborators do not start independent transactions,
so lock order and atomic invoice creation remain unchanged.

## Local Development

### Prerequisites

- Java 21 (`java -version` must report 21)
- PostgreSQL with the `btree_gist` extension files available; PostgreSQL 14 and
  17 are verified by local and CI runs
- the committed Gradle Wrapper (`parking-backend/gradlew`); a system Gradle
  installation is not required
- an empty application database plus a separate, disposable test database

Example PostgreSQL setup (run as a PostgreSQL administrator):

```sql
CREATE USER parking_user WITH PASSWORD 'choose_a_local_password';
CREATE DATABASE parking_db OWNER parking_user;
CREATE DATABASE parking_test_db OWNER parking_user;
```

Migration V4 runs `CREATE EXTENSION IF NOT EXISTS btree_gist`. On supported
PostgreSQL releases `btree_gist` is a trusted extension, so the database owner
can install it when the extension package is present. The application role
therefore needs `CONNECT` and normal schema rights and should own the empty
database; it does not need to be a PostgreSQL superuser. If a managed provider
does not permit the owner to install extensions, ask its administrator to run
the following once in the new application database instead of granting broad
superuser rights:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
```

### Environment variables

Copy `.env.example` to `.env` if you want a local reference file, but do not
commit `.env`. Spring Boot does not load `.env` files automatically. Export the
variables in the shell before starting the application:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/parking_db'
export DB_USERNAME='parking_user'
export DB_PASSWORD='choose_a_local_password'
openssl rand -base64 32
export JWT_SECRET_BASE64='paste_the_generated_value_here'

export PARKING_PRICING_ZONE_ID='Asia/Bishkek'
export PARKING_DAY_START='08:00'
export PARKING_DAY_END='20:00'
export PARKING_DAY_RATE_PER_MINUTE='2.00'
export PARKING_NIGHT_RATE_PER_MINUTE='1.00'

export RESERVATION_AUTOMATION_DELAY='PT30S'
export PARKING_REST_ALLOWED_ORIGINS='http://localhost:5173'
export PARKING_WEBSOCKET_ALLOWED_ORIGINS='http://localhost:5173'
export MAIL_ENABLED='false'

export TEST_DB_URL='jdbc:postgresql://localhost:5432/parking_test_db'
export TEST_DB_USERNAME='parking_user'
export TEST_DB_PASSWORD='choose_a_local_password'
```

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET_BASE64` are mandatory
and intentionally have no runtime credentials or connection defaults.
`JWT_SECRET_BASE64` must decode to at least 32 random bytes. Keep the same local
secret across restarts if previously issued access tokens should remain valid.
Full and smoke integration tests require all three `TEST_DB_*` variables and
must point to a dedicated disposable database, never `parking_db`. The
committed test profile contains only an isolated JWT test key; production and
local runtime configuration never fall back to it.

Pricing defaults to `Asia/Bishkek`, a half-open day period of `[08:00, 20:00)`,
`2.00 KGS` per day minute, and `1.00 KGS` per night minute. The five
`PARKING_*` variables above override the zone, boundaries, and rates. Rates
must be zero or positive, and the day start must be earlier than the day end.
Reservation automation runs every 30 seconds by default. Email is disabled by
default, so cleanup, registration, reservations, the barrier, and invoices run
without an SMTP server.

Browser REST calls accept only origins listed in
`PARKING_REST_ALLOWED_ORIGINS`; only the current `GET`, `POST`, and `OPTIONS`
methods and `Authorization`/`Content-Type` request headers are allowed. The
WebSocket handshake separately uses `PARKING_WEBSOCKET_ALLOWED_ORIGINS`.
Both variables accept a comma-separated list, default to
`http://localhost:5173` for local development, and reject wildcard origins.
No production domain is assumed.

### Build and run

```bash
cd parking-backend
./gradlew bootRun
```

Flyway applies migrations from `src/main/resources/db/migration` before
Hibernate validates the schema. Hibernate does not create or update tables.
To run the complete build separately:

```bash
cd parking-backend
./gradlew clean build
```

The build requires `TEST_DB_*` because the persistence tests apply the real
Flyway migrations and verify PostgreSQL-specific constraints; H2 is not used.

To verify the initial data:

```bash
psql -h localhost -p 5432 -U "$DB_USERNAME" -d parking_db \
  -c "SELECT code, active FROM parking_spots ORDER BY code;"
```

Migrations V1 and V2 create `parking_spots` and add spots `A1`-`A10` and
`B1`-`B10`. Current occupancy is deliberately derived from reservations and
active visits rather than stored as a second source of truth on a spot.

Migration V3 adds users and their vehicles. Email uniqueness is
case-insensitive, while normalized vehicle plates are globally unique so a
future barrier lookup can identify exactly one vehicle.

Migration V4 adds a single `reservations` table for both temporary holds and
confirmed reservations. PostgreSQL exclusion constraints over half-open
`tstzrange` intervals prevent overlapping `HELD`, `ACTIVE`, and `CHECKED_IN`
records for either the same parking spot or the same vehicle.

Migration V5 adds `parking_visits`. Partial unique indexes allow only one
active visit per vehicle and per parking spot, while a unique reservation link
prevents using one booking for multiple visits. Visit history is retained after
exit and is never cascade-deleted with a vehicle, spot, or reservation.

Migration V6 adds `invoices`. Each completed visit can have at most one invoice,
and PostgreSQL validates its currency, non-negative durations, rates and costs,
duration split, and timestamp order. The visit foreign key uses `ON DELETE
RESTRICT`, so an issued financial record is not removed through a visit cascade.

Migration V7 adds `reservation_notifications` with a restrictive reservation
foreign key, a unique `(reservation_id, notification_type)` pair, constrained
processing statuses, attempt history, and an index for pending work. It also
adds a partial index for finding due `ACTIVE` no-show reservations.

### Driver API

Public endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`

The remaining endpoints require `Authorization: Bearer <access-token>`:

- `GET /api/users/me`
- `POST /api/vehicles`
- `GET /api/vehicles`
- `GET /api/vehicles/{id}`
- `GET /api/parking/spots`
- `GET /api/parking/spots/{id}`
- `GET /api/parking/availability`
- `POST /api/reservations/holds`
- `POST /api/reservations/holds/{id}/confirm`
- `POST /api/reservations/holds/{id}/release`
- `GET /api/reservations/holds/my`
- `GET /api/reservations/my`
- `POST /api/reservations/{id}/cancel`
- `POST /api/barrier/entry`
- `POST /api/barrier/exit`
- `GET /api/visits/my`
- `GET /api/visits/{id}/cost`
- `GET /api/visits/{id}/invoice`
- `GET /api/invoices/my`
- `GET /api/invoices/{id}`

JWT access tokens expire after 60 minutes. There is no refresh-token or logout
API in the current stage.

With the application running, register and log in with real HTTP requests. The
following example uses `jq` only to extract the token:

```bash
curl -sS -X POST 'http://localhost:8080/api/auth/register' \
  -H 'Content-Type: application/json' \
  -d '{"email":"driver@example.com","password":"SecurePassword123!"}'

export TOKEN="$(curl -sS -X POST 'http://localhost:8080/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email":"driver@example.com","password":"SecurePassword123!"}' \
  | jq -r '.accessToken')"

export VEHICLE_ID="$(curl -sS -X POST 'http://localhost:8080/api/vehicles' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"plateNumber":"01-KG-123-ABC"}' | jq -r '.id')"

curl -sS 'http://localhost:8080/api/parking/spots' \
  -H "Authorization: Bearer $TOKEN"
```

### Reservation flow

Creating a hold accepts only the driver's own vehicle, an active parking spot,
and a future interval. The server calculates `holdExpiresAt` as the earlier of
the reservation start and three minutes after creation; clients cannot set or
extend it. A hold can be atomically confirmed to `ACTIVE` or released to
`HOLD_RELEASED`. Unconfirmed holds become `HOLD_EXPIRED` through a scheduled
cleanup, while creation and availability checks also treat a hold as expired
as soon as its deadline passes. An `ACTIVE` reservation can be cancelled before
its start and remains in history as `CANCELLED`.

Availability is calculated for the requested interval and returns `FREE`,
`HELD`, `RESERVED`, `OCCUPIED`, or `UNAVAILABLE`. The regular spot list reflects only the
current instant, so a future reservation does not make a spot appear occupied
now. When several blockers exist, the display priority is `OCCUPIED`,
`RESERVED`, `HELD`, then `FREE`; inactive spots always return `UNAVAILABLE`.

With the application running, the reservation path can be checked with curl.
The examples assume `TOKEN`, `VEHICLE_ID`, and `SPOT_ID` were obtained from the
authentication, vehicle, and parking endpoints:

```bash
export START_AT='2026-12-01T14:00:00+06:00'
export END_AT='2026-12-01T16:00:00+06:00'

curl -sS 'http://localhost:8080/api/parking/availability' \
  -H "Authorization: Bearer $TOKEN" \
  --get --data-urlencode "startAt=$START_AT" --data-urlencode "endAt=$END_AT"

curl -sS -X POST 'http://localhost:8080/api/reservations/holds' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"vehicleId\":$VEHICLE_ID,\"spotId\":$SPOT_ID,\"startAt\":\"$START_AT\",\"endAt\":\"$END_AT\"}"

curl -sS -X POST "http://localhost:8080/api/reservations/holds/$HOLD_ID/confirm" \
  -H "Authorization: Bearer $TOKEN"
curl -sS 'http://localhost:8080/api/reservations/my' \
  -H "Authorization: Bearer $TOKEN"
curl -sS -X POST "http://localhost:8080/api/reservations/$HOLD_ID/cancel" \
  -H "Authorization: Bearer $TOKEN"
```

Use `POST /api/reservations/holds/{id}/release` instead of confirmation to
voluntarily return a held spot. The three-minute `HOLD_EXPIRED` rule applies
only to unconfirmed holds. A confirmed `ACTIVE` booking instead becomes
`EXPIRED` when entry has not happened by `min(endAt, startAt + 15 minutes)`.
This no-show transition creates no visit, invoice, fee, or penalty. Availability
and hold creation treat that deadline as effective immediately, even before the
next scheduled database update, so an expired booking no longer blocks a spot.

### Barrier and parking visits

`POST /api/barrier/entry` normalizes the submitted plate and only accepts a
vehicle owned by the authenticated driver. If an `ACTIVE` reservation is in
its entry window—from `startAt` through the earlier of `endAt` and
`startAt + 15 minutes`, excluding the deadline—the visit uses its reserved spot
and changes the reservation to `CHECKED_IN`.

Without a reservation, walk-in entry assigns the first suitable active spot in
natural order (`A1` through `B10`). Selection locks candidate spots in that
same order and rechecks physical occupancy and reservation blockers after the
lock is acquired. Because a walk-in exit time is unknown, spots with any live
hold or confirmed future reservation are conservatively skipped. The same rule
prevents walk-in entry when the vehicle itself has a future hold or booking.

`POST /api/barrier/exit` completes the active visit using server time and issues
its invoice in the same transaction. A linked reservation changes from
`CHECKED_IN` to `COMPLETED`. The response retains the visit fields and also
includes `invoiceId`, `invoiceNumber`, `totalAmount`, and `currency`.
`GET /api/visits/my` returns active and completed visits, newest first, with
actual duration for completed visits. Active visits make a spot `OCCUPIED` for
both its current state and all future availability requests until physical exit.

Example with an existing token and registered vehicle:

```bash
curl -sS -X POST 'http://localhost:8080/api/barrier/entry' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"plateNumber":"01KG123ABC"}'

curl -sS 'http://localhost:8080/api/visits/my' \
  -H "Authorization: Bearer $TOKEN"

curl -sS -X POST 'http://localhost:8080/api/barrier/exit' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"plateNumber":"01KG123ABC"}'
```

Exit does not require or simulate payment. Control of physical barrier
hardware remains outside the current stage.

### Reservation automation and email reminders

One scheduled task runs every 30 seconds by default. It retains the existing
temporary-hold cleanup, expires confirmed no-shows in bounded PostgreSQL
batches, and processes ending-soon reminders. Entry and expiration use the
same half-open 15-minute deadline and lock order (spot, vehicle, reservation),
so the exact deadline is already too late and concurrent work cannot leave an
`EXPIRED` booking with an active visit.

An email reminder is eligible only in `[endAt - 10 minutes, endAt)` while the
booking is `CHECKED_IN` and its linked visit is still active. This also covers
a short booking when entry happens with less than ten minutes remaining. An
early exit prevents the message; walk-in visits, no-shows, and cancelled
bookings are not eligible. The plain-text message uses the vehicle owner's
stored email address and displays the planned end time in `Asia/Bishkek`.

Email is disabled by default. To use a local test SMTP server such as Mailpit:

```bash
docker run --rm -p 1025:1025 -p 8025:8025 axllent/mailpit
export MAIL_ENABLED='true'
export MAIL_HOST='localhost'
export MAIL_PORT='1025'
export MAIL_FROM='parking@example.test'
export MAIL_STARTTLS_ENABLED='false'
./parking-backend/gradlew -p parking-backend bootRun
```

Mailpit then shows captured messages at `http://localhost:8025`. Authenticated
SMTP can additionally use `MAIL_USERNAME` and `MAIL_PASSWORD`; both must be
set together. Connect, read, and write timeouts default to five seconds and can
be overridden with `MAIL_CONNECT_TIMEOUT`, `MAIL_READ_TIMEOUT`, and
`MAIL_WRITE_TIMEOUT`. Leaving `MAIL_ENABLED=false` requires no SMTP settings
and does not disable no-show cleanup or any backend feature.

The PostgreSQL outbox has a unique notification type per reservation and uses
row claiming with `SKIP LOCKED`, retryable failures, and stale `SENDING`
recovery. SMTP itself cannot provide end-to-end exactly-once delivery: if a
server accepts a message and the application stops before recording `SENT`, a
later retry can still duplicate it. Automated tests therefore verify
application-level deduplication with a fake transport; they never contact a
real mailbox. Controlled `Clock` integration tests exercise both automation paths
without waiting 10 or 15 real minutes:

```bash
cd parking-backend
./gradlew test --tests '*ReservationAutomationIntegrationTest' \
  --tests '*ReservationReminderIntegrationTest'
```

### Real-time parking updates

The backend exposes a native WebSocket endpoint at `/ws/parking`, speaks STOMP,
and uses the built-in single-instance simple broker. A browser performs the
HTTP upgrade from an allowed Origin, sends `Authorization: Bearer <JWT>` in the
STOMP `CONNECT` headers, and subscribes to `/topic/parking/changes`. The token
is decoded by the same `JwtDecoder` as REST, including signature, expiration,
issuer, and positive user-ID subject checks. Tokens are never accepted in the
URL. Anonymous or invalid connections, subscriptions to other destinations,
and every client `SEND` command are rejected.

Successful state changes produce a minimal event after the PostgreSQL
transaction commits:

```json
{
  "type": "PARKING_AVAILABILITY_CHANGED",
  "reason": "HOLD_CREATED",
  "spotId": 2,
  "spotCode": "A2",
  "occurredAt": "2026-12-01T13:30:00+06:00"
}
```

Supported reasons are `HOLD_CREATED`, `HOLD_RELEASED`, `HOLD_EXPIRED`,
`RESERVATION_CONFIRMED`, `RESERVATION_CANCELLED`, `RESERVATION_EXPIRED`,
`VEHICLE_ENTERED`, and `VEHICLE_EXITED`. The public topic contains no user,
vehicle, invoice, email, or token data. A rollback produces no message, and a
broker failure cannot roll back an already committed hold, visit, or invoice.

An event is only an invalidation hint because availability depends on the
client's interval. After connecting, and after every event, a future React
client should call `GET /api/parking/availability?startAt=...&endAt=...` for
its current interval. Frequent notifications should be debounced into a small
number of refreshes, and an older HTTP response must not overwrite newer data.
PostgreSQL constraints and service transactions—not the event—remain the
authority for a hold attempt.

Temporary holds become logically ineffective at `holdExpiresAt`; the scheduled
`HOLD_EXPIRED` event may arrive up to one automation interval later. A client
should keep its server-provided deadline and still refresh availability. After
a disconnect, browser sleep, server restart, or JWT expiration, reconnect with
a valid token, resubscribe, and perform a full REST refresh. Reconnection does
not extend a hold or replay missed events.

For a future STOMP.js client, the essential setup is:

```javascript
const client = new Client({
  brokerURL: "ws://localhost:8080/ws/parking",
  connectHeaders: { Authorization: `Bearer ${token}` },
  onConnect: () => client.subscribe("/topic/parking/changes", refreshAvailability)
});
client.activate();
```

The real integration test opens two authenticated STOMP clients, subscribes
both, creates and confirms a hold, receives both events, and verifies `HELD`
then `RESERVED` through the existing availability service. It also covers
Origin and JWT rejection without using arbitrary sleeps:

```bash
cd parking-backend
./gradlew test --tests '*ParkingRealtimeIntegrationTest'
```

The simple broker is intentionally non-durable: disconnected clients can miss
events, and separate backend instances do not exchange them. Horizontal
deployment would require a shared broker or another distribution mechanism;
neither event persistence nor an external broker is introduced in this stage.

### Parking pricing

`GET /api/visits/{id}/cost` returns a detailed price only for a completed visit
owned by the authenticated driver. Active visits return a conflict because
their final duration is unknown; missing and foreign visits share the same
not-found response. For a visit with an invoice, the response uses its saved
historical values. A completed pre-V6 visit without an invoice retains the old
current-tariff calculation behavior and is not presented as an issued invoice.

Tariff selection always uses the configured parking zone, `Asia/Bishkek` by
default, rather than the host system zone or the offsets originally used to
write a timestamp. Every visit is split at `08:00`, `20:00`, and midnight for
each local calendar day. For example, a visit from `19:30` to `20:30` contains
30 day minutes and 30 night minutes and costs `90.00 KGS` with the default
rates.

Partial minutes are proportional to actual whole seconds:
`segmentSeconds × ratePerMinute / 60`. Segment values are accumulated before
the total is rounded to two decimal places with `HALF_UP`; a 30-second day
visit therefore costs `1.00 KGS`. The response includes total/day/night
seconds, both rates, both cost components, total cost, and currency `KGS`.

```bash
curl -sS "http://localhost:8080/api/visits/$VISIT_ID/cost" \
  -H "Authorization: Bearer $TOKEN"
```

### Invoices and billing history

Every new successful exit creates one `ISSUED` invoice. The invoice snapshots
entry and exit time, vehicle plate, spot code, pricing zone, day/night seconds,
both rates, high-precision cost components, the once-rounded authoritative
`totalAmount`, and `KGS` currency. Later pricing configuration changes therefore
cannot change an already issued amount. Visit completion and invoice insertion
are atomic, while row locks and the unique `visit_id` constraint prevent a
concurrent exit from creating a second charge.

`GET /api/invoices/my` returns the authenticated driver's invoices newest
first. `GET /api/invoices/{id}` and `GET /api/visits/{id}/invoice` return the
saved invoice details; missing and foreign invoices are hidden with 404. An
active visit or a completed pre-V6 visit without a snapshot returns a conflict,
and GET requests never create financial records.

```bash
curl -sS 'http://localhost:8080/api/invoices/my' \
  -H "Authorization: Bearer $TOKEN"
curl -sS "http://localhost:8080/api/invoices/$INVOICE_ID" \
  -H "Authorization: Bearer $TOKEN"
curl -sS "http://localhost:8080/api/visits/$VISIT_ID/invoice" \
  -H "Authorization: Bearer $TOKEN"
```

Invoices are a history of charges, not successful bank transactions. This
stage has no payment acceptance, `PAID` state, PDF, email receipt, refund, or
automatic backfill. Historical rates for visits completed before V6 were never
stored, so the application deliberately does not invent invoices for them.

## Clean PostgreSQL verification

The backend has been verified from an empty database owned by a non-superuser
application role. On the first start Flyway installed `btree_gist`, applied
V1-V7, created all tables, constraints, and indexes, seeded exactly 20 spots,
and Hibernate completed `ddl-auto=validate`. A second start kept seven
successful migration rows and 20 spots, demonstrating that migrations and
seed data were not repeated.

You can verify the same properties after the first local start:

```bash
psql -h localhost -p 5432 -U "$DB_USERNAME" -d parking_db \
  -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
psql -h localhost -p 5432 -U "$DB_USERNAME" -d parking_db \
  -c "SELECT extname FROM pg_extension WHERE extname = 'btree_gist';"
psql -h localhost -p 5432 -U "$DB_USERNAME" -d parking_db \
  -c "SELECT COUNT(*) FROM parking_spots;"
```

Expected results are seven successful migration rows (versions 1 through 7),
one `btree_gist` row, and a parking spot count of 20. Restart with
`./gradlew bootRun` and repeat the queries; the counts must remain unchanged.

## Startup troubleshooting

- **`DB_URL`, `DB_USERNAME`, or `DB_PASSWORD` cannot be resolved:** export all
  mandatory database variables in the same shell that starts Gradle. A `.env`
  file is only a reference and is not loaded automatically.
- **Connection refused:** confirm PostgreSQL is running, the host/port in the
  JDBC URL are correct, and the database exists.
- **Password authentication failed:** verify that `DB_USERNAME` owns or can
  connect to the selected database and that `DB_PASSWORD` matches that role.
- **`JWT_SECRET_BASE64 must be configured` or secret-length error:** generate a
  fresh value with `openssl rand -base64 32`; do not use the committed test key
  for a running application.
- **`btree_gist` is unavailable or permission is denied:** install the server's
  contrib/extension package, make the application role the database owner, or
  have an administrator create only that extension once as described above.
- **Wrong Java or toolchain error:** ensure both `java -version` and the Gradle
  toolchain resolve Java 21.
- **REST preflight is rejected:** add the exact frontend origin, including its
  scheme and port, to `PARKING_REST_ALLOWED_ORIGINS`; wildcard is intentionally
  rejected.
- **WebSocket handshake returns 403:** add the exact origin separately to
  `PARKING_WEBSOCKET_ALLOWED_ORIGINS`, then reconnect with a valid JWT in the
  STOMP `CONNECT` header.
- **SMTP configuration error:** keep `MAIL_ENABLED=false` unless host and from
  address (and both username/password when authenticated) are configured.
- **Tests cannot start:** export all `TEST_DB_*` variables and verify they point
  to a disposable database distinct from the application database.

## Continuous Integration

The backend workflow is defined in `.github/workflows/backend-ci.yml`. It runs
for every pull request targeting `dev-parking`, for new commits added to such a
pull request, and after changes are pushed to `dev-parking`.

GitHub Actions starts an ephemeral PostgreSQL 17 Docker service with a health
check. The service creates an isolated CI user and database, then Flyway applies
the real PostgreSQL migrations before Hibernate validates the mappings. The
container and its data are removed automatically when the job finishes. JWT
secrets are generated for each workflow run and are not stored in the
repository.

The workflow assembles the Spring Boot JAR and runs 22 tagged smoke tests for
application startup and Flyway, registration and login, JWT protection,
vehicle creation and ownership, duplicate handling, parking spot listing, and
availability validation, reservation lifecycle, concurrent hold protection,
reservation entry/exit, concurrent walk-in allocation, and pricing across the
day/night boundary, automatic invoice issuance, and concurrent double-charge
protection. It also covers exact-boundary no-show expiration and one-time email
reminder processing through a fake transport, plus real delivery of hold and
confirmation events to two JWT-authenticated STOMP clients. The check appears
in a pull request as
`Backend CI / Build and Smoke Tests`. If the job fails, its Checks page contains
the logs and a downloadable `backend-smoke-test-reports-*` artifact with the
JUnit/Gradle reports, retained for seven days.

Smoke Tests are the fast critical-path subset used by GitHub on each pull
request. Run them locally against a dedicated PostgreSQL test database with:

```bash
cd parking-backend
./gradlew --no-daemon clean assemble smokeTest
```

Full Tests remain the complete local regression suite, including all existing
unit and integration tests:

```bash
cd parking-backend
./gradlew clean build
```

A successful Smoke CI run confirms only the selected critical scenarios; it
does not mean that the full test suite has been executed.
