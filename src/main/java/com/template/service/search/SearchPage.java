package com.template.service.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchPage<T>(
        List<T> items,
        int size,
        long revision,
        boolean hasNext,
        Integer nextOffset,
        String nextCursor,
        Long totalCount,
        Long filteredCount) {
}
