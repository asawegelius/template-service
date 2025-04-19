# Template Service

This is a reusable template for modules and microservices (Spring Boot 3, Java 21).

## Quick Start

1. Clone this repo
2. Replace package name `com.template.service` with your desired package (e.g., `com.calendarchat.identity`).
3. Adjust constants in `TemplateServiceConfig`:
    - `SERVICE_NAME`
    - `SWAGGER_TITLE`
    - `SWAGGER_DESCRIPTION`
    - `SWAGGER_VERSION`
4. Update the `application.yaml` if needed.
5. Done! 🚀

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