package com.wcw.aisql.query.service;

import com.wcw.aisql.query.model.TableMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataPermissionService {

    private static final Pattern TABLE_ALIAS_PATTERN = Pattern.compile(
            "(?i)\\b(from|join)\\s+([`\\w.]+)(?:\\s+(?:as\\s+)?([`\\w]+))?"
    );
    private static final List<String> CLAUSE_ORDER = Arrays.asList(" group by ", " order by ", " limit ", " having ");
    private static final List<String> RESERVED_ALIAS = Arrays.asList("on", "where", "group", "order", "limit", "left", "right", "inner", "join");

    private final DatabaseSchemaService databaseSchemaService;

    /**
     * 简单的身份验证应用方法（当前版本不实施具体权限控制）
     * @param sql 原始SQL
     * @return 未经修改的SQL
     */
    public String apply(String sql) {
        // 当前版本不实施数据权限控制，直接返回原始SQL
        return sql;
    }

    private Map<String, String> extractAliasToTable(String sql) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher matcher = TABLE_ALIAS_PATTERN.matcher(sql);
        while (matcher.find()) {
            String table = normalizeIdentifier(matcher.group(2));
            String alias = matcher.group(3) == null ? table : normalizeIdentifier(matcher.group(3));
            if (RESERVED_ALIAS.contains(alias.toLowerCase(Locale.ROOT))) {
                alias = table;
            }
            map.put(alias, table);
        }
        return map;
    }

    private String normalizeIdentifier(String identifier) {
        String clean = identifier.replace("`", "");
        int dot = clean.lastIndexOf('.');
        return dot >= 0 ? clean.substring(dot + 1) : clean;
    }

    private String injectPredicates(String sql, List<String> predicates) {
        String condition = predicates.stream().collect(Collectors.joining(" and ", "(", ")"));
        String lower = sql.toLowerCase(Locale.ROOT);
        int splitIndex = findClauseStart(lower);
        String head = splitIndex >= 0 ? sql.substring(0, splitIndex) : sql;
        String tail = splitIndex >= 0 ? sql.substring(splitIndex) : "";
        if (lower.contains(" where ")) {
            return head + " and " + condition + tail;
        }
        return head + " where " + condition + tail;
    }

    private int findClauseStart(String lowerSql) {
        int idx = -1;
        for (String clause : CLAUSE_ORDER) {
            int c = lowerSql.indexOf(clause);
            if (c >= 0 && (idx == -1 || c < idx)) {
                idx = c;
            }
        }
        return idx;
    }
}
