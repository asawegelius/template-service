package com.template.service.search;

public final class SearchOptions {

    private SearchOptions() {
    }

    public enum Mode {
        CURSOR, OFFSET
    }

    public enum Direction {
        ASC, DESC
    }

    public enum Count {
        NONE, TOTAL, FILTERED, BOTH;

        public boolean includesTotal() {
            return this == TOTAL || this == BOTH;
        }

        public boolean includesFiltered() {
            return this == FILTERED || this == BOTH;
        }
    }
}
