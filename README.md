# Template Service

Reusable Spring Boot service template for Java 21 projects.

## What This Template Includes

- Spring Boot 3.5
- Java 21 toolchain
- Spring Web, Validation, Actuator, and Spring Data JPA
- Liquibase with an empty starter changelog
- OpenAPI / Swagger UI
- H2 for local development and PostgreSQL driver for service deployments
- Spock-based test setup
- Bootstrap scripts for creating a fresh project from the template
- GitHub Actions build workflow and Dependabot configuration

## Creating a New Project from This Template

1. Clone this repository into a temporary folder:

```bash
git clone https://github.com/your-organization/template-service.git temp-template-service
cd temp-template-service
```

2. Run the bootstrap script:

Linux/macOS/WSL:

```bash
./bootstrap-new-project.sh "Your New Project Name"
```

Windows PowerShell:

```powershell
./bootstrap-new-project.ps1 "Your New Project Name"
```

The bootstrap script:

- removes the old Git history
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

1. Replace package name `com.template.service` with your real package.
2. Update values in [TemplateServiceConfig.java](src/main/java/com/template/service/config/TemplateServiceConfig.java).
3. Review [application.yaml](src/main/resources/application.yaml) for environment-specific defaults.
4. Replace the empty Liquibase starter changelog with your actual schema plan.
5. Add or remove dependencies based on whether the new service really needs persistence.

## Default Behavior Notes

- JPA schema auto-update is disabled by default.
- Liquibase points to [master.yaml](src/main/resources/db/changelog/master.yaml).
- Global exception handling returns safe default error messages instead of raw exception text.
- The template is meant to be a solid starting point, not a final production policy.

## License

MIT
