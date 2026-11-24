package com.template.service.search.catalog;

public record ItemSummary(long id, String name, Status status, long priceCents) {

    public enum Status {
        ACTIVE, ARCHIVED
    }
}
