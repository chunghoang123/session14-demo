package com.example.demo.service;

import com.example.demo.dto.McpQueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class McpService {

    private final SafeSqlValidator safeSqlValidator;
    private final JdbcTemplate jdbcTemplate;
    private final LLMOpsService llmOpsService;

    public McpQueryResponse processMcpQuery(String naturalQuery, String rawSql) {
        long startTime = System.currentTimeMillis();

        // Ensure Stdio pollution protection: log strictly to System.err
        logToStderr("[MCP Stdio Guard] Processing query under Stdio Protocol JSON-RPC framing safety.");

        String sqlToValidate;
        if (rawSql != null && !rawSql.isBlank()) {
            sqlToValidate = rawSql;
        } else {
            sqlToValidate = translateNaturalLanguageToSql(naturalQuery);
        }

        // Validate SQL with AST/Safe rules
        SafeSqlValidator.ValidationResult valResult = safeSqlValidator.validateAndSanitize(sqlToValidate);

        if (!valResult.isValid) {
            logToStderr("[MCP Stdio Guard] Safe SQL Validator blocked query: " + valResult.reason);
            long executionTimeMs = System.currentTimeMillis() - startTime;
            llmOpsService.recordCall("McpQuery", 110, 60, executionTimeMs, 1, false);

            return McpQueryResponse.builder()
                    .naturalQuery(naturalQuery)
                    .generatedSql(sqlToValidate)
                    .safeSqlValidated(false)
                    .validationMessage(valResult.reason)
                    .queryResults(Collections.emptyList())
                    .stdioTransportStatus("ACTIVE_JSON_RPC_PROTECTED")
                    .stdioLogTarget("System.err (stdout reserved for JSON-RPC)")
                    .executionTimeMs(executionTimeMs)
                    .build();
        }

        logToStderr("[MCP Stdio Guard] Query passed AST validation: " + valResult.sanitizedSql);

        // Execute query safely
        List<Map<String, Object>> results;
        try {
            results = jdbcTemplate.queryForList(valResult.sanitizedSql);
        } catch (Exception e) {
            logToStderr("[MCP Stdio Guard] SQL execution error: " + e.getMessage());
            long executionTimeMs = System.currentTimeMillis() - startTime;
            llmOpsService.recordCall("McpQuery", 110, 60, executionTimeMs, 1, false);

            return McpQueryResponse.builder()
                    .naturalQuery(naturalQuery)
                    .generatedSql(valResult.sanitizedSql)
                    .safeSqlValidated(true)
                    .validationMessage("Execution error: " + e.getMessage())
                    .queryResults(Collections.emptyList())
                    .stdioTransportStatus("ACTIVE_JSON_RPC_PROTECTED")
                    .stdioLogTarget("System.err (stdout reserved for JSON-RPC)")
                    .executionTimeMs(executionTimeMs)
                    .build();
        }

        long executionTimeMs = System.currentTimeMillis() - startTime;
        llmOpsService.recordCall("McpQuery", 180, 140, executionTimeMs, 1, true);

        return McpQueryResponse.builder()
                .naturalQuery(naturalQuery)
                .generatedSql(valResult.sanitizedSql)
                .safeSqlValidated(true)
                .validationMessage("Query executed safely with LIMIT 100 auto-enforced.")
                .queryResults(results)
                .stdioTransportStatus("ACTIVE_JSON_RPC_PROTECTED")
                .stdioLogTarget("System.err (stdout reserved for JSON-RPC)")
                .executionTimeMs(executionTimeMs)
                .build();
    }

    private String translateNaturalLanguageToSql(String query) {
        if (query == null) return "SELECT * FROM deliveries";
        String lower = query.toLowerCase();

        if (lower.contains("bưu cục") || lower.contains("hn-01") || lower.contains("trễ")) {
            if (lower.contains("bao nhiêu") || lower.contains("đếm") || lower.contains("số lượng")) {
                return "SELECT count(*) AS total_delayed FROM deliveries WHERE hub_code = 'HN-01' AND status = 'DELAYED'";
            }
            return "SELECT * FROM deliveries WHERE hub_code = 'HN-01' AND status = 'DELAYED'";
        }

        if (lower.contains("hỏng") || lower.contains("damaged")) {
            return "SELECT * FROM deliveries WHERE status = 'DAMAGED'";
        }

        if (lower.contains("sự cố") || lower.contains("incident")) {
            return "SELECT * FROM incidents ORDER BY id DESC";
        }

        return "SELECT * FROM deliveries";
    }

    private void logToStderr(String message) {
        PrintStream err = System.err;
        err.println(message);
    }
}
