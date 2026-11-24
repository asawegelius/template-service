package com.template.service.search.catalog;

import static org.jooq.impl.DSL.noCondition;

import com.template.service.search.SearchOptions.Count;
import com.template.service.search.SearchOptions.Mode;
import com.template.service.search.SearchPage;
import com.template.service.search.SearchProperties;
import com.template.service.search.catalog.ItemCursorCodec.Cursor;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class ItemSearchService {

    private final ItemSearchRepository repository;
    private final ItemCursorCodec cursors;
    private final SearchProperties properties;
    private final TransactionTemplate snapshot;

    ItemSearchService(ItemSearchRepository repository, ItemCursorCodec cursors, SearchProperties properties,
                      PlatformTransactionManager transactions, DSLContext sql) {
        this.repository = repository;
        this.cursors = cursors;
        this.properties = properties;
        this.snapshot = new TransactionTemplate(transactions);
        snapshot.setReadOnly(true);
        // H2 repeatable read still allows phantoms. PostgreSQL repeatable read uses a transaction snapshot.
        snapshot.setIsolationLevel(sql.dialect().family() == SQLDialect.H2
                ? TransactionDefinition.ISOLATION_SERIALIZABLE
                : TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public SearchPage<ItemSummary> search(ItemSearchQuery query) {
        int size = query.pageSize(properties);
        var cursor = query.cursor() == null ? null : cursors.decode(query);
        return snapshot.execute(transaction -> searchSnapshot(query, cursor, size));
    }

    private SearchPage<ItemSummary> searchSnapshot(ItemSearchQuery query, Cursor cursor, int size) {
        Long expectedRevision = cursor == null ? query.revision() : cursor.revision();
        long revision = repository.revision();
        if (expectedRevision != null && expectedRevision != revision) {
            throw new SearchChangedException();
        }

        var filters = repository.filters(query);
        var page = repository.page(query, filters, cursor, size);
        Count count = query.count();
        Long total = count.includesTotal() ? repository.countMatches(noCondition()) : null;
        Long filtered = null;
        if (count.includesFiltered()) {
            filtered = total != null && !query.hasFilters() ? total : repository.countMatches(filters);
        }

        String nextCursor = null;
        Integer nextOffset = null;
        if (page.hasNext()) {
            nextCursor = cursors.encode(query, revision, page.items().getLast());
            if (query.mode() == Mode.OFFSET) {
                long offset = (query.offset() == null ? 0L : query.offset()) + size;
                if (offset <= properties.maxOffset()) {
                    nextOffset = (int) offset;
                }
            }
        }
        return new SearchPage<>(page.items(), size, revision, page.hasNext(), nextOffset, nextCursor, total, filtered);
    }

    static class SearchChangedException extends RuntimeException {
    }
}
