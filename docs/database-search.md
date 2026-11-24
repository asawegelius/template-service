# Database search

The persistence flavor includes a catalog example at `GET /api/catalog/items` and a server-sent event stream at `GET /api/catalog/items/changes`. Adapt the schema, filters, projection, and tests to the service's domain. The catalog starts empty. With PostgreSQL configured, insert an example row using SQL.

```sql
INSERT INTO catalog_item (name, status, price_cents, description)
VALUES ('Apple', 'ACTIVE', 150, 'A detail field');
```

```bash
curl 'http://localhost:8080/api/catalog/items?q=a&status=ACTIVE&size=10&count=BOTH'
curl -N 'http://localhost:8080/api/catalog/items/changes'
```

## HTTP contract

| Parameter | Semantics |
| --- | --- |
| `q` | Case-insensitive name prefix from the first character. Leading and trailing whitespace is trimmed. An empty value omits the filter. Up to 100 characters. |
| `status` | `ACTIVE` or `ARCHIVED`. |
| `minPriceCents`, `maxPriceCents` | Inclusive nonnegative integer bounds. All catalog filters combine with AND. |
| `sort`, `direction` | `ID` (default), `NAME`, or `PRICE` with `ASC` (default) or `DESC`. The item ID breaks ties in the same direction. Name ordering uses the database collation. |
| `mode`, `size` | `CURSOR` (default) or `OFFSET`. The default size is 25 and the default maximum is 100. |
| `cursor` | Pass `nextCursor` with the same filters and sort in `CURSOR` mode. Size and count may change. |
| `offset` | Zero-based row offset in `OFFSET` mode. The default maximum is 10,000. |
| `revision` | Expected revision for an offset page or an initial cursor request. Omit it to start with the current revision. Cursor continuations carry their own revision. |
| `count` | `NONE` (default), `TOTAL`, `FILTERED`, or `BOTH`. |

Responses include `items`, the effective `size`, the search `revision`, and `hasNext`. The list projection is `id`, `name`, `status`, and `priceCents`. When more results exist, `nextCursor` is available in either mode. Offset mode also returns `nextOffset` when it is within the configured bound. Use `nextCursor` to continue when the offset bound is reached, and prefer cursor pagination for sequential navigation. For an offset continuation, send the previous `revision` and `nextOffset` with the same filters and sort. An offset request without a revision opens a fresh view and can jump to any allowed offset.

Requested counts are exact 64-bit values. `totalCount` covers the full base scope. `filteredCount` applies the search and typed filters. Both ignore pagination, including the cursor boundary. A request for both counts without filters reuses one aggregate. The API omits unrequested counts and unavailable continuation fields.

Invalid values, incompatible pagination parameters, and cursors from a different filter or sort return HTTP 400 with `bad_request`. A stale revision returns HTTP 409 with `search_changed`. Start a fresh search without a cursor or expected revision after a 409. Treat cursors as opaque, versioned positions for one query shape. Resolve access control and tenant scope independently in every query.

## Transaction and change behavior

A short, read-only transaction gives the revision, list, and requested counts the same snapshot. PostgreSQL uses `REPEATABLE READ`. H2 uses `SERIALIZABLE` to prevent phantoms. Liquibase installs a PostgreSQL statement-level trigger and an H2 row-level trigger for inserts, updates, and deletes. The trigger updates the collection revision in the writer's transaction, so rollbacks leave it unchanged. Each request starts a new snapshot. Revision checks reject continuations after a committed change, including when a write moves a row across a sort boundary. The catalog invalidates the entire collection, including for changes outside the active filters. On PostgreSQL, a write statement that affects zero rows can advance the revision.

