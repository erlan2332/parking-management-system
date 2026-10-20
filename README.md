# Parking Management System

Full-stack paid parking management system.

## Tech Stack

- Backend: Java 21, Spring Boot, Gradle
- Frontend: React, TypeScript (planned)
- Database: PostgreSQL, Flyway
- Infrastructure: Docker Compose (planned)

## Project Structure

- parking-backend/ - Spring Boot backend
- parking-frontend/ - React frontend (planned)

## Current Status

The Spring Boot backend is configured to use a local PostgreSQL database.
Flyway creates the `parking_spots` table and seeds 20 parking spots on startup.
Frontend and business logic are not implemented yet.

## Planned Features

- Driver profiles and vehicles
- Parking spot management
- Parking reservations
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
```

### Environment variables

Copy `.env.example` to `.env` if you want a local reference file, but do not
commit `.env`. Spring Boot does not load `.env` files automatically. Export the
variables in the shell before starting the application:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/parking_db'
export DB_USERNAME='parking_user'
export DB_PASSWORD='choose_a_local_password'
```

`DB_URL` and `DB_USERNAME` have local-development defaults. `DB_PASSWORD` is
required and intentionally has no default value.

### Build and run

```bash
cd parking-backend
./gradlew clean build
./gradlew bootRun
```

Flyway applies migrations from `src/main/resources/db/migration` before
Hibernate validates the schema. Hibernate does not create or update tables.

To verify the initial data:

```bash
psql -h localhost -p 5432 -U "$DB_USERNAME" -d parking_db \
  -c "SELECT code, active FROM parking_spots ORDER BY code;"
```

The first migrations create only the `parking_spots` table and add spots
`A1`-`A10` and `B1`-`B10`. Current occupancy is deliberately not stored on a
parking spot; it will be derived from reservations and active visits in later
stages.
