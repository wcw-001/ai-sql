package com.wcw.aisql.query.service;

import com.wcw.aisql.query.model.QueryResult;
import com.wcw.aisql.query.security.SqlSecurityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaturalLanguageQueryService {

    private static final String SQL_PROMPT = """
            你是一个专业的SQL开发专家，请基于以下数据库结构生成准确、高效的MySQL查询语句
            
            数据库结构:
            1. 严格只返回SQL语句，不包含任何解释性文字
            2. 使用标准MySQL8.0语法
            3. 明确指定查询字段，避免使用SELECT *
            4. 字符串条件使用单引号，正确转义特殊字符
            5. 合理使用JOIN替代子查询提升性能
            6. 包含必要的WHERE条件避免全表扫描

            数据库结构:
            {schema}

            用户要求: {query}
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
            
            // 直接执行SQL，不进行数据权限过滤
            queryAuditService.logGeneratedSql(userQuery, sql);
            auditedSql = sql;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            long duration = System.currentTimeMillis() - start;
            queryAuditService.logExecution(sql, List.of(), duration, rows.size(), null);
            return QueryResult.success(sql, List.of(), rows);
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
}
