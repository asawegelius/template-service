package com.template.service.search.catalog;

import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.lower;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.val;

import com.template.service.search.SearchOptions.Direction;
import com.template.service.search.catalog.ItemCursorCodec.Cursor;
import com.template.service.search.catalog.ItemSummary.Status;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

@Repository
class ItemSearchRepository {

    private static final Table<?> ITEMS = table(name("catalog_item"));
    private static final Field<Long> ID = field(name("id"), Long.class);
    private static final Field<String> NAME = field(name("name"), String.class);
    private static final Field<String> STATUS = field(name("status"), String.class);
    private static final Field<Long> PRICE = field(name("price_cents"), Long.class);
    private static final Table<?> REVISIONS = table(name("catalog_revision"));
    private static final Field<Long> REVISION = field(name("revision"), Long.class);

    private final DSLContext sql;

    ItemSearchRepository(DSLContext sql) {
        this.sql = sql;
    }

    long revision() {
        return sql.select(REVISION).from(REVISIONS).where(field(name("id"), Integer.class).eq(1))
                .fetchSingle().value1();
    }

    Condition filters(ItemSearchQuery query) {
        Condition condition = noCondition();
        if (query.q() != null) {
            String prefix = query.q().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            condition = condition.and(lower(NAME).like(lower(val(prefix + "%")), '!'));
        }
        if (query.status() != null) {
            condition = condition.and(STATUS.eq(query.status().name()));
        }
        if (query.minPriceCents() != null) {
            condition = condition.and(PRICE.ge(query.minPriceCents()));
        }
        if (query.maxPriceCents() != null) {
            condition = condition.and(PRICE.le(query.maxPriceCents()));
        }
        return condition;
    }

    long countMatches(Condition filters) {
        return sql.select(count().cast(Long.class)).from(ITEMS).where(filters).fetchSingle().value1();
    }

    Rows page(ItemSearchQuery query, Condition filters, Cursor cursor, int size) {
        var ordered = sql.select(ID, NAME, STATUS, PRICE)
                .from(ITEMS)
                .where(filters)
                .orderBy(order(query, ID, NAME, PRICE));

        // The database checks for one more match. JDBC still receives at most size rows.
        var bounded = (cursor == null
                ? ordered.limit(size + 1).offset(query.offset() == null ? 0 : query.offset())
                : ordered.seekAfter(seekValues(query, cursor)).limit(size + 1)).asTable("bounded_page");
        var id = bounded.field(ID);
        var name = bounded.field(NAME);
        var status = bounded.field(STATUS);
        var price = bounded.field(PRICE);

        var rows = sql.select(id, name, status, price, count().over())
                .from(bounded)
                .orderBy(order(query, id, name, price))
                .limit(size)
                .fetch();
        boolean hasNext = !rows.isEmpty() && rows.getFirst().value5() > size;
        List<ItemSummary> items = rows.map(row ->
                new ItemSummary(row.value1(), row.value2(), Status.valueOf(row.value3()), row.value4()));
        return new Rows(items, hasNext);
    }

    private List<SortField<?>> order(ItemSearchQuery query, Field<Long> id, Field<String> name, Field<Long> price) {
        return switch (query.sort()) {
            case ID -> List.of(sort(id, query.direction()));
            case NAME -> List.of(sort(name, query.direction()), sort(id, query.direction()));
            case PRICE -> List.of(sort(price, query.direction()), sort(id, query.direction()));
        };
    }

    private SortField<?> sort(Field<?> field, Direction direction) {
        return direction == Direction.ASC ? field.asc() : field.desc();
    }

    private Object[] seekValues(ItemSearchQuery query, Cursor cursor) {
        return switch (query.sort()) {
            case ID -> new Object[] {cursor.id()};
            case NAME -> new Object[] {cursor.sortValue(), cursor.id()};
            case PRICE -> new Object[] {Long.parseLong(cursor.sortValue()), cursor.id()};
        };
    }

    record Rows(List<ItemSummary> items, boolean hasNext) {
    }
}
