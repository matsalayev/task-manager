# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a task management system built in Scala that allows companies to assign tasks to employees and monitor completion time. The system includes Telegram bots for corporate and employee interaction, and is designed to be extended with web and mobile interfaces.

## Architecture

The codebase follows a clean architecture pattern with clear module separation:

### Core Modules Structure
- **endpoints/**: Main application modules organized in dependency layers
  - `00-domain`: Core business domain models
  - `01-repos`: Data access layer with database repositories
  - `02-core`: Business logic and services
  - `03-api`: HTTP API routes and controllers
  - `03-jobs`: Background job processing
  - `04-server`: HTTP4s server configuration
  - `05-runner`: Application entry point and configuration

- **supports/**: Shared infrastructure components
  - `database`: PostgreSQL/Skunk database support
  - `redis`: Redis caching support
  - `logback`: Logging configuration
  - `services`: Common service utilities
  - `sttp`: HTTP client support
  - `jobs`: Cron job scheduling support

- **integrations/**: External service integrations
  - `telegram`: Telegram Bot API integration
  - `aws`: AWS S3 integration

- **common/**: Shared domain types and utilities
- **test-tools/**: Testing utilities and helpers

## Development Commands

### Building and Running
```bash
# Compile the project
sbt compile

# Run the server (preferred method)
sbt runServer

# Alternative run command
sbt "endpoints-runner/run"

# Run tests
sbt test

# Format code (Scalafmt)
sbt scalafmt

# Check formatting
sbt scalafmtCheck
```

### Docker Support
- Docker configuration is available for the runner service
- Redis is configured via Docker Compose at `.build/redis/docker-compose.yml`
- Start Redis: `docker-compose -f .build/redis/docker-compose.yml up`

## Key Technologies

- **Scala 2.13.11** with Cats Effect for functional programming
- **HTTP4s** for REST API server
- **Skunk** for PostgreSQL database access
- **Circe** for JSON serialization
- **Redis4Cats** for caching
- **Flyway** for database migrations
- **OpenPDF** for PDF generation
- **STTP** for HTTP client requests
- **FS2** for streaming and reactive programming

## Database and Configuration

- PostgreSQL database with Flyway migrations
- Configuration files in `endpoints/05-runner/src/main/resources/`
- Redis for caching and session management
- Environment-specific configs: `local.conf`, `reference.conf`

## Code Style and Standards

- Scalafmt configuration at `.scalafmt.conf` with 100 character line limit
- Import organization: javax, scala, external libraries, then tm.* (project packages)
- Uses refined types for validation and newtype pattern for type safety
- Functional programming patterns with Cats Effect and tagless final

## Testing

- Weaver test framework for unit testing
- Test containers for integration testing with PostgreSQL
- Test utilities in `test-tools/` module

## Key Patterns

- **Tagless Final**: Services defined as traits with type parameters
- **Reader Pattern**: Configuration and dependencies injected via Cats Effect
- **Error Handling**: Custom domain errors with Either and validated types
- **Database**: Repository pattern with Skunk for type-safe SQL
- **HTTP Routes**: HTTP4s DSL with automatic JSON serialization via Circe