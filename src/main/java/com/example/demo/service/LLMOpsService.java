package com.example.demo.service;

import com.example.demo.dto.TelemetryStatsDTO;
import com.example.demo.dto.TraceLogDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class LLMOpsService {

    private final Deque<TraceLogDTO> traceLogs = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    // Cost rates per 1K tokens ($0.0005 prompt, $0.0015 completion for light models)
    private static final double PROMPT_COST_PER_1K = 0.0005;
    private static final double COMPLETION_COST_PER_1K = 0.0015;

    public void recordCall(String spanName, int promptTokens, int completionTokens, long latencyMs, int agentIterations, boolean success) {
        String traceId = "tr-" + UUID.randomUUID().toString().substring(0, 8);
        double cost = ((promptTokens / 1000.0) * PROMPT_COST_PER_1K) + ((completionTokens / 1000.0) * COMPLETION_COST_PER_1K);
        int totalTokens = promptTokens + completionTokens;

        TraceLogDTO trace = TraceLogDTO.builder()
                .traceId(traceId)
                .spanName(spanName)
                .latencyMs(latencyMs)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .costUsd(Math.round(cost * 100000.0) / 100000.0)
                .agentIterations(agentIterations)
                .success(success)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        traceLogs.addFirst(trace);
        if (traceLogs.size() > 50) {
            traceLogs.removeLast();
        }

        totalRequests.incrementAndGet();
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalLatencyMs.addAndGet(latencyMs);

        log.info("[LLMOps Telemetry] Span={} | Latency={}ms | PromptTok={} | CompTok={} | Cost=${} | Trace={}",
                spanName, latencyMs, promptTokens, completionTokens, String.format("%.5f", cost), traceId);
    }

    public TelemetryStatsDTO getStats() {
        long reqs = totalRequests.get();
        long pTok = totalPromptTokens.get();
        long cTok = totalCompletionTokens.get();
        long totTok = pTok + cTok;
        double avgLat = reqs > 0 ? (double) totalLatencyMs.get() / reqs : 0.0;
        double totalCost = ((pTok / 1000.0) * PROMPT_COST_PER_1K) + ((cTok / 1000.0) * COMPLETION_COST_PER_1K);

        int maxIter = traceLogs.stream()
                .mapToInt(TraceLogDTO::getAgentIterations)
                .max()
                .orElse(1);

        boolean slaRagPassed = traceLogs.stream()
                .filter(t -> t.getSpanName().contains("Rag"))
                .allMatch(t -> t.getLatencyMs() < 3000);

        boolean slaAgentPassed = traceLogs.stream()
                .filter(t -> t.getSpanName().contains("Agent"))
                .allMatch(t -> t.getLatencyMs() < 5000);

        List<TraceLogDTO> recentList = new ArrayList<>(traceLogs.stream().limit(15).toList());

        return TelemetryStatsDTO.builder()
                .totalRequests(reqs)
                .totalPromptTokens(pTok)
                .totalCompletionTokens(cTok)
                .totalTokens(totTok)
                .totalCostUsd(Math.round(totalCost * 100000.0) / 100000.0)
                .avgLatencyMs(Math.round(avgLat * 10.0) / 10.0)
                .maxAgentIterationsObserved(maxIter)
                .slaRagPassed(slaRagPassed)
                .slaAgentPassed(slaAgentPassed)
                .recentTraces(recentList)
                .build();
    }
}
