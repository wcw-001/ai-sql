package com.wcw.aisql.query.security;

import com.wcw.aisql.query.common.ErrorCode;
import com.wcw.aisql.query.exceotion.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

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
            throw new BusinessException(ErrorCode.API_ERROR,"没有生成有效的sql！");
        }

        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("select")) {
            throw new BusinessException(ErrorCode.API_ERROR,"仅仅支持查询！");
        }

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (normalized.matches(".*\\b" + keyword + "\\b.*")) {
                throw new BusinessException(ErrorCode.API_ERROR,"SQL内容有违规词的: " + keyword);
            }
        }

        if (MULTI_STATEMENT_PATTERN.matcher(normalized).find()) {
            throw new BusinessException(ErrorCode.API_ERROR,"不允许使用 Multi-Statement 功能来连续执行多个 SQL 语句！");
        }
    }
}
