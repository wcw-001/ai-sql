# Repository Guidelines

## Project Structure & Module Organization
This repository is a Maven-based Spring Boot service for natural-language-to-SQL querying.
- Main code: `src/main/java/com/wcw/aisql`
- Feature packages: `query/controller`, `query/service`, `query/security`, `query/config`, `query/model`, `query/common`
- App config and resources: `src/main/resources` (notably `application.yml`)
- Tests: `src/test/java/com/wcw/aisql` mirroring production package layout
- Build output: `target/` (generated; do not edit manually)

When adding modules, keep package boundaries clear: controllers for HTTP APIs, services for business logic, and security/config separated from domain logic.

## Build, Test, and Development Commands
Use Maven Wrapper to ensure consistent tooling:
- `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`): run locally on port `8080`
- `./mvnw test`: run all JUnit 5 tests
- `./mvnw clean package`: build executable jar and run checks
- `./mvnw clean package -DskipTests`: build quickly without tests (CI should still run full tests)

## Coding Style & Naming Conventions
- Java 17+ style with 4-space indentation; UTF-8 source files.
- Class names: `PascalCase`; methods/fields: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep REST endpoints in `*Controller`, business logic in `*Service`, and DTO/model objects in `model`.
- Prefer constructor injection for new components; keep methods focused and small.

## Testing Guidelines
- Frameworks: Spring Boot Test + JUnit 5 (`spring-boot-starter-test`).
- Test classes end with `Test` and mirror source packages.
- Prefer fast unit tests; use `@SpringBootTest` only when context wiring is required.
- Add regression tests for bug fixes and API behavior changes before merging.

## Commit & Pull Request Guidelines
No Git history is available in this workspace snapshot, so follow Conventional Commits by default:
- `feat: add schema cache refresh endpoint`
- `fix: prevent unsafe SQL keyword execution`

PRs should include:
- Clear summary of behavior changes
- Linked issue/task ID
- Test evidence (command output or screenshots for API/UI changes)
- Config-impact notes (DB/Redis/AI settings)

## Security & Configuration Tips
- Never commit real secrets (API keys, DB passwords) in `application.yml`.
- Use environment variables or profile-specific files (for example, `application-local.yml`) for local credentials.
- Review SQL validation and permission checks whenever query-generation logic changes.
