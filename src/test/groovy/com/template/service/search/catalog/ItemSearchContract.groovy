package com.template.service.search.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

@SpringBootTest(properties = [
        'app.search.default-page-size=3',
        'app.search.max-page-size=20',
        'app.search.max-offset=8',
        'app.search.changes.poll-interval=PT1H',
        'app.search.changes.timeout=PT2H',
        'app.search.changes.max-subscribers=2'
])
@AutoConfigureMockMvc
@Import(SearchSqlTrace)
abstract class ItemSearchContract extends Specification {

    static final String ITEMS = '/api/catalog/items'

    @Autowired MockMvc mvc
    @Autowired JdbcTemplate jdbc
    @Autowired TransactionTemplate transactions
    @Autowired ObjectMapper mapper
    @Autowired SearchSqlTrace sqlTrace
    @Autowired CatalogChangeFeed changeFeed

    def setup() {
        jdbc.update('DELETE FROM catalog_item')
        transactions.executeWithoutResult {
            insert(1, 'Alpha', 'ACTIVE', 150)
            insert(2, 'Alpine', 'ACTIVE', 200)
            insert(3, 'Amber', 'ARCHIVED', 150)
            insert(4, 'Beta', 'ACTIVE', 200)
            insert(5, 'Aardvark', 'ACTIVE', 50)
            insert(6, 'A%literal', 'ACTIVE', 250)
            insert(7, 'A_literal', 'ACTIVE', 300)
            insert(8, 'A!literal', 'ACTIVE', 350)
        }
    }

    def cleanup() {
        sqlTrace.stopTrace()
    }

    def 'combines typed filters from the first character and treats punctuation literally'() {
        expect:
        search([q: 'a', status: 'ACTIVE', minPriceCents: '150', maxPriceCents: '250',
                size: '20']).items*.id == [1, 2, 6]
        search([q: 'a%']).items*.id == [6]
        search([q: 'a_']).items*.id == [7]
        search([q: 'a!']).items*.id == [8]
        search([q: '%']).items == []
    }

    def 'publishes the shared search parameters in OpenAPI'() {
        when:
        def response = mvc.perform(get('/v3/api-docs')).andReturn().response
        def paths = json(response).paths
        def parameters = paths[ITEMS]['get'].parameters.collectEntries { [(it.name): it.schema] }

        then:
        response.status == 200
        parameters.keySet().containsAll(['q', 'status', 'minPriceCents', 'maxPriceCents', 'sort', 'direction',
                                         'mode', 'size', 'offset', 'cursor', 'revision', 'count'])
        parameters.mode['enum'] == ['CURSOR', 'OFFSET']
        parameters.count['enum'] == ['NONE', 'TOTAL', 'FILTERED', 'BOTH']
        paths[ITEMS + '/changes']['get'].responses['200'].content.containsKey('text/event-stream')
    }

    def 'traverses keyset pages using a unique tie breaker in both directions'() {
        given:
        def parameters = [sort: 'PRICE', direction: direction, size: '2']
        def first = search(parameters)
        def second = search(parameters + [cursor: first.nextCursor])
        def third = search(parameters + [cursor: second.nextCursor])
        def last = search(parameters + [cursor: third.nextCursor])

        expect:
        (first.items + second.items + third.items + last.items)*.id == expected
        [first, second, third, last]*.revision.unique().size() == 1
        !last.hasNext
        !last.containsKey('nextCursor')

        where:
        direction | expected
        'ASC'     | [5, 1, 3, 2, 4, 6, 7, 8]
        'DESC'    | [8, 7, 6, 4, 2, 3, 1, 5]
    }

