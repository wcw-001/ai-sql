package com.wcw.aisql.query.service;

import com.wcw.aisql.query.config.AiQueryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryAuditService {

    private final AiQueryProperties properties;

    public void logGeneratedSql(String naturalQuery, String sql) {
        log.info("audit.generate query='{}' sql='{}'", naturalQuery, sql);
    }

    public void logExecution(String sql, List<Object> params, long durationMs, int rowCount, Throwable error) {
        if (error == null) {
            log.info("audit.execute ok durationMs={} rows={} sql='{}' params={}", durationMs, rowCount, sql, params);
        } else {
            log.error("audit.execute fail durationMs={} sql='{}' params={}", durationMs, sql, params, error);
        }
        if (durationMs >= properties.getSlowQueryThresholdMs()) {
            log.warn("audit.slow-query durationMs={} thresholdMs={} sql='{}'",
                    durationMs, properties.getSlowQueryThresholdMs(), sql);
        }
    }
}
