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
through a secured REST API. The frontend is not implemented yet.

## Planned Features

- Driver profiles and vehicles
- Parking spot management
- Barrier entry and exit simulator
- Day and night tariffs
- Visit and invoice history
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
`HELD`, `RESERVED`, or `UNAVAILABLE`. The regular spot list reflects only the
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
voluntarily return a held spot. This stage does not implement physical entry or
exit, no-show expiration of confirmed reservations, tariffs, payments,
WebSocket updates, or a frontend; clients receive current state when they poll
the API.

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

The workflow assembles the Spring Boot JAR and runs 14 tagged smoke tests for
application startup and Flyway, registration and login, JWT protection,
vehicle creation and ownership, duplicate handling, parking spot listing, and
availability validation, plus reservation lifecycle and concurrent hold
protection. The check appears in a pull request as
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
