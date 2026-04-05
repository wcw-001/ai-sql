package com.wcw.aisql.query.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record TableMetadata(String tableName, Set<String> columns, String comment) {

    public TableMetadata {
        tableName = tableName == null ? "" : tableName;
        columns = normalizeColumns(columns);
        comment = normalizeComment(comment);
    }

    public TableMetadata(String tableName, Set<String> columns) {
        this(tableName, columns, null);
    }

    public boolean hasColumn(String columnName) {
        if (columnName == null) {
            return false;
        }
        return columns.contains(columnName.toLowerCase(Locale.ROOT));
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

    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmedComment = comment.trim();
        return trimmedComment.isEmpty() ? null : trimmedComment;
    }
}
