package com.example.demo.dto;

import com.example.demo.model.DeliveryStatus;
import com.example.demo.model.IncidentSeverity;
import com.example.demo.model.IncidentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationChatResponse {
    private String responseMessage;
    private ExtractedEntities extractedEntities;
    private List<ToolExecutionLog> toolExecutionLogs;
    private boolean success;
    private int agentIterations;
    private long executionTimeMs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtractedEntities {
        private String trackingCode;
        private IncidentType incidentType;
        private String hubCode;
        private IncidentSeverity severity;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ToolExecutionLog {
        private String toolName;
        private String arguments;
        private String result;
        private boolean success;
    }
}
