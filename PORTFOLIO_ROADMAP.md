# Ecommerce Portfolio Roadmap (Advanced Java)

## Stage 1: Core Commerce Foundation
- Build domain model: Product, Category, InventoryItem, Customer, Cart, Order, OrderLine.
- Implement layered architecture: controller, service, repository, DTO mapper.
- Add validation and global exception handling.
- Deliverables:
  - CRUD APIs for products and categories.
  - Cart add/update/remove endpoints.
  - Order checkout flow with transactional service.

## Stage 2: Security and Identity
- Add role-based access control with Spring Security.
- Define roles: ADMIN, STAFF, CUSTOMER.
- Protect admin catalog operations and order admin operations.
- Deliverables:
  - Login flow and session/JWT strategy.
  - Method-level security with tests.

## Stage 3: Data Integrity and Migrations
- Use Flyway for schema versioning and reproducible environments.
- Add optimistic locking and transactional boundaries.
- Deliverables:
  - Versioned SQL migration scripts.
  - Rollback strategy and migration verification checklist.

## Stage 4: Testing Maturity
- Unit tests for service logic and validators.
- Integration tests with PostgreSQL Testcontainers.
- Contract tests for key API endpoints.
- Deliverables:
  - Test pyramid documentation.
  - CI test matrix and coverage report.

## Stage 5: Performance and Reliability
- Introduce caching for catalog and lookup data.
- Add pagination/search and remove N+1 query issues.
- Measure startup and endpoint latency regressions.
- Deliverables:
  - Baseline benchmark report.
  - Performance budget and alert thresholds.

## Stage 6: Production Readiness
- Add actuator health/readiness/liveness checks.
- Add structured logging and metrics dashboards.
- Containerize with Docker Compose for local parity.
- Deliverables:
  - Runbook (startup, deploy, rollback).
  - Demo script for interview walkthrough.

## Suggested Branch Strategy
- main: stable showcase branch.
- feature/stage-x-* branches: isolated stage implementations.
- docs/benchmark: benchmark snapshots and tuning notes.

## Suggested Weekly Cadence
- Week 1: Stage 1 + API docs.
- Week 2: Stage 2 + authorization tests.
- Week 3: Stage 3 + migration quality gates.
- Week 4: Stage 4 + CI hardening.
- Week 5: Stage 5 + benchmark improvements.
- Week 6: Stage 6 + polished portfolio demo.
