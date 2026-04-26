# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [1.2.0] - 2026-04-26

### Added
- Cross-platform GitHub Actions test matrix for Ubuntu and Windows
- Machine-readable `template-origin.properties` output from the bootstrap scripts
- Optional bootstrap arguments for service slug and base package
- Automatic source-tree and application-class retargeting during bootstrap
- Structured logging starter using Spring Boot's built-in ECS console format
- Request correlation ID filter that propagates `X-Correlation-Id` and enriches MDC with request context
- Error-code starter that keeps API error responses and structured logs aligned
- Bootstrap flavor support for `persistence` and `generic`

### Changed
- Bootstrap scripts now write both a human-readable README note and a machine-readable provenance file
- The template codebase uses more generic application class names so generated projects need less manual cleanup

## [1.1.0] - 2026-04-26

### Added
- GitHub Actions build workflow
- Dependabot configuration
- Liquibase starter changelog
- PostgreSQL runtime driver alongside the in-memory H2 default
- Validation starter dependency
- Basic Spock smoke test
- Template versioning notes in the README
- Bootstrap scripts now record the source template tag or commit in the generated `README.md`

### Changed
- Updated to Spring Boot 3.5.11
- Updated springdoc to 2.8.17
- Updated Spock to 2.4 for Groovy 4
- Updated the Gradle wrapper to 8.14.4
- Switched the template defaults to Liquibase-managed persistence with safer JPA and Actuator settings
- Hardened the default exception handling so client responses use safe error payloads
- Cleaned the bootstrap scripts and template documentation
- Tracked the Gradle wrapper jar and executable wrapper script so CI works reliably

### Removed
- Tracked Gradle cache files from the repository

### Fixed
- Stopped returning raw server exception messages to API clients by default

---

## [1.0.0] - 2024-04-22

### Added
- First release of the Template Service project
- H2 database default setup
- Spring Data JPA
- Actuator health/info/metrics endpoints
- Swagger auto-generated documentation
- Global exception handling
- Spock unit testing framework
