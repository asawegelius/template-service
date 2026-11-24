package com.template.service.search.catalog

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.springframework.test.context.TestPropertySource
import spock.lang.Tag

@Tag('postgres')
@TestPropertySource(properties = [
        'spring.datasource.url=${SEARCH_TEST_DB_URL:jdbc:postgresql://localhost:5432/template_search}',
        'spring.datasource.driver-class-name=org.postgresql.Driver',
        'spring.datasource.username=${SEARCH_TEST_DB_USERNAME:template_test}',
        'spring.datasource.password=${SEARCH_TEST_DB_PASSWORD:template_test}'
])
class PostgresItemSearchSpec extends ItemSearchContract {

    def 'uses PostgreSQL indexes and serves concurrent two-page sessions over 100,000 rows'() {
        given:
        jdbc.update('DELETE FROM catalog_item')
        jdbc.update("""
            INSERT INTO catalog_item (id, name, status, price_cents, description)
            SELECT n, CASE WHEN n % 1000 = 0 THEN 'Z-' || n ELSE 'Item-' || n END,
                   CASE WHEN n % 3 = 0 THEN 'ARCHIVED' ELSE 'ACTIVE' END,
                   n % 10000, repeat('x', 512)
            FROM generate_series(1, 100000) AS n
        """)
        jdbc.execute('ANALYZE catalog_item')
        def trace = sqlTrace.startTrace()

        when:
        def prefix = search([q: 'z', size: '20', count: 'BOTH'])
        def prefixPlan = explain(trace.statements.find { it.sql.contains('bounded_page') }.inlined())
        trace.statements.clear()
        def parameters = [sort: 'PRICE', size: '20']
        def first = search(parameters)
        def second = search(parameters + [cursor: first.nextCursor])
        def seekPlan = explain(trace.statements.findAll { it.sql.contains('bounded_page') }.last().inlined())
        sqlTrace.stopTrace()

        then:
        prefix.totalCount == 100_000
        prefix.filteredCount == 100
        prefix.items.size() == 20
        prefixPlan.contains('catalog_item_prefix_idx')
        second.items.size() == 20
        seekPlan.contains('catalog_item_price_id_idx')
        seekPlan.contains('Index Cond')

        when:
        int sessions = 12
        def workers = Executors.newFixedThreadPool(sessions)
        def start = new CountDownLatch(1)
        def futures = (1..sessions).collect { session ->
            workers.submit({ ->
                assert start.await(10, TimeUnit.SECONDS)
                def filters = session % 2 == 0 ? [q: 'z', status: 'ACTIVE'] : [sort: 'PRICE', direction: 'DESC']
                (1..6).collectMany {
                    long begin = System.nanoTime()
                    def leading = search(filters + [size: '20'])
                    long leadingMs = elapsedMs(begin)
                    begin = System.nanoTime()
                    def trailing = search(filters + [size: '20', cursor: leading.nextCursor])
                    long trailingMs = elapsedMs(begin)
                    assert leading.items.size() == 20 && trailing.items.size() == 20
                    assert leading.revision == trailing.revision
                    assert !leading.items*.id.intersect(trailing.items*.id)
                    [leadingMs, trailingMs]
                }
            } as Callable<List<Long>>)
        }
        start.countDown()
        def times = futures.collectMany { it.get(30, TimeUnit.SECONDS) }.sort()
        long p95 = times[(Math.ceil(times.size() * 0.95) as int) - 1]
        println "PostgreSQL search latency over ${times.size()} requests and ${sessions} sessions, p95=${p95}ms"

        then:
        times.size() == 144
        p95 < 3000

        cleanup:
        workers?.shutdownNow()
    }

    private String explain(String sql) {
        jdbc.queryForList('EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ' + sql, String).join('\n')
    }

    private static long elapsedMs(long begin) {
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin)
    }
}
