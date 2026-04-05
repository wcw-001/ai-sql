package com.wcw.aisql.query.service;

import com.wcw.aisql.query.config.AiQueryProperties;
import com.wcw.aisql.query.model.PageInfo;
import com.wcw.aisql.query.model.QueryResult;
import com.wcw.aisql.query.model.SqlAnalysis;
import com.wcw.aisql.query.model.TableMetadata;
import com.wcw.aisql.query.security.SqlSchemaValidator;
import com.wcw.aisql.query.security.SqlSecurityValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class NaturalLanguageQueryService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Pattern JOIN_PATTERN = Pattern.compile("\\bjoin\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(?is)```(?:sql)?\\s*(.*?)\\s*```");
    private static final Pattern TRAILING_LIMIT_PATTERN = Pattern.compile(
            "(?is)\\s+limit\\s+(?:\\d+\\s*,\\s*\\d+|\\d+\\s+offset\\s+\\d+|\\d+)\\s*$"
    );

    private static final String SQL_PROMPT = """
            You are a senior MySQL 8.0 expert.
            Generate exactly one executable SELECT statement for the user's request.

            Rules:
            1. Return SQL only. No markdown, no explanation, no comments.
            2. Use only tables, columns, and relations that appear in the schema below.
            3. Never invent table names, column names, aliases, or join conditions.
            4. Use explicit column lists instead of SELECT *.
            5. If multiple tables are needed, every JOIN condition must reference real columns from both sides.
            6. Add necessary filtering conditions when the request clearly implies them.
            7. Do not add LIMIT or OFFSET. Pagination is appended by the service.
            8. Prefer the simplest correct query. If a field is uncertain, do not use it.
            9. Use column comments, types, nullability, and primary-key hints to infer field meaning correctly.

            Database schema:
            {schema}

            User request:
            {query}
            """;

    private static final String SQL_REPAIR_PROMPT = """
            The previous SQL is invalid and must be corrected.

            Rules:
            1. Return SQL only. No markdown, no explanation, no comments.
            2. Keep the SQL as simple as possible while satisfying the request.
            3. Use only tables, columns, and relations from the schema.
            4. Do not use LIMIT or OFFSET.
            5. Fix every issue described in the failure reason.
            6. Re-evaluate column meaning from column comments, types, nullability, and primary-key hints.

            Database schema:
            {schema}

            User request:
            {query}

            Previous SQL:
            {sql}

            Failure reason:
            {reason}
            """;

    private final ChatClient chatClient;
    private final JdbcTemplate jdbcTemplate;
    private final SqlSecurityValidator sqlSecurityValidator;
    private final SqlSchemaValidator sqlSchemaValidator;
    private final AiQueryProperties properties;
    private final DatabaseSchemaService databaseSchemaService;
    private final DataPermissionService dataPermissionService;
    private final QueryAuditService queryAuditService;
    private final ConcurrentMap<String, QueryPlanCacheEntry> queryPlanCache = new ConcurrentHashMap<>();

    public NaturalLanguageQueryService(ChatClient.Builder chatClientBuilder,
                                       JdbcTemplate jdbcTemplate,
                                       SqlSecurityValidator sqlSecurityValidator,
                                       SqlSchemaValidator sqlSchemaValidator,
                                       AiQueryProperties properties,
                                       DatabaseSchemaService databaseSchemaService,
                                       DataPermissionService dataPermissionService,
                                       QueryAuditService queryAuditService) {
        this.chatClient = chatClientBuilder.build();
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSecurityValidator = sqlSecurityValidator;
        this.sqlSchemaValidator = sqlSchemaValidator;
        this.properties = properties;
        this.databaseSchemaService = databaseSchemaService;
        this.dataPermissionService = dataPermissionService;
        this.queryAuditService = queryAuditService;
    }

    public QueryResult execute(String userQuery, int page, int pageSize) {
        long start = System.currentTimeMillis();
        String auditedSql = null;
        List<Object> auditedParams = List.of();
        try {
            int safePage = Math.max(page, 1);
            int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));

            QueryPlan queryPlan = resolveQueryPlan(userQuery);
            String baseSql = queryPlan.baseSql();
            String pagedSql = appendPagination(baseSql, safePage, safePageSize);

            queryAuditService.logGeneratedSql(userQuery, pagedSql);
            auditedSql = pagedSql;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(pagedSql);
            PageInfo pageInfo = buildPageInfo(safePage, safePageSize, queryPlan.total());

            long duration = System.currentTimeMillis() - start;
            queryAuditService.logExecution(pagedSql, List.of(), duration, rows.size(), null);
            return QueryResult.success(pagedSql, List.of(), queryPlan.sqlAnalysis(), rows, pageInfo);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            queryAuditService.logExecution(auditedSql, auditedParams, duration, 0, e);
            log.error("Natural language query failed, query={}", userQuery, e);
            return QueryResult.error("Query failed: " + e.getMessage());
        }
    }

    private String generateSql(String prompt) {
        String output = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return normalizeSql(output);
    }

    private String normalizeSql(String rawSql) {
        if (rawSql == null) {
            return "";
        }
        String sql = rawSql.trim();
        Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(sql);
        if (codeBlockMatcher.find()) {
            sql = codeBlockMatcher.group(1).trim();
        }
        int sqlStartIndex = indexOfSqlStart(sql);
        if (sqlStartIndex > 0) {
            sql = sql.substring(sqlStartIndex).trim();
        }
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        return sql;
    }

    private String buildGeneratePrompt(String userQuery, String schemaPrompt) {
        return SQL_PROMPT
                .replace("{schema}", schemaPrompt)
                .replace("{query}", userQuery == null ? "" : userQuery);
    }

    private String buildRepairPrompt(String userQuery, String schemaPrompt, String previousSql, String failureReason) {
        return SQL_REPAIR_PROMPT
                .replace("{schema}", schemaPrompt)
                .replace("{query}", userQuery == null ? "" : userQuery)
                .replace("{sql}", previousSql == null || previousSql.isBlank() ? "(empty)" : previousSql)
                .replace("{reason}", failureReason == null || failureReason.isBlank() ? "Unknown validation error" : failureReason);
    }

    private String stripTrailingLimit(String sql) {
        if (sql == null) {
            return "";
        }
        return TRAILING_LIMIT_PATTERN.matcher(sql.trim()).replaceFirst("").trim();
    }

    private String appendPagination(String sql, int page, int pageSize) {
        long offset = (long) (page - 1) * pageSize;
        return sql + " LIMIT " + pageSize + " OFFSET " + offset;
    }

    private QueryPlan resolveQueryPlan(String userQuery) {
        long ttlMs = Math.max(0, properties.getQueryPlanCacheSeconds()) * 1000;
        String cacheKey = normalizeUserQuery(userQuery);
        long now = System.currentTimeMillis();

        if (ttlMs > 0) {
            QueryPlanCacheEntry cached = queryPlanCache.get(cacheKey);
            if (cached != null && now < cached.expiresAtMs()) {
                return cached.queryPlan();
            }
        }

        String schemaPrompt = databaseSchemaService.buildSchemaPrompt();
        Map<String, TableMetadata> metadataMap = databaseSchemaService.getTableMetadataMap();
        QueryPlan freshPlan = generateValidatedQueryPlan(userQuery, schemaPrompt, metadataMap);

        if (ttlMs > 0) {
            queryPlanCache.put(cacheKey, new QueryPlanCacheEntry(freshPlan, now + ttlMs));
        }
        return freshPlan;
    }

    private QueryPlan generateValidatedQueryPlan(String userQuery,
                                                 String schemaPrompt,
                                                 Map<String, TableMetadata> metadataMap) {
        int maxAttempts = Math.max(1, properties.getSqlGenerationMaxAttempts());
        String previousSql = "";
        String failureReason = "";

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String prompt = attempt == 1
                    ? buildGeneratePrompt(userQuery, schemaPrompt)
                    : buildRepairPrompt(userQuery, schemaPrompt, previousSql, failureReason);

            String generatedSql = generateSql(prompt);
            String securedSql = dataPermissionService.apply(generatedSql);
            String baseSql = stripTrailingLimit(securedSql);

            try {
                sqlSecurityValidator.validate(baseSql);
                sqlSchemaValidator.validate(baseSql, metadataMap);
                return new QueryPlan(baseSql, analyzeSql(baseSql), queryTotal(baseSql));
            } catch (Exception e) {
                previousSql = baseSql;
                failureReason = extractFailureReason(e);
                log.warn("SQL generation attempt {} failed, query={}, sql={}, reason={}",
                        attempt, userQuery, baseSql, failureReason);
            }
        }

        throw new IllegalStateException("AI failed to generate a valid SQL after retries: " + failureReason);
    }

    private String normalizeUserQuery(String userQuery) {
        return userQuery == null ? "" : userQuery.trim();
    }

    private long queryTotal(String sql) {
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS total_rows";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        return total == null ? 0L : total;
    }

    private PageInfo buildPageInfo(int page, int pageSize, long total) {
        long totalPages = total == 0 ? 0 : (long) Math.ceil((double) total / pageSize);
        return new PageInfo(
                page,
                pageSize,
                total,
                totalPages,
                page > 1,
                totalPages > 0 && page < totalPages
        );
    }

    private int indexOfSqlStart(String sql) {
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        int selectIndex = lowerSql.indexOf("select");
        int withIndex = lowerSql.indexOf("with");
        if (selectIndex < 0) {
            return withIndex;
        }
        if (withIndex < 0) {
            return selectIndex;
        }
        return Math.min(selectIndex, withIndex);
    }

    private SqlAnalysis analyzeSql(String sql) {
        int score = 100;
        List<String> risks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        String lowerSql = sql == null ? "" : sql.toLowerCase(Locale.ROOT);

        if (lowerSql.contains("select *")) {
            score -= 20;
            risks.add("SELECT * may introduce unnecessary IO and network cost.");
            suggestions.add("Select only the required columns.");
        }

        if (!lowerSql.contains("where")) {
            score -= 20;
            risks.add("Missing WHERE clause may trigger a full table scan.");
            suggestions.add("Add filters and prefer indexed columns.");
        }

        if (!lowerSql.contains("limit")) {
            score -= 10;
            risks.add("The base SQL has no LIMIT and relies on service-side pagination.");
            suggestions.add("Keep pagination at the service layer for stable behavior.");
        }

        int joinCount = countMatches(JOIN_PATTERN, lowerSql);
        if (joinCount >= 4) {
            score -= 12;
            risks.add("Many JOINs may produce a complex execution plan.");
            suggestions.add("Ensure JOIN columns are indexed and review EXPLAIN output.");
        }

        if (lowerSql.contains("order by") && !lowerSql.contains("limit")) {
            score -= 8;
            risks.add("ORDER BY without LIMIT may be expensive.");
            suggestions.add("Ensure sort columns are indexed and watch large-page scans.");
        }

        if (lowerSql.contains("not in")) {
            score -= 8;
            risks.add("NOT IN may perform poorly on large datasets.");
            suggestions.add("Consider NOT EXISTS or LEFT JOIN + IS NULL.");
        }

        score = Math.max(0, Math.min(score, 100));
        String level;
        if (score >= 90) {
            level = "A";
        } else if (score >= 75) {
            level = "B";
        } else if (score >= 60) {
            level = "C";
        } else {
            level = "D";
        }

        if (risks.isEmpty()) {
            risks.add("No obvious risk detected.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("The SQL looks stable; validate it further with EXPLAIN.");
        }

        return new SqlAnalysis(score, level, risks, suggestions);
    }

    private int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String extractFailureReason(Exception exception) {
        Throwable cursor = exception;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return (message == null || message.isBlank()) ? exception.getClass().getSimpleName() : message;
    }

    private record QueryPlan(String baseSql, SqlAnalysis sqlAnalysis, long total) {
    }

    private record QueryPlanCacheEntry(QueryPlan queryPlan, long expiresAtMs) {
    }
}
