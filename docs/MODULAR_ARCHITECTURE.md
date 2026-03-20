# Modular Architecture

## Goal

Organize the backend as a modular monolith using `package-by-feature` and explicit internal boundaries.

This project should prefer:

- feature-first modules such as `auth`, `notifications`, `mail`, `realtime`, `security`
- small internal layers inside each feature
- interfaces only for real boundaries, not for every class

## Internal Structure Per Feature

Use this shape by default:

```text
feature/
  api/
    controller/
    dto/
  application/
  domain/
    model/
  infrastructure/
    config/
    persistence/
    security/
    websocket/
    smtp/
```

Not every feature needs every sub-package. Add only the ones that are useful.

## Responsibilities

### `api`

- HTTP entrypoints
- request/response DTOs
- transport mapping
- no business orchestration beyond controller concerns

### `application`

- use-case orchestration
- transaction boundaries
- coordinates domain objects and infrastructure dependencies
- examples: authentication flow, password reset flow, admin notification dispatch

### `domain`

- business model
- enums and state that belong to the feature
- pure business rules when possible

### `infrastructure`

- Spring Security adapters
- JPA repositories
- SMTP implementations
- WebSocket adapters
- framework-specific configuration

## Interface Rule

Do not create interfaces for every service by default.

Create an interface when:

- the dependency is a real boundary
- multiple implementations are expected
- tests benefit from replacing the dependency at the boundary

Good candidates:

- mail sender
- external publisher
- storage client
- clock or token generator when needed

Usually not needed:

- one-off application services with a single implementation
- internal coordinators with no alternative implementation

## Current Direction

The `auth` module has been migrated to this structure first:

```text
auth/
  api/
    controller/
    dto/
  application/
  domain/
    model/
  infrastructure/
    config/
    persistence/
    security/
```

This module is the reference for the next migrations.

## Next Recommended Migrations

1. `notifications`
2. `mail`
3. `realtime`

When migrating a feature:

1. move files physically first
2. update package names and imports
3. keep behavior unchanged
4. compile after every feature migration
