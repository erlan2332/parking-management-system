# Architecture Decisions

## Feature-first packages with explicit responsibilities

Backend classes live in a business feature first and a technical role second.
This keeps related behavior close while making controller, DTO, mapper,
service, repository, entity, model, and configuration boundaries visible.
Packages are not created merely to satisfy a template.

## DTO and mapper boundary

HTTP DTOs are persistence-agnostic. Dedicated Spring mappers translate domain
results into response contracts; they do not query repositories or make
business decisions. Services can therefore evolve internally without moving
JPA knowledge into the public contract.

## Selective Lombok usage

`@RequiredArgsConstructor`, `@Getter`, protected `@NoArgsConstructor`, and
simple property accessors replace boilerplate where generation is transparent.
Validation-heavy constructors, notably JWT and pricing configuration, remain
manual. Entities do not use broad `@Data` semantics because identity,
mutability, and status transitions are deliberate.

## Services and interfaces

Services expose concrete use-case implementations by default. An interface is
appropriate only for multiple implementations, an external adapter boundary,
or demonstrated substitution in tests or runtime configuration. A one-to-one
interface is not added as ceremonial abstraction.

## Transaction and database guarantees

`BarrierService` remains the transaction boundary for entry and exit. Its
entry and exit collaborators execute inside that transaction, preserving the
spot-to-vehicle-to-related-record lock order, no-show persistence, concurrent
entry protection, and atomic exit plus invoice creation. Reservation cleanup
is shared through a focused component but request-time cleanup remains inside
the caller transaction.

PostgreSQL exclusion constraints, partial unique indexes, foreign-key
restrictions, and pessimistic locking remain authoritative for races. They are
not replaced by Java pre-checks. Flyway V1-V7 are immutable.

## Stable public contracts

The refactoring intentionally changes no REST route, JSON field or type, HTTP
status, JWT contract, reservation deadline, tariff, rounding rule, invoice
snapshot, WebSocket endpoint, subscription topic, or event reason. Regression
coverage is provided by the complete PostgreSQL suite and the existing tagged
smoke suite.
