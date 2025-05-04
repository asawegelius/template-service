# Template Service

This is a reusable template for modules and microservices (Spring Boot 3, Java 21).

## Creating a New Project from This Template

To create a new project based on this template:

### 1. Clone this repository into a temporary folder:

``` bash
git clone https://github.com/your-organization/template-service.git temp-template-service
cd temp-template-service
```

### 2. Run the bootstrap script to clean the project and initialize it with your new project name:

**On Linux/macOS/WSL:**
``` bash
./bootstrap-new-project.sh "Your New Project Name"
```

**On Windows PowerShell:**
``` bash
./bootstrap-new-project.ps1 "Your New Project Name"
```
### What the bootstrap script does:

- Removes the old Git history (.git folder)
- Deletes the old README.md
- Creates a fresh README.md with your project name as the title
- Initializes a fresh Git repository
- Makes the first clean commit

### Notes for Non-Git Users

If you do not plan to use Git for your project,
you can optionally remove the `.git/` folder after running the bootstrap script.

**On Linux/macOS/WSL:**
``` bash
rm -rf .git
```

**On Windows PowerShell:**
``` powershell
Remove-Item -Recurse -Force .git
```

The `.git` folder is harmless if left in place,
but removing it can help keep your project clean if you're not using any version control.

### 3. Create a new GitHub repository for your new project.

### 4. Connect your project to GitHub:
``` bash
git branch -M main
git remote add origin git@github.com:your-organization/your-new-project-name.git
git push -u origin main
```

### 5. Adapt your new project


1. Replace package name `com.template.service` with your desired package (e.g., `com.calendarchat.identity`).
2. Adjust constants in `TemplateServiceConfig`:
    - `SERVICE_NAME`
    - `SWAGGER_TITLE`
    - `SWAGGER_DESCRIPTION`
    - `SWAGGER_VERSION`
3. Update the `application.yaml` if needed.
4. Done! 🚀

## Features

- Spring Boot 3.4
- Java 21 ready
- H2 Database default setup
- Spring Data JPA
- Actuator endpoints for health, info, metrics
- Swagger (OpenAPI) auto-generated documentation
- Constructor-injected, test-friendly logger ready
- Global exception handler prepared
- Spock unit test

## To configure Prometheus / Grafana

Actuator endpoints already exposed! Add Prometheus dependency if you need more metrics.

---

## License
MIT