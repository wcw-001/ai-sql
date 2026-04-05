package com.wcw.aisql.query.service;

import com.wcw.aisql.query.config.AiQueryProperties;
import com.wcw.aisql.query.model.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseSchemaService {

    private final DataSource dataSource;
    private final AiQueryProperties properties;
    private final AtomicReference<SchemaCache> cacheRef = new AtomicReference<>();

    public String buildSchemaPrompt() {
        return getSchemaSnapshot().prompt;
    }

    public Map<String, TableMetadata> getTableMetadataMap() {
        return getSchemaSnapshot().metadataMap;
    }

    private SchemaSnapshot getSchemaSnapshot() {
        long now = System.currentTimeMillis();
        SchemaCache cache = cacheRef.get();
        if (isCacheValid(cache, now)) {
            return cache.snapshot;
        }

        synchronized (cacheRef) {
            cache = cacheRef.get();
            if (isCacheValid(cache, now)) {
                return cache.snapshot;
            }

            SchemaSnapshot fresh = loadSchemaSnapshot();
            long ttlMs = Math.max(0, properties.getSchemaCacheSeconds()) * 1000;
            long expiresAtMs = ttlMs == 0 ? now : now + ttlMs;
            cacheRef.set(new SchemaCache(fresh, expiresAtMs));
            return fresh;
        }
    }

    private boolean isCacheValid(SchemaCache cache, long now) {
        return cache != null && now < cache.expiresAtMs;
    }

    private SchemaSnapshot loadSchemaSnapshot() {
        Map<String, MutableTableMetadata> tablesByKey = new TreeMap<>();
        List<String> relations = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();

            loadTables(metaData, catalog, tablesByKey);
            loadColumnsAndRelations(metaData, catalog, tablesByKey, relations);
        } catch (Exception e) {
            log.warn("Failed to load database metadata, fallback to empty schema", e);
        }

        return buildSnapshot(tablesByKey, relations);
    }

    private void loadTables(DatabaseMetaData metaData,
                            String catalog,
                            Map<String, MutableTableMetadata> tablesByKey) throws Exception {
        try (ResultSet tables = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String actualTableName = tables.getString("TABLE_NAME");
                String normalizedKey = actualTableName.toLowerCase(Locale.ROOT);
                String comment = normalizeComment(tables.getString("REMARKS"));
                tablesByKey.put(normalizedKey, new MutableTableMetadata(actualTableName, comment));
            }
        }
    }

    private void loadColumnsAndRelations(DatabaseMetaData metaData,
                                         String catalog,
                                         Map<String, MutableTableMetadata> tablesByKey,
                                         List<String> relations) throws Exception {
        for (MutableTableMetadata table : tablesByKey.values()) {
            try (ResultSet columns = metaData.getColumns(catalog, null, table.actualTableName, "%")) {
                while (columns.next()) {
                    table.columns.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }

            try (ResultSet importedKeys = metaData.getImportedKeys(catalog, null, table.actualTableName)) {
                while (importedKeys.next()) {
                    relations.add("%s.%s -> %s.%s".formatted(
                            table.actualTableName,
                            importedKeys.getString("FKCOLUMN_NAME"),
                            importedKeys.getString("PKTABLE_NAME"),
                            importedKeys.getString("PKCOLUMN_NAME")
                    ));
                }
            }
        }
    }

    private SchemaSnapshot buildSnapshot(Map<String, MutableTableMetadata> tablesByKey, List<String> relations) {
        Map<String, TableMetadata> metadataMap = new LinkedHashMap<>();
        StringBuilder promptBuilder = new StringBuilder("Database schema:\n");

        for (Map.Entry<String, MutableTableMetadata> entry : tablesByKey.entrySet()) {
            String normalizedKey = entry.getKey();
            MutableTableMetadata table = entry.getValue();
            TableMetadata metadata = new TableMetadata(table.actualTableName, table.columns, table.comment);
            metadataMap.put(normalizedKey, metadata);

            promptBuilder.append("Table: ").append(metadata.tableName());
            if (metadata.hasComment()) {
                promptBuilder.append(" (comment: ").append(metadata.comment()).append(")");
            }
            promptBuilder.append('\n');
            promptBuilder.append("  Columns:\n");
            for (String column : metadata.columns()) {
                promptBuilder.append("  - ").append(column).append('\n');
            }
            promptBuilder.append('\n');
        }

        if (!relations.isEmpty()) {
            promptBuilder.append("Relations:\n");
            relations.stream().sorted().forEach(relation -> promptBuilder.append("- ").append(relation).append('\n'));
        }

        return new SchemaSnapshot(promptBuilder.toString().trim(), Map.copyOf(metadataMap));
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record SchemaCache(SchemaSnapshot snapshot, long expiresAtMs) {
    }

    private record SchemaSnapshot(String prompt, Map<String, TableMetadata> metadataMap) {
    }

    private static final class MutableTableMetadata {

        private final String actualTableName;
        private final String comment;
        private final TreeSet<String> columns = new TreeSet<>();

        private MutableTableMetadata(String actualTableName, String comment) {
            this.actualTableName = actualTableName;
            this.comment = comment;
        }
    }
}
