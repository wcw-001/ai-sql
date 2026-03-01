package com.wcw.aisql.query.model;

import java.util.List;

public record SqlAnalysis(
        int score,
        String level,
        List<String> risks,
        List<String> suggestions
) {
}
