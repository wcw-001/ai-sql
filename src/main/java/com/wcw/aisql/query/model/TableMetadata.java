package com.wcw.aisql.query.model;

import java.util.Set;

public record TableMetadata(String tableName, Set<String> columns) {

    public boolean hasColumn(String columnName) {
        return columns.contains(columnName.toLowerCase());
    }
}
