package com.wcw.aisql.query.controller;

import com.wcw.aisql.query.common.BaseResponse;
import com.wcw.aisql.query.common.ErrorCode;
import com.wcw.aisql.query.common.ResultUtils;
import com.wcw.aisql.query.model.QueryResult;
import com.wcw.aisql.query.service.NaturalLanguageQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/query")
public class NaturalLanguageQueryController {

    private final NaturalLanguageQueryService queryService;

    @GetMapping("/natural")
    public BaseResponse<QueryResult> naturalQuery(
            @RequestParam("q") @NotBlank String query,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(200) int pageSize
    ) {
        QueryResult result = queryService.execute(query, page, pageSize);
        if (result.success()) {
            return ResultUtils.success(result);
        }
        return ResultUtils.error(400000, result.message());
    }
}
