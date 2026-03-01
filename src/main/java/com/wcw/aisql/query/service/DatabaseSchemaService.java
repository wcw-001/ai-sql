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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseSchemaService {

    private final DataSource dataSource;
    private final AiQueryProperties properties;
    private final AtomicReference<SchemaCache> cacheRef = new AtomicReference<>();

    public String buildSchemaPrompt() {
        return "Database schema:\n" + getSchemaSnapshot().prompt;
    }

    public Map<String, TableMetadata> getTableMetadataMap() {
        return getSchemaSnapshot().metadataMap;
    }

    private SchemaSnapshot getSchemaSnapshot() {
        SchemaCache cache = cacheRef.get();
        long now = System.currentTimeMillis();
        if (cache != null && now < cache.expiresAtMs) {
            return cache.snapshot;
        }
        SchemaSnapshot fresh = loadSchemaSnapshot();
        long expiresAt = now + properties.getSchemaCacheSeconds() * 1000;
        cacheRef.set(new SchemaCache(fresh, expiresAt));
        return fresh;
    }

    private SchemaSnapshot loadSchemaSnapshot() {
        Map<String, Set<String>> columnsByTable = new HashMap<>();
        Map<String, String> actualTableNames = new HashMap<>();
        List<String> relations = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();

            try (ResultSet tables = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String table = tables.getString("TABLE_NAME");
                    String key = table.toLowerCase(Locale.ROOT);
                    columnsByTable.put(key, new HashSet<>());
                    actualTableNames.put(key, table);
                }
            }

            for (String table : columnsByTable.keySet()) {
                String actualName = actualTableNames.getOrDefault(table, table);
                try (ResultSet columns = meta.getColumns(catalog, null, actualName, "%")) {
                    while (columns.next()) {
                        String col = columns.getString("COLUMN_NAME");
                        columnsByTable.get(table).add(col.toLowerCase(Locale.ROOT));
                    }
                }
                try (ResultSet fks = meta.getImportedKeys(catalog, null, actualName)) {
                    while (fks.next()) {
                        String fkCol = fks.getString("FKCOLUMN_NAME");
                        String pkTable = fks.getString("PKTABLE_NAME");
                        String pkCol = fks.getString("PKCOLUMN_NAME");
                        relations.add(actualName + "." + fkCol + " -> " + pkTable + "." + pkCol);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load database metadata, fallback to empty schema", e);
        }

        Map<String, TableMetadata> metadataMap = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        columnsByTable.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    String table = entry.getKey();
                    List<String> cols = entry.getValue().stream().sorted().toList();
                    metadataMap.put(table, new TableMetadata(table, entry.getValue()));
                    sb.append("Table ").append(table).append('\n');
                    for (String col : cols) {
                        sb.append("- ").append(col).append('\n');
                    }
                    sb.append('\n');
                });

        if (!relations.isEmpty()) {
            sb.append("Relations:\n");
            relations.stream().sorted().forEach(relation -> sb.append("- ").append(relation).append('\n'));
        }
        return new SchemaSnapshot(sb.toString().trim(), metadataMap);
    }

    private record SchemaCache(SchemaSnapshot snapshot, long expiresAtMs) {
    }

    private record SchemaSnapshot(String prompt, Map<String, TableMetadata> metadataMap) {
    }
}
