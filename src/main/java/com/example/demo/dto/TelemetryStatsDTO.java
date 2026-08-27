package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryStatsDTO {
    private long totalRequests;
    private long totalPromptTokens;
    private long totalCompletionTokens;
    private long totalTokens;
    private double totalCostUsd;
    private double avgLatencyMs;
    private int maxAgentIterationsObserved;
    private boolean slaRagPassed;
    private boolean slaAgentPassed;
    private List<TraceLogDTO> recentTraces;
}
