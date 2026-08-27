package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraceLogDTO {
    private String traceId;
    private String spanName;
    private long latencyMs;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private double costUsd;
    private int agentIterations;
    private boolean success;
    private String timestamp;
}
