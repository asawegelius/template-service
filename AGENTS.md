# Development instructions

- Use the checked-in Gradle wrapper with Java 21. Run `./gradlew test` on Linux/macOS or `./gradlew.bat test` on Windows.
- Keep validation, error responses, and OpenAPI metadata aligned with the HTTP contract.

<!-- TEMPLATE-PERSISTENCE-START -->
## Database search

- Follow the [database search contract](docs/database-search.md) for new list endpoints and update it when the contract changes.
- Keep filters, ordering, projection, and page bounds in SQL. Use typed bind values and explicit fields. Map public sort names to an allowlist of SQL fields and end the order with a unique key.
- Treat each search scope's revision and mutations as one transaction. Test an insert, update, delete, and rollback when you change revision tracking or pagination. Apply the same authorization and base scope to rows, counts, cursors, and change notifications.
- For each search endpoint, verify the SQL statement budget, the number of result rows, and the selected columns. Keep unrequested counts out of the request path.
- Run `./gradlew postgresTest` against the dedicated PostgreSQL test database when modifying queries, indexes, migrations, or revision behavior. Preserve the PostgreSQL CI job and adapt its query-plan and concurrency assertions when replacing the catalog example.
<!-- TEMPLATE-PERSISTENCE-END -->
