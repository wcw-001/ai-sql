package com.wcw.aisql.query.security;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SqlSecurityValidator {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "insert", "update", "delete", "drop", "alter", "truncate",
            "create", "replace", "merge", "grant", "revoke", "call"
    );
    private static final Pattern MULTI_STATEMENT_PATTERN = Pattern.compile(";\\s*\\S+");

    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("AI did not generate valid SQL");
        }

        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("select")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed");
        }

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (normalized.matches(".*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException("SQL contains forbidden keyword: " + keyword);
            }
        }

        if (MULTI_STATEMENT_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException("Multiple SQL statements are not allowed");
        }
    }
}
