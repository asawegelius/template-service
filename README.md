# Template Service

Reusable Spring Boot service template for Java 21 projects.

## What This Template Includes

- Spring Boot 3.5
- Java 21 toolchain
- Spring Web, Validation, Actuator, and Spring Data JPA
- Liquibase with an empty starter changelog
- OpenAPI / Swagger UI
- H2 for local development and PostgreSQL driver for service deployments
- Structured JSON logging to stdout using Spring Boot's built-in ECS format
- Request correlation ID starter with `X-Correlation-Id` propagation
- Spock-based test setup
- Bootstrap support for both `persistence` and `generic` service flavors
- Bootstrap scripts for creating a fresh project from the template
- GitHub Actions build workflow and Dependabot configuration

## Template Versioning

- This template should be released with Git tags using `vMAJOR.MINOR.PATCH`.
- [CHANGELOG.md](CHANGELOG.md) is the human-readable record of what changed between template releases.
- The bootstrap scripts preserve a human-readable summary in the generated `README.md`.
- The bootstrap scripts also generate `template-origin.properties`, which gives a machine-readable record of the source template tag or commit.
- Before recommending the template for wider reuse, cut a tag from a merged and stable branch.

## Creating a New Project from This Template

1. Clone this repository into a temporary folder:

```bash
git clone https://github.com/your-organization/template-service.git temp-template-service
cd temp-template-service
```

2. Run the bootstrap script:

Linux/macOS/WSL:

```bash
./bootstrap-new-project.sh "Your New Project Name" "your-service-slug" "com.example.your.service" "persistence"
```

Windows PowerShell:

```powershell
./bootstrap-new-project.ps1 "Your New Project Name" -ServiceSlug "your-service-slug" -BasePackage "com.example.your.service" -TemplateFlavor "persistence"
```

The bootstrap script:

- supports two bootstrap flavors:
  - `persistence` keeps JPA, Liquibase, datasource config, and the starter changelog
  - `generic` removes the persistence-specific dependencies and config during bootstrap
- removes the old Git history
- can derive a sensible service slug and base package if you do not pass them explicitly
- moves the Java and test source trees to the chosen base package
- renames the application entrypoint and smoke-test classes to match the chosen service slug
- records template provenance in `template-origin.properties`
- records the source template tag or commit in the new `README.md`
- replaces the README with a new project title
- initializes a fresh Git repository
- creates the first clean commit

3. Create a new GitHub repository for the project.

4. Connect the new local repository to GitHub:

```bash
git branch -M main
git remote add origin git@github.com:your-organization/your-new-project-name.git
git push -u origin main
```

## First Things To Change

1. Choose the right flavor when you bootstrap:
   - `persistence` for services with database ownership
   - `generic` for simpler services without JPA or Liquibase
2. Prefer passing `-ServiceSlug` / `-BasePackage` or the equivalent bash arguments when you bootstrap the project.
3. Review [ApplicationMetadata.java](src/main/java/com/template/service/config/ApplicationMetadata.java) for service-specific values.
4. Review [application.yaml](src/main/resources/application.yaml) for environment-specific defaults.
5. If you chose the persistence flavor, replace the empty Liquibase starter changelog with your actual schema plan.

## Default Behavior Notes

- JPA schema auto-update is disabled by default.
- Liquibase points to [master.yaml](src/main/resources/db/changelog/master.yaml).
- Global exception handling returns safe default error messages instead of raw exception text.
- Console logs use Spring Boot structured logging in ECS JSON format.
- Incoming `X-Correlation-Id` is reused when present, otherwise one is generated and returned in the response.
- Request-scoped `correlation_id`, `http_method`, and `request_path` are added to MDC so they appear in structured logs.
- Error responses include stable `error_code` and `correlation_id` fields, and the same `error_code` is written to logs through MDC.
- The template is meant to be a solid starting point, not a final production policy.

## License

MIT
