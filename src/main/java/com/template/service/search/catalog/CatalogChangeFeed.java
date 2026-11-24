package com.template.service.search.catalog;

import com.template.service.search.SearchProperties;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class CatalogChangeFeed {

    private final ItemSearchRepository repository;
    private final SearchProperties.Changes properties;
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    CatalogChangeFeed(ItemSearchRepository repository, SearchProperties properties) {
        this.repository = repository;
        this.properties = properties.changes();
    }

    synchronized SseEmitter subscribe() {
        if (subscriptions.size() >= properties.maxSubscribers()) {
            return null;
        }

        var emitter = new SseEmitter(properties.timeout().toMillis());
        var subscription = new Subscription(emitter);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onError(error -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> {
            subscriptions.remove(subscription);
            emitter.complete();
        });
        subscriptions.add(subscription);
        return emitter;
    }

    @Scheduled(fixedDelayString = "${app.search.changes.poll-interval}")
    void publishChanges() {
        if (subscriptions.isEmpty()) {
            return;
        }
        long revision = repository.revision();
        for (var subscription : subscriptions) {
            if (subscription.lastRevision != null && subscription.lastRevision == revision) {
                continue;
            }
            try {
                subscription.emitter.send(SseEmitter.event()
                        .name("revision")
                        .id(Long.toString(revision))
                        .data(revision));
                subscription.lastRevision = revision;
            } catch (IOException | IllegalStateException exception) {
                subscriptions.remove(subscription);
            }
        }
    }

    private static class Subscription {
        private final SseEmitter emitter;
        // Only the scheduled publisher accesses this field.
        private Long lastRevision;

        private Subscription(SseEmitter emitter) {
            this.emitter = emitter;
        }
    }
}