The revision row serializes writers for this collection. Keep write transactions short and batch large imports. For write-heavy or multi-tenant services, scope revisions and change subscriptions to the collection or tenant covered by the query. If replacing or truncating the example collection for maintenance, stop serving searches during the operation. In H2, use `DELETE` for ordinary collection clearing so the trigger runs. [PostgreSQL transaction isolation](https://www.postgresql.org/docs/17/transaction-iso.html) · [Trigger semantics](https://www.postgresql.org/docs/17/sql-createtrigger.html)

The SSE stream sends an initial `revision` event and another whenever it observes a later committed revision. Event IDs and data contain the revision. The stream supports invalidating a displayed result set and refetching from the first page. Polling coalesces writes, and reconnecting gets the current revision. Each application instance polls once per interval with active subscribers and borrows a connection only for that query. Connections are bounded and expire so clients can reconnect. The endpoint returns HTTP 503 when all subscriber slots are occupied.

| Setting | Default |
| --- | --- |
| `SEARCH_DEFAULT_PAGE_SIZE` | `25` |
| `SEARCH_MAX_PAGE_SIZE` | `100` |
| `SEARCH_MAX_OFFSET` | `10000` |
| `SEARCH_CHANGES_POLL_INTERVAL` | `PT2S` |
| `SEARCH_CHANGES_TIMEOUT` | `PT2M` |
| `SEARCH_CHANGES_MAX_SUBSCRIBERS` | `128` per application instance |

## SQL and indexing

| Option | Assessment for this template |
| --- | --- |
| jOOQ | Composable typed conditions, explicit projections, and a seek DSL. Spring Boot supplies the dialect and integrates jOOQ with Spring transactions. The sample declares a small typed schema in one repository. Generate schema bindings from the Liquibase-managed schema when the number of tables and projections grows. [Dynamic SQL](https://www.jooq.org/doc/3.19/manual/sql-building/dynamic-sql/) · [Seek](https://www.jooq.org/doc/3.19/manual/sql-building/sql-statements/select-statement/seek-clause/) · [Spring Boot integration](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html#data.sql.jooq) |
| Spring Data JPA Specifications | Fits entity-centric queries that compose reusable predicates. Projections and keyset scrolling are available. PostgreSQL expression indexes, bounded window projections, and separate count plans call for more query-specific integration. [Specifications](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/specifications.html) · [Scrolling](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/query-methods.html#jpa.scroll) |
| MyBatis Dynamic SQL | Composes typed predicates and explicit select lists with mapper-driven result handling. The catalog would need custom seek predicate and projection mapping code. [Select statements](https://mybatis.org/mybatis-dynamic-sql/docs/select.html) |

jOOQ backs catalog reads. Spring Data JPA remains available to services using the persistence flavor. Both use the Boot-managed datasource, pool, and Spring transactions.

- PostgreSQL uses a B-tree expression index on `lower(name) text_pattern_ops` for selective prefix queries. Bind the escaped prefix as a value and keep the wildcard at its end. Very common prefixes can match many rows. Use a database collation with the case mapping your language requires and check the planner with representative distributions. [PostgreSQL operator classes](https://www.postgresql.org/docs/17/indexes-opclass.html)
- The ID, name/ID, and price/ID indexes support the public ordering. The status/ID index supports status filtering with ID order. Design further composite indexes for measured filter and sort combinations.
- The inner SQL query produces up to `size + 1` projected rows for a bounded look-ahead. The outer limit returns at most `size` item rows to JDBC, plus a window value on those rows for `hasNext`. A default search issues one revision query and one page query. The optional count strategies add up to two scalar aggregates.
- PostgreSQL handles buffer and index caching. Search responses use `Cache-Control: no-store`. Use actual query plans, buffers, and pool metrics to tune each deployment. Exact counts may scan substantial data. Request them when the client needs them.

## Verification

Run `./gradlew test` for the H2 contract and the template's unit and smoke tests. Run `./gradlew postgresTest` with Java 21 and a **dedicated, disposable PostgreSQL 17 database**. The suite replaces catalog data. Set `SEARCH_TEST_DB_URL`, `SEARCH_TEST_DB_USERNAME`, and `SEARCH_TEST_DB_PASSWORD`. The PostgreSQL CI job provisions this database automatically. Use `gradlew.bat` on Windows.

The tests cover filters, cursor/offset pagination, tie-breaking, counts, stale continuations, writes committed between SQL statements, rollbacks, shared SSE polling, result columns, actual JDBC row counts, and SQL statement budgets. The PostgreSQL suite also checks execution plans for indexed prefix and keyset reads and runs 144 requests across 12 simultaneous client sessions on a 100,000-row fixture. Its 3-second p95 threshold detects major regressions in CI. Establish workload-specific thresholds for the deployed service. Update both the tests and the [development instructions](../AGENTS.md) when changing the template contract.
