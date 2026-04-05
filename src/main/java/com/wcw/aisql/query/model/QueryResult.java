package com.wcw.aisql.query.model;

import java.util.List;
import java.util.Map;

public record QueryResult(
        boolean success,
        String message,
        String sql,
        List<Object> parameters,
        SqlAnalysis sqlAnalysis,
        List<Map<String, Object>> data,
        PageInfo pageInfo
) {
    public static QueryResult success(String sql,
                                      List<Object> parameters,
                                      SqlAnalysis sqlAnalysis,
                                      List<Map<String, Object>> data,
                                      PageInfo pageInfo) {
        return new QueryResult(true, "OK", sql, parameters, sqlAnalysis, data, pageInfo);
    }

    public static QueryResult error(String message) {
        return new QueryResult(false, message, null, List.of(), null, List.of(), null);
    }
}