    def 'uses name and ID for keyset ties and can switch from offset to cursor'() {
        given:
        insert(9, 'Alpha', 'ACTIVE', 150)

        when:
        def offset = search([mode: 'OFFSET', offset: '6', size: '3', sort: 'ID'])
        def first = search([q: 'Alpha', sort: 'NAME', size: '1'])
        def next = search([q: 'Alpha', sort: 'NAME', cursor: first.nextCursor])
        def capped = search([mode: 'OFFSET', offset: '8', size: '1'])

        then:
        offset.items*.id == [7, 8, 9]
        !offset.hasNext
        first.items*.id == [1]
        next.items*.id == [9]
        !next.hasNext
        !capped.hasNext

        when:
        insert(10, 'Gamma', 'ACTIVE', 100)
        capped = search([mode: 'OFFSET', offset: '8', size: '1'])
        def continuation = search([cursor: capped.nextCursor])

        then:
        capped.hasNext
        !capped.containsKey('nextOffset')
        continuation.items*.id == [10]
    }

    def 'offset and exact counts are independent and include matches beyond the page'() {
        given:
        def first = search([mode: 'OFFSET', q: 'A', status: 'ACTIVE', count: 'BOTH'])
        def next = search([mode: 'OFFSET', q: 'A', status: 'ACTIVE', offset: first.nextOffset.toString(),
                           revision: first.revision.toString(), count: 'FILTERED'])
        def empty = search([mode: 'OFFSET', offset: '8', count: 'BOTH'])

        expect:
        first.items*.id == [1, 2, 5]
        first.totalCount == 8
        first.filteredCount == 6
        first.nextOffset == 3
        next.items*.id == [6, 7, 8]
        !next.hasNext
        next.filteredCount == 6
        !next.containsKey('totalCount')
        empty.items == []
        !empty.hasNext
        empty.totalCount == 8
        empty.filteredCount == 8
        search([q: 'none', count: 'TOTAL']).subMap(['totalCount', 'filteredCount']) == [totalCount: 8]
        search([q: 'none', count: 'FILTERED']).filteredCount == 0
    }

    def 'rejects invalid parameters through the standard error contract'() {
        expect:
        def response = call(parameters).response
        response.status == 400
        json(response).error_code == 'bad_request'

        where:
        parameters << [[size: '0'], [size: '21'], [size: 'oops'], [sort: 'description'],
                       [status: 'UNKNOWN'], [minPriceCents: '8', maxPriceCents: '7'],
                       [minPriceCents: '-1'], [q: 'a' * 101], [cursor: 'not_a_cursor'],
                       [cursor: 'a' * 2049], [offset: '1'], [mode: 'OFFSET', offset: '9'],
                       [mode: 'OFFSET', cursor: 'x'], [cursor: 'x', revision: '0']]
    }

    def 'rejects a cursor from a different filter or sort but accepts a different size or count'() {
        given:
        def first = search([q: 'A', size: '1'])

        expect:
        call([q: 'B', cursor: first.nextCursor]).response.status == 400
        call([q: 'A', direction: 'DESC', cursor: first.nextCursor]).response.status == 400
        search([q: 'A', cursor: first.nextCursor, size: '2', count: 'FILTERED']).items*.id == [2, 3]
    }

    def 'returns only the requested projection and row count with a fixed SQL budget'() {
        given:
        def trace = sqlTrace.startTrace()

        when:
        def page = search([size: '2'])

        then:
        page.items.size() == 2
        page.hasNext
        page.items.every { it.keySet() == ['id', 'name', 'status', 'priceCents'] as Set }
        !page.containsKey('totalCount')
        !page.containsKey('filteredCount')
        trace.statements.size() == 2 // Revision and one bounded projection
        trace.statements*.rows == [1, 2]
        trace.statements[1].sql.contains('bounded_page')
        !trace.statements[1].sql.contains('description')
        !trace.statements[1].sql.contains('select *')

        when:
        trace.statements.clear()
        search([count: 'BOTH'])

        then:
        trace.statements*.rows == [1, 3, 1] // The unfiltered total is reused

        when:
        trace.statements.clear()
        search([q: 'a', count: 'BOTH'])

        then:
        trace.statements*.rows == [1, 3, 1, 1]
    }

