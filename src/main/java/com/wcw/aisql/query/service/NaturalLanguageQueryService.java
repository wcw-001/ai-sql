package com.wcw.aisql.query.service;

import com.wcw.aisql.query.model.QueryResult;
import com.wcw.aisql.query.model.SqlAnalysis;
import com.wcw.aisql.query.security.SqlSecurityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaturalLanguageQueryService {

    private static final Pattern JOIN_PATTERN = Pattern.compile("\\bjoin\\b", Pattern.CASE_INSENSITIVE);

    private static final String SQL_PROMPT = """
            你是一个专业的 SQL 开发专家，请基于以下数据库结构生成准确、高效的 MySQL 查询语句。

            规则：
            1. 只返回 SQL 语句，不包含任何解释性文字。
            2. 使用标准 MySQL 8.0 语法。
            3. 明确指定查询字段，避免使用 SELECT *。
            4. 字符串条件使用单引号，并正确处理特殊字符。
            5. 合理使用 JOIN，避免不必要的子查询。
            6. 包含必要的 WHERE 条件，避免全表扫描。

            数据库结构：
            {schema}

            用户需求：{query}
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final JdbcTemplate jdbcTemplate;
    private final SqlSecurityValidator sqlSecurityValidator;
    private final DatabaseSchemaService databaseSchemaService;
    private final DataPermissionService dataPermissionService;
    private final QueryAuditService queryAuditService;

    public QueryResult execute(String userQuery) {
        long start = System.currentTimeMillis();
        String auditedSql = null;
        List<Object> auditedParams = List.of();
        try {
            String sql = generateSql(userQuery);
            sqlSecurityValidator.validate(sql);

            queryAuditService.logGeneratedSql(userQuery, sql);
            auditedSql = sql;

            SqlAnalysis sqlAnalysis = analyzeSql(sql);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            long duration = System.currentTimeMillis() - start;
            queryAuditService.logExecution(sql, List.of(), duration, rows.size(), null);
            return QueryResult.success(sql, List.of(), sqlAnalysis, rows);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            queryAuditService.logExecution(auditedSql, auditedParams, duration, 0, e);
            log.error("Natural language query failed, query={}", userQuery, e);
            return QueryResult.error("Query failed: " + e.getMessage());
        }
    }

    private String generateSql(String userQuery) {
        String prompt = SQL_PROMPT
                .replace("{schema}", databaseSchemaService.buildSchemaPrompt())
                .replace("{query}", userQuery);

        String output = chatClientBuilder.build()
                .prompt()
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
        sql = sql.replace("```sql", "").replace("```", "").trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        return sql;
    }

    private SqlAnalysis analyzeSql(String sql) {
        int score = 100;
        List<String> risks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        String lowerSql = sql == null ? "" : sql.toLowerCase(Locale.ROOT);

        if (lowerSql.contains("select *")) {
            score -= 20;
            risks.add("使用 SELECT *，可能带来不必要的 IO 和网络开销。");
            suggestions.add("改为显式选择必要字段，降低查询成本。");
        }

        if (!lowerSql.contains(" where ")) {
            score -= 20;
            risks.add("缺少 WHERE 条件，存在全表扫描风险。");
            suggestions.add("补充过滤条件并优先使用索引字段。");
        }

        if (!lowerSql.contains(" limit ")) {
            score -= 10;
            risks.add("缺少 LIMIT，结果集可能过大。");
            suggestions.add("添加 LIMIT 或分页条件，避免单次返回过多数据。");
        }

        int joinCount = countMatches(JOIN_PATTERN, lowerSql);
        if (joinCount >= 4) {
            score -= 12;
            risks.add("JOIN 数量较多，执行计划可能复杂。");
            suggestions.add("确认 JOIN 字段有索引，并检查执行计划。");
        }

        if (lowerSql.contains("order by") && !lowerSql.contains("limit")) {
            score -= 8;
            risks.add("ORDER BY 未配合 LIMIT，排序代价可能较高。");
            suggestions.add("若只需要前 N 条数据，建议 ORDER BY + LIMIT 组合。");
        }

        if (lowerSql.contains(" not in ")) {
            score -= 8;
            risks.add("NOT IN 在大数据量下可能性能较差。");
            suggestions.add("可评估改写为 NOT EXISTS 或 LEFT JOIN + IS NULL。");
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
            risks.add("未发现明显风险。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("SQL 结构较稳健，可结合 EXPLAIN 继续验证执行计划。");
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
}
