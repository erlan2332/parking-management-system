# AI-Assisted Development Prompts

## Task 11-R — Backend architecture refactoring

The implementation was guided by a constrained refactoring brief: normalize
feature package structure, establish clear Controller/Service/Repository/
Entity/Mapper/DTO responsibilities, use Lombok only where safe, simplify the
large barrier and reservation services, remove proven dead persistence paths,
and reduce test duplication.

The prompt explicitly required preserving public REST and WebSocket contracts,
JWT behavior, PostgreSQL constraints, Flyway V1-V7, transaction boundaries,
lock ordering, reservation timing, pricing and rounding, historical invoices,
and the existing GitHub Actions smoke suite. It also required a full build,
concurrency-sensitive regression tests, a focused commit history, and a pull
request without merge or auto-merge.

AI-generated changes were validated against the existing source and tests,
compiled after each architectural group, and verified with the complete
PostgreSQL-backed test suite plus `smokeTest`. Architectural suggestions that
would add ceremonial interfaces, CQRS, new migrations, or public contract
changes were intentionally excluded.
