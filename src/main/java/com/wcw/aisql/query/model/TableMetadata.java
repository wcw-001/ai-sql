package com.wcw.aisql.query.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record TableMetadata(String tableName,
                            Set<String> columns,
                            String comment,
                            Map<String, ColumnMetadata> columnDetails) {

    public TableMetadata {
        tableName = tableName == null ? "" : tableName;
        columns = normalizeColumns(columns);
        comment = normalizeComment(comment);
        columnDetails = normalizeColumnDetails(columnDetails);
    }

    public TableMetadata(String tableName, Set<String> columns) {
        this(tableName, columns, null, Map.of());
    }

    public TableMetadata(String tableName, Set<String> columns, String comment) {
        this(tableName, columns, comment, Map.of());
    }

    public boolean hasColumn(String columnName) {
        if (columnName == null) {
            return false;
        }
        return columns.contains(columnName.toLowerCase(Locale.ROOT));
    }

    public ColumnMetadata getColumn(String columnName) {
        if (columnName == null) {
            return null;
        }
        return columnDetails.get(columnName.toLowerCase(Locale.ROOT));
    }

    public boolean hasComment() {
        return comment != null;
    }

    private static Set<String> normalizeColumns(Set<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalizedColumns = new LinkedHashSet<>();
        for (String column : columns) {
            if (column != null) {
                normalizedColumns.add(column.toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(normalizedColumns);
    }

    private static Map<String, ColumnMetadata> normalizeColumnDetails(Map<String, ColumnMetadata> columnDetails) {
        if (columnDetails == null || columnDetails.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, ColumnMetadata> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ColumnMetadata> entry : columnDetails.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmedComment = comment.trim();
        return trimmedComment.isEmpty() ? null : trimmedComment;
    }
}
