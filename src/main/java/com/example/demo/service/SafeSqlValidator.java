package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SafeSqlValidator {

    private static final Pattern FORBIDDEN_WORDS = Pattern.compile(
            "\\b(DELETE|UPDATE|DROP|ALTER|INSERT|TRUNCATE|EXEC|EXECUTE|CREATE|GRANT|REVOKE|UNION|INTO)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public static class ValidationResult {
        public final boolean isValid;
        public final String sanitizedSql;
        public final String reason;

        public ValidationResult(boolean isValid, String sanitizedSql, String reason) {
            this.isValid = isValid;
            this.sanitizedSql = sanitizedSql;
            this.reason = reason;
        }
    }

    public ValidationResult validateAndSanitize(String inputSql) {
        if (inputSql == null || inputSql.isBlank()) {
            return new ValidationResult(false, "", "SQL query cannot be empty.");
        }

        String trimmed = inputSql.trim();

        // Check for multiple statements separated by semicolon
        if (trimmed.contains(";") && trimmed.indexOf(';') != trimmed.length() - 1) {
            return new ValidationResult(false, "", "Multiple SQL statements separated by ';' are strictly forbidden.");
        }

        // Must start with SELECT
        if (!trimmed.toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            return new ValidationResult(false, "", "Only SELECT queries are allowed by MCP Safe SQL Policy.");
        }

        // Check forbidden mutation keywords
        if (FORBIDDEN_WORDS.matcher(trimmed).find()) {
            return new ValidationResult(false, "", "SQL contains forbidden mutating keyword (UPDATE/DELETE/DROP/ALTER/etc.).");
        }

        // Auto append LIMIT 100 if not present
        String finalSql = trimmed.replaceAll(";$", "");
        if (!finalSql.toUpperCase(Locale.ROOT).contains("LIMIT")) {
            finalSql += " LIMIT 100";
        }

        return new ValidationResult(true, finalSql, "VALID");
    }
}
