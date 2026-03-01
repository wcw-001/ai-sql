package com.wcw.aisql.query.service;

import com.wcw.aisql.query.model.QueryResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Repeat;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NaturalLanguageQueryServiceTest {

    @Resource
    private NaturalLanguageQueryService queryService;

    @Test
    void execute() {
        QueryResult result = queryService.execute("查询所有用户");
        System.out.println(result);
    }
}
