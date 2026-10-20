# Parking Management System

Full-stack paid parking management system.

## Tech Stack

- Backend: Java 21, Spring Boot, Gradle
- Frontend: React, TypeScript (planned)
- Database: PostgreSQL, Flyway
- Security: Spring Security, OAuth2 Resource Server, JWT (HS256)
- Infrastructure: Docker Compose (planned)

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
enabled. The frontend is not implemented yet.

## Planned Features

- Driver profiles and vehicles
- Parking spot management
- Payments and payment history
- WebSocket real-time updates

## Local Development

### Prerequisites

- Java 21
- PostgreSQL running on `localhost:5432`
- A database and user created for local development

Example PostgreSQL setup (run as a PostgreSQL administrator):

```sql
CREATE USER parking_user WITH PASSWORD 'choose_a_local_password';
CREATE DATABASE parking_db OWNER parking_user;
CREATE DATABASE parking_test_db OWNER parking_user;
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
export MAIL_ENABLED='false'

export TEST_DB_URL='jdbc:postgresql://localhost:5432/parking_test_db'
export TEST_DB_USERNAME='parking_user'
export TEST_DB_PASSWORD='choose_a_local_password'
```

`DB_URL` and `DB_USERNAME` have local-development defaults. `DB_PASSWORD` is
required and intentionally has no default value. Integration tests use the
separate `TEST_DB_*` connection and never point to `parking_db` by default.
`JWT_SECRET_BASE64` must decode to at least 32 random bytes. Keep the same local
secret across restarts if previously issued access tokens should remain valid.
The committed test profile contains only an isolated test key; production and
local runtime configuration never fall back to it.

Pricing defaults to `Asia/Bishkek`, a half-open day period of `[08:00, 20:00)`,
`2.00 KGS` per day minute, and `1.00 KGS` per night minute. The five
`PARKING_*` variables above override the zone, boundaries, and rates. Rates
must be zero or positive, and the day start must be earlier than the day end.
Reservation automation runs every 30 seconds by default. Email is disabled by
default, so cleanup, registration, reservations, the barrier, and invoices run
without an SMTP server.

### Build and run

```bash
cd parking-backend
./gradlew clean build
./gradlew bootRun
```

Flyway applies migrations from `src/main/resources/db/migration` before
Hibernate validates the schema. Hibernate does not create or update tables.
`./gradlew clean build` requires the separate PostgreSQL test database because
the persistence tests verify PostgreSQL-specific constraints and do not use H2.

To verify the initial data:

```bash
psql -h localhost -p 5432 -U "$DB_USERNAME" -d parking_db \
  -c "SELECT code, active FROM parking_spots ORDER BY code;"
```

The first migrations create only the `parking_spots` table and add spots
`A1`-`A10` and `B1`-`B10`. Current occupancy is deliberately not stored on a
parking spot; it will be derived from reservations and active visits in later
stages.

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

Exit does not require or simulate payment. Live WebSocket updates and control
of physical barrier hardware remain outside the current stage.

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

The workflow assembles the Spring Boot JAR and runs 21 tagged smoke tests for
application startup and Flyway, registration and login, JWT protection,
vehicle creation and ownership, duplicate handling, parking spot listing, and
availability validation, reservation lifecycle, concurrent hold protection,
reservation entry/exit, concurrent walk-in allocation, and pricing across the
day/night boundary, automatic invoice issuance, and concurrent double-charge
protection. It also covers exact-boundary no-show expiration and one-time email
reminder processing through a fake transport. The check appears in a pull
request as
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
