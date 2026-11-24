package com.template.service.search.catalog;

import com.template.service.search.SearchOptions.Count;
import com.template.service.search.SearchOptions.Direction;
import com.template.service.search.SearchOptions.Mode;
import com.template.service.search.SearchProperties;
import com.template.service.search.catalog.ItemSummary.Status;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ItemSearchQuery(
        @Size(max = 100) String q,
        Status status,
        @PositiveOrZero Long minPriceCents,
        @PositiveOrZero Long maxPriceCents,
        SortBy sort,
        Direction direction,
        Mode mode,
        @Positive Integer size,
        @PositiveOrZero Integer offset,
        @Size(max = 2048) String cursor,
        @PositiveOrZero Long revision,
        Count count) {

    public ItemSearchQuery {
        if (q != null) {
            q = q.strip();
            if (q.isEmpty()) {
                q = null;
            }
        }
        sort = sort == null ? SortBy.ID : sort;
        direction = direction == null ? Direction.ASC : direction;
        mode = mode == null ? Mode.CURSOR : mode;
        count = count == null ? Count.NONE : count;
    }

    int pageSize(SearchProperties properties) {
        if (minPriceCents != null && maxPriceCents != null && minPriceCents > maxPriceCents) {
            throw new IllegalArgumentException("Minimum price exceeds maximum price.");
        }
        if ((mode == Mode.CURSOR && offset != null) || (mode == Mode.OFFSET && cursor != null)
                || (cursor != null && revision != null)) {
            throw new IllegalArgumentException("Conflicting pagination parameters.");
        }
        if (offset != null && offset > properties.maxOffset()) {
            throw new IllegalArgumentException("Offset exceeds the configured maximum.");
        }
        if (size != null && size > properties.maxPageSize()) {
            throw new IllegalArgumentException("Page size exceeds the configured maximum.");
        }
        return size == null ? properties.defaultPageSize() : size;
    }

    boolean hasFilters() {
        return q != null || status != null || minPriceCents != null || maxPriceCents != null;
    }

    public enum SortBy {
        ID, NAME, PRICE
    }
}
