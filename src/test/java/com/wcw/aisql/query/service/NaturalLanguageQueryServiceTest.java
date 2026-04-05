package com.wcw.aisql.query.service;

import com.wcw.aisql.query.config.AiQueryProperties;
import com.wcw.aisql.query.model.QueryResult;
import com.wcw.aisql.query.model.TableMetadata;
import com.wcw.aisql.query.security.SqlSchemaValidator;
import com.wcw.aisql.query.security.SqlSecurityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NaturalLanguageQueryServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SqlSecurityValidator sqlSecurityValidator;

    private AiQueryProperties properties;

    @Mock
    private DatabaseSchemaService databaseSchemaService;

    @Mock
    private DataPermissionService dataPermissionService;

    @Mock
    private QueryAuditService queryAuditService;

    private NaturalLanguageQueryService naturalLanguageQueryService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        properties = new AiQueryProperties();
        properties.setQueryPlanCacheSeconds(60);
        properties.setSqlGenerationMaxAttempts(3);

        naturalLanguageQueryService = new NaturalLanguageQueryService(
                chatClientBuilder,
                jdbcTemplate,
                sqlSecurityValidator,
                new SqlSchemaValidator(),
                properties,
                databaseSchemaService,
                dataPermissionService,
                queryAuditService
        );
    }

    @Test
    void executeShouldReturnPagedResult() {
        when(databaseSchemaService.buildSchemaPrompt()).thenReturn("Table: users\n  - id");
        when(databaseSchemaService.getTableMetadataMap())
                .thenReturn(Map.of("users", new TableMetadata("users", Set.of("id"))));
        when(responseSpec.content()).thenReturn("```sql\nSELECT * FROM users;\n```");
        when(dataPermissionService.apply("SELECT * FROM users")).thenReturn("SELECT id FROM users LIMIT 10");
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM (SELECT id FROM users) AS total_rows"), eq(Long.class)))
                .thenReturn(25L);
        when(jdbcTemplate.queryForList("SELECT id FROM users LIMIT 10 OFFSET 10"))
                .thenReturn(List.of(Map.of("id", 11), Map.of("id", 12)));

        QueryResult result = naturalLanguageQueryService.execute("查询用户", 2, 10);

        assertTrue(result.success());
        assertEquals("SELECT id FROM users LIMIT 10 OFFSET 10", result.sql());
        assertEquals(2, result.data().size());
        assertEquals(25L, result.pageInfo().total());
        assertEquals(3L, result.pageInfo().totalPages());
        assertEquals(2, result.pageInfo().page());
        assertTrue(result.pageInfo().hasPrevious());
        assertTrue(result.pageInfo().hasNext());
        verify(sqlSecurityValidator).validate("SELECT id FROM users");
        verify(queryAuditService).logGeneratedSql("查询用户", "SELECT id FROM users LIMIT 10 OFFSET 10");
        verify(chatClientBuilder).build();
    }

    @Test
    void executeShouldReuseCachedPlanForSameQuery() {
        when(databaseSchemaService.buildSchemaPrompt()).thenReturn("Table: users\n  - id");
        when(databaseSchemaService.getTableMetadataMap())
                .thenReturn(Map.of("users", new TableMetadata("users", Set.of("id"))));
        when(responseSpec.content()).thenReturn("SELECT id FROM users");
        when(dataPermissionService.apply("SELECT id FROM users")).thenReturn("SELECT id FROM users");
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM (SELECT id FROM users) AS total_rows"), eq(Long.class)))
                .thenReturn(25L);
        when(jdbcTemplate.queryForList("SELECT id FROM users LIMIT 10 OFFSET 0"))
                .thenReturn(List.of(Map.of("id", 1)));
        when(jdbcTemplate.queryForList("SELECT id FROM users LIMIT 10 OFFSET 10"))
                .thenReturn(List.of(Map.of("id", 11)));

        QueryResult firstPage = naturalLanguageQueryService.execute("查询用户", 1, 10);
        QueryResult secondPage = naturalLanguageQueryService.execute("查询用户", 2, 10);

        assertTrue(firstPage.success());
        assertTrue(secondPage.success());
        assertEquals("SELECT id FROM users LIMIT 10 OFFSET 0", firstPage.sql());
        assertEquals("SELECT id FROM users LIMIT 10 OFFSET 10", secondPage.sql());
        verify(responseSpec, times(1)).content();
        verify(jdbcTemplate, times(1))
                .queryForObject("SELECT COUNT(*) FROM (SELECT id FROM users) AS total_rows", Long.class);
        verify(sqlSecurityValidator, times(1)).validate("SELECT id FROM users");
        verify(queryAuditService, times(2)).logGeneratedSql(eq("查询用户"), any());
    }

    @Test
    void executeShouldRetryWhenSchemaValidationFails() {
        when(databaseSchemaService.buildSchemaPrompt()).thenReturn("Table: users\n  - id\n  - name");
        when(databaseSchemaService.getTableMetadataMap())
                .thenReturn(Map.of("users", new TableMetadata("users", Set.of("id", "name"))));
        when(responseSpec.content()).thenReturn("SELECT user_name FROM users", "SELECT name FROM users");
        when(dataPermissionService.apply("SELECT user_name FROM users")).thenReturn("SELECT user_name FROM users");
        when(dataPermissionService.apply("SELECT name FROM users")).thenReturn("SELECT name FROM users");
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM (SELECT name FROM users) AS total_rows"), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList("SELECT name FROM users LIMIT 10 OFFSET 0"))
                .thenReturn(List.of(Map.of("name", "alice")));

        QueryResult result = naturalLanguageQueryService.execute("查询用户名", 1, 10);

        assertTrue(result.success());
        assertEquals("SELECT name FROM users LIMIT 10 OFFSET 0", result.sql());
        verify(responseSpec, times(2)).content();
        verify(sqlSecurityValidator, times(2)).validate(anyString());
        verify(jdbcTemplate, times(1))
                .queryForObject("SELECT COUNT(*) FROM (SELECT name FROM users) AS total_rows", Long.class);
    }
}
