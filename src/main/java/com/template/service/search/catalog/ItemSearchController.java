package com.template.service.search.catalog;

import com.template.service.exception.ErrorResponse;
import com.template.service.logging.CorrelationIdFilter;
import com.template.service.search.SearchPage;
import com.template.service.search.catalog.ItemSearchService.SearchChangedException;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/catalog/items")
class ItemSearchController {

    private final ItemSearchService service;
    private final CatalogChangeFeed changeFeed;

    ItemSearchController(ItemSearchService service, CatalogChangeFeed changeFeed) {
        this.service = service;
        this.changeFeed = changeFeed;
    }

    @GetMapping
    ResponseEntity<SearchPage<ItemSummary>> search(@Valid @ModelAttribute @ParameterObject ItemSearchQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(query));
    }

    @GetMapping(path = "/changes", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> changes() {
        var emitter = changeFeed.subscribe();
        if (emitter == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(emitter);
    }

    @ExceptionHandler(SearchChangedException.class)
    ResponseEntity<ErrorResponse> changed() {
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("search_changed", "Search results changed. Start a new search.",
                        MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)));
    }
}
