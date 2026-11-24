package com.template.service.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.search")
public record SearchProperties(
        @Min(1) int defaultPageSize,
        @Min(1) @Max(Integer.MAX_VALUE - 1) int maxPageSize,
        @Min(0) int maxOffset,
        @Valid Changes changes) {

    public SearchProperties {
        if (defaultPageSize > maxPageSize) {
            throw new IllegalArgumentException("Default page size must be at most the maximum page size.");
        }
    }

    public record Changes(Duration pollInterval, Duration timeout, @Min(1) int maxSubscribers) {
        public Changes {
            if (pollInterval.toMillis() < 1 || timeout.compareTo(pollInterval) <= 0) {
                throw new IllegalArgumentException("Change feed timeout must exceed the positive poll interval.");
            }
        }
    }
}