    def 'invalidates both continuation modes after a committed insert, update, or delete'() {
        given:
        def first = search([mode: 'OFFSET', sort: 'PRICE'])

        when:
        mutations[operation].call()
        def cursorError = call([sort: 'PRICE', cursor: first.nextCursor]).response
        def offsetError = call([mode: 'OFFSET', sort: 'PRICE', offset: first.nextOffset.toString(),
                                revision: first.revision.toString()]).response

        then:
        cursorError.status == 409
        offsetError.status == 409
        json(cursorError).error_code == 'search_changed'
        json(offsetError).error_code == 'search_changed'
        search([sort: 'PRICE']).revision > first.revision

        where:
        operation << ['insert', 'update', 'delete']
    }

    def 'keeps page, revision and counts on one snapshot while another session commits changes'() {
        given:
        def first = search([count: 'BOTH', q: 'A'])
        def writer = Executors.newSingleThreadExecutor()
        sqlTrace.startTrace {
            writer.submit({ ->
                transactions.executeWithoutResult {
                    mutations['insert'].call()
                    insert(21, 'Delta second', 'ACTIVE', 30)
                    mutations['update'].call()
                    mutations['delete'].call()
                }
                true
            } as Callable<Boolean>).get(10, TimeUnit.SECONDS)
        }

        when:
        def page = search([count: 'BOTH', q: 'A'])

        then:
        page.revision == first.revision
        page.items == first.items
        page.totalCount == 8
        page.filteredCount == 7

        when:
        def fresh = search([count: 'BOTH', q: 'A'])

        then:
        fresh.revision > first.revision
        fresh.items*.id == [2, 5, 6]
        fresh.totalCount == 9
        fresh.filteredCount == 5

        cleanup:
        writer?.shutdownNow()
    }

    def 'broadcasts committed revisions with one shared poll and ignores rollbacks'() {
        given:
        def initial = search()
        def first = mvc.perform(get(ITEMS + '/changes')).andReturn()
        def second = mvc.perform(get(ITEMS + '/changes')).andReturn()
        def trace = sqlTrace.startTrace()

        when:
        changeFeed.publishChanges()

        then:
        first.request.asyncStarted
        second.request.asyncStarted
        mvc.perform(get(ITEMS + '/changes')).andReturn().response.status == 503
        trace.statements.size() == 1
        [first, second].every { it.response.contentAsString.contains("data:${initial.revision}\n") }

        when:
        def originalEvent = first.response.contentAsString
        transactions.executeWithoutResult { transaction ->
            mutations['update'].call()
            transaction.setRollbackOnly()
        }
        changeFeed.publishChanges()

        then:
        first.response.contentAsString == originalEvent
        search([cursor: initial.nextCursor]).revision == initial.revision

        when:
        mutations['update'].call()
        changeFeed.publishChanges()
        def current = search()

        then:
        current.revision > initial.revision
        [first, second].every { it.response.contentAsString.contains("data:${current.revision}\n") }

        cleanup:
        first?.request?.asyncContext?.complete()
        second?.request?.asyncContext?.complete()
    }

    protected Map<String, Closure> getMutations() {
        [insert: { insert(20, 'Delta', 'ACTIVE', 25) },
         update: { jdbc.update("UPDATE catalog_item SET name = 'Omega', price_cents = 5 WHERE id = 1") },
         delete: { jdbc.update('DELETE FROM catalog_item WHERE id = 3') }]
    }

    protected void insert(long id, String name, String status, long price) {
        jdbc.update('INSERT INTO catalog_item (id, name, status, price_cents, description) VALUES (?, ?, ?, ?, ?)',
                id, name, status, price, 'Description excluded from the list projection')
    }

    MvcResult call(Map<String, String> parameters = [:]) {
        def request = get(ITEMS)
        parameters.each { key, value -> request.param(key, value) }
        mvc.perform(request).andReturn()
    }

    Map search(Map<String, String> parameters = [:]) {
        def response = call(parameters).response
        assert response.status == 200 : response.contentAsString
        json(response)
    }

    protected Map json(response) {
        mapper.readValue(response.contentAsByteArray, Map)
    }
}
