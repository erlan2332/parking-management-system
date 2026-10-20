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
walk-in entry, physical exit, and visit history. Completed visits can be priced
with configurable day and night rates. The frontend is not implemented yet.

## Planned Features

- Driver profiles and vehicles
- Parking spot management
- Invoice and payment history
- WebSocket real-time updates
- Email notifications

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
voluntarily return a held spot. Automatic no-show expiration, tariffs,
payments, WebSocket updates, and a frontend remain outside the current stage;
clients receive current state when they poll the API.

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

`POST /api/barrier/exit` completes the active visit using server time. A linked
reservation changes from `CHECKED_IN` to `COMPLETED`. `GET /api/visits/my`
returns active and completed visits, newest first, with actual duration for
completed visits. Active visits make a spot `OCCUPIED` for both its current
state and all future availability requests until physical exit.

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

This stage does not create invoices, require payment for exit, expire no-show
reservations automatically, send live WebSocket updates, or control physical
barrier hardware.

### Parking pricing

`GET /api/visits/{id}/cost` calculates a detailed price only for a completed
visit owned by the authenticated driver. Active visits return a conflict
because their final duration is unknown; missing and foreign visits share the
same not-found response. The calculation uses the persisted actual entry and
exit instants for both reservation and walk-in visits.

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

Stage 07 calculates with the current configuration and does not persist a
charge. Recalculating an old visit after a rate change can therefore produce a
different result. A later invoice stage must snapshot the applied rates and
amount; no tariff versioning, invoice, or payment state is introduced here.

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

The workflow assembles the Spring Boot JAR and runs 17 tagged smoke tests for
application startup and Flyway, registration and login, JWT protection,
vehicle creation and ownership, duplicate handling, parking spot listing, and
availability validation, reservation lifecycle, concurrent hold protection,
reservation entry/exit, concurrent walk-in allocation, and pricing across the
day/night boundary. The check appears in a pull request as
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
