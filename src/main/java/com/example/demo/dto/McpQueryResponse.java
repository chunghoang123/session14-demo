package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpQueryResponse {
    private String naturalQuery;
    private String generatedSql;
    private boolean safeSqlValidated;
    private String validationMessage;
    private List<Map<String, Object>> queryResults;
    private String stdioTransportStatus;
    private String stdioLogTarget; // System.err enforced
    private long executionTimeMs;
}
