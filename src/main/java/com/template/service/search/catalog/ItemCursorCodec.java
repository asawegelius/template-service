package com.template.service.search.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.service.search.SearchOptions.Direction;
import com.template.service.search.catalog.ItemSearchQuery.SortBy;
import com.template.service.search.catalog.ItemSummary.Status;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class ItemCursorCodec {

    private static final int VERSION = 1;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper mapper;

    ItemCursorCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String encode(ItemSearchQuery query, long revision, ItemSummary lastItem) {
        String sortValue = switch (query.sort()) {
            case ID -> null;
            case NAME -> lastItem.name();
            case PRICE -> Long.toString(lastItem.priceCents());
        };
        return ENCODER.encodeToString(serialize(
                new Cursor(VERSION, fingerprint(query), revision, lastItem.id(), sortValue)));
    }

    Cursor decode(ItemSearchQuery query) {
        Cursor cursor;
        try {
            cursor = mapper.readValue(DECODER.decode(query.cursor()), Cursor.class);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid search cursor.");
        }
        if (cursor == null || cursor.version() != VERSION || cursor.revision() == null
                || cursor.revision() < 0 || cursor.id() == null
                || !fingerprint(query).equals(cursor.scope()) || !validSortValue(query.sort(), cursor.sortValue())) {
            throw new IllegalArgumentException("Invalid search cursor.");
        }
        return cursor;
    }

    private boolean validSortValue(SortBy sort, String value) {
        return switch (sort) {
            case ID -> value == null;
            case NAME -> value != null && value.length() <= 320;
            case PRICE -> nonNegativeLong(value);
        };
    }

    private boolean nonNegativeLong(String value) {
        try {
            return Long.parseLong(value) >= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String fingerprint(ItemSearchQuery query) {
        var scope = new Scope(query.q(), query.status(), query.minPriceCents(), query.maxPriceCents(),
                query.sort(), query.direction());
        try {
            return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256").digest(serialize(scope)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private byte[] serialize(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the search cursor.", exception);
        }
    }

    record Cursor(int version, String scope, Long revision, Long id, String sortValue) {
    }

    private record Scope(String q, Status status, Long minPriceCents, Long maxPriceCents,
                         SortBy sort, Direction direction) {
    }
}
