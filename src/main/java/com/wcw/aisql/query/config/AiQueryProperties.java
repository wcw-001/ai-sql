package com.wcw.aisql.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.query")
public class AiQueryProperties {

    /**
     * Slow query threshold in milliseconds.
     */
    private long slowQueryThresholdMs = 1000;

    /**
     * Database schema cache ttl in seconds.
     */
    private long schemaCacheSeconds = 300;

    public long getSlowQueryThresholdMs() {
        return slowQueryThresholdMs;
    }

    public void setSlowQueryThresholdMs(long slowQueryThresholdMs) {
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    public long getSchemaCacheSeconds() {
        return schemaCacheSeconds;
    }

    public void setSchemaCacheSeconds(long schemaCacheSeconds) {
        this.schemaCacheSeconds = schemaCacheSeconds;
    }
}
