package com.example.demo;

import com.example.demo.dto.*;
import com.example.demo.model.*;
import com.example.demo.repository.DeliveryRepository;
import com.example.demo.repository.IncidentRepository;
import com.example.demo.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SmartHubIntegrationTests {

    private RagService ragService;
    private LLMOpsService llmOpsService;
    private SafeSqlValidator safeSqlValidator;
    private LogisticsTools logisticsTools;
    private AgentService agentService;

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @BeforeEach
    void setUp() {
        ragService = new RagService();
        llmOpsService = new LLMOpsService();
        safeSqlValidator = new SafeSqlValidator();
        logisticsTools = new LogisticsTools(deliveryRepository, incidentRepository);
        agentService = new AgentService(deliveryRepository, logisticsTools, llmOpsService);
    }

    // ============================
    // MODULE 1: RAG TESTS (RQ-01)
    // ============================

    @Test
    @DisplayName("Module 1 - RAG: Returns policy details with citations for a valid query")
    void testRagAskReturnsValidAnswer() {
        RagResponse response = ragService.askPolicy(
                "Đơn giao trễ được bồi thường như thế nào?",
                500, 10, 0.45
        );

        assertNotNull(response);
        assertTrue(response.isFoundInDocuments(), "RAG should find relevant documents");
        assertFalse(response.getCitations().isEmpty(), "RAG should return citations");
        assertTrue(response.getAnswer().contains("RikkeiExpress"), "Answer should reference RikkeiExpress");
        assertTrue(response.getExecutionTimeMs() < 3000, "RAG SLA: must respond in < 3 seconds");

        // Verify citation structure
        RagResponse.Citation citation = response.getCitations().get(0);
        assertNotNull(citation.getDocumentName());
        assertNotNull(citation.getSection());
        assertNotNull(citation.getPageNumber());
    }

    @Test
    @DisplayName("Module 1 - RAG: Returns 'not found' without hallucination for irrelevant query")
    void testRagAntiHallucination() {
        RagResponse response = ragService.askPolicy(
                "Giá cổ phiếu Apple hôm nay bao nhiêu?",
                500, 10, 0.95
        );

        assertNotNull(response);
        assertFalse(response.isFoundInDocuments(), "RAG should NOT find info for irrelevant query");
        assertTrue(response.getAnswer().contains("Không tìm thấy"), "Should explicitly say 'not found'");
        assertTrue(response.getCitations().isEmpty(), "No citations for unknown queries");
    }

    @Test
    @DisplayName("Module 1 - RAG RQ-01: Different chunk sizes produce valid results")
    void testRagChunkSizeVariations() {
        for (int chunkSize : new int[]{300, 500, 1000}) {
            RagResponse response = ragService.askPolicy(
                    "Hàng hóa bị ướt hỏng bồi thường ra sao?",
                    chunkSize, 10, 0.45
            );
            assertNotNull(response, "ChunkSize=" + chunkSize + " should return a valid response");
            assertTrue(response.isFoundInDocuments(), "ChunkSize=" + chunkSize + " should find documents");
            assertEquals(chunkSize, response.getChunkConfig().getChunkSize());
        }
    }

    // ============================
    // MODULE 2: AI AGENT TESTS (RQ-02)
    // ============================

    @Test
    @DisplayName("Module 2 - Agent: Extracts entities and calls tools for valid complaint")
    void testAgentProcessValidComplaint() {
        Delivery mockDelivery = Delivery.builder()
                .id(1L)
                .trackingCode("RK-2026-001")
                .customerName("Nguyễn Văn A")
                .hubCode("HN-01")
                .status(DeliveryStatus.IN_TRANSIT)
                .codAmount(new BigDecimal("500000"))
                .build();

        when(deliveryRepository.findByTrackingCode("RK-2026-001"))
                .thenReturn(Optional.of(mockDelivery));
        when(incidentRepository.save(any(Incident.class)))
                .thenAnswer(inv -> {
                    Incident saved = inv.getArgument(0);
                    saved.setId(100L);
                    return saved;
                });
        when(deliveryRepository.save(any(Delivery.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OperationChatResponse response = agentService.processOperationMessage(
                "Đơn RK-2026-001 gửi tuần trước bị ướt hỏng hàng ở kho Hà Nội, kiểm tra giúp tôi."
        );

        assertNotNull(response);
        assertTrue(response.isSuccess(), "Agent should succeed for valid tracking code");

        // Verify entity extraction (RQ-02)
        OperationChatResponse.ExtractedEntities entities = response.getExtractedEntities();
        assertEquals("RK-2026-001", entities.getTrackingCode());
        assertEquals(IncidentType.HỎNG_HÓC, entities.getIncidentType());
        assertEquals("HN-01", entities.getHubCode());
        assertEquals(IncidentSeverity.CRITICAL, entities.getSeverity());

        // Verify tool execution logs
        assertFalse(response.getToolExecutionLogs().isEmpty(), "Tool logs should be populated");
        assertTrue(response.getToolExecutionLogs().stream()
                .anyMatch(t -> t.getToolName().equals("createIncidentTool")));
        assertTrue(response.getToolExecutionLogs().stream()
                .anyMatch(t -> t.getToolName().equals("updateDeliveryStatusTool")));

        // Verify max iterations guard
        assertTrue(response.getAgentIterations() <= 6, "Agent iterations must be <= 6");

        // Verify DB calls were made
        verify(incidentRepository, times(1)).save(any(Incident.class));
        verify(deliveryRepository, atLeastOnce()).save(any(Delivery.class));
    }

    @Test
    @DisplayName("Module 2 - Agent: Returns friendly error for non-existent tracking code (no crash)")
    void testAgentInvalidTrackingCode() {
        when(deliveryRepository.findByTrackingCode("RK-9999-999"))
                .thenReturn(Optional.empty());

        OperationChatResponse response = agentService.processOperationMessage(
                "Đơn RK-9999-999 bị trễ 5 ngày rồi."
        );

        assertNotNull(response);
        assertFalse(response.isSuccess(), "Should fail gracefully for invalid code");
        assertTrue(response.getResponseMessage().contains("không tồn tại"),
                "Should contain friendly error message in Vietnamese");
    }

    @Test
    @DisplayName("Module 2 - Agent: Returns error when no tracking code found in message")
    void testAgentNoTrackingCodeInMessage() {
        OperationChatResponse response = agentService.processOperationMessage(
                "Tôi muốn hỏi về đơn hàng bị hỏng."
        );

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getResponseMessage().contains("mã đơn hàng"));
    }

    // ============================
    // MODULE 3: MCP & SAFE SQL TESTS (RQ-03)
    // ============================

    @Test
    @DisplayName("Module 3 - Safe SQL: Allows SELECT and auto-appends LIMIT 100")
    void testSafeSqlAllowsSelect() {
        SafeSqlValidator.ValidationResult result =
                safeSqlValidator.validateAndSanitize("SELECT * FROM deliveries");

        assertTrue(result.isValid, "SELECT queries must be allowed");
        assertTrue(result.sanitizedSql.contains("LIMIT 100"),
                "LIMIT 100 must be auto-appended if missing");
    }

    @Test
    @DisplayName("Module 3 - Safe SQL: Preserves existing LIMIT clause")
    void testSafeSqlPreservesExistingLimit() {
        SafeSqlValidator.ValidationResult result =
                safeSqlValidator.validateAndSanitize("SELECT * FROM deliveries LIMIT 50");

        assertTrue(result.isValid);
        assertTrue(result.sanitizedSql.contains("LIMIT 50"));
        assertFalse(result.sanitizedSql.contains("LIMIT 100"),
                "Should NOT add LIMIT 100 when LIMIT already exists");
    }

    @Test
    @DisplayName("Module 3 - Safe SQL: Blocks DELETE statement")
    void testSafeSqlBlocksDelete() {
        SafeSqlValidator.ValidationResult result =
                safeSqlValidator.validateAndSanitize("DELETE FROM deliveries");

        assertFalse(result.isValid, "DELETE must be blocked");
        assertTrue(result.reason.toLowerCase().contains("select"),
                "Error should mention only SELECT allowed");
    }

    @Test
    @DisplayName("Module 3 - Safe SQL: Blocks UPDATE statement")
    void testSafeSqlBlocksUpdate() {
        SafeSqlValidator.ValidationResult result =
                safeSqlValidator.validateAndSanitize("UPDATE deliveries SET status='DAMAGED'");

        assertFalse(result.isValid, "UPDATE must be blocked");
    }

    @Test
    @DisplayName("Module 3 - Safe SQL: Blocks DROP TABLE")
    void testSafeSqlBlocksDropTable() {
        SafeSqlValidator.ValidationResult result =
                safeSqlValidator.validateAndSanitize("DROP TABLE deliveries");

        assertFalse(result.isValid, "DROP must be blocked");
    }

    @Test
    @DisplayName("Module 3 - Safe SQL: Blocks multi-statement injection")
    void testSafeSqlBlocksMultiStatement() {
        SafeSqlValidator.ValidationResult result =
                safeSqlValidator.validateAndSanitize("SELECT 1; DROP TABLE deliveries");

        assertFalse(result.isValid, "Multi-statement SQL injection must be blocked");
    }

    @Test
    @DisplayName("Module 3 - Safe SQL: Blocks empty query")
    void testSafeSqlBlocksEmpty() {
        SafeSqlValidator.ValidationResult result =
                safeSqlValidator.validateAndSanitize("");

        assertFalse(result.isValid, "Empty SQL must be blocked");
    }

    // ============================
    // MODULE 4: LLMOPS TELEMETRY TESTS (RQ-04)
    // ============================

    @Test
    @DisplayName("Module 4 - LLMOps: Records and retrieves telemetry stats")
    void testTelemetryRecordAndRetrieve() {
        llmOpsService.recordCall("RagQuery", 200, 150, 1500, 1, true);
        llmOpsService.recordCall("AgentProcess", 300, 250, 2500, 3, true);

        TelemetryStatsDTO stats = llmOpsService.getStats();

        assertNotNull(stats);
        assertEquals(2, stats.getTotalRequests());
        assertEquals(500, stats.getTotalPromptTokens());    // 200 + 300
        assertEquals(400, stats.getTotalCompletionTokens()); // 150 + 250
        assertEquals(900, stats.getTotalTokens());           // 500 + 400
        assertTrue(stats.getTotalCostUsd() > 0, "Cost should be > $0");
        assertTrue(stats.getAvgLatencyMs() > 0, "Average latency should be > 0");
        assertEquals(3, stats.getMaxAgentIterationsObserved());

        // Verify recent traces contain our entries
        assertFalse(stats.getRecentTraces().isEmpty());
    }

    @Test
    @DisplayName("Module 4 - LLMOps: Agent iteration guard limit is <= 6")
    void testTelemetryMaxIterationGuard() {
        llmOpsService.recordCall("AgentProcess", 100, 80, 1200, 6, true);

        TelemetryStatsDTO stats = llmOpsService.getStats();

        assertTrue(stats.getMaxAgentIterationsObserved() <= 6,
                "Max agent iterations observed must never exceed 6");
    }

    @Test
    @DisplayName("Module 4 - LLMOps: Trace log captures all required fields")
    void testTelemetryTraceLogFields() {
        llmOpsService.recordCall("TestSpan", 120, 90, 800, 2, true);

        TelemetryStatsDTO stats = llmOpsService.getStats();
        TraceLogDTO trace = stats.getRecentTraces().get(0);

        assertNotNull(trace.getTraceId(), "Trace ID must not be null");
        assertTrue(trace.getTraceId().startsWith("tr-"), "Trace ID should start with 'tr-'");
        assertEquals("TestSpan", trace.getSpanName());
        assertEquals(800, trace.getLatencyMs());
        assertEquals(120, trace.getPromptTokens());
        assertEquals(90, trace.getCompletionTokens());
        assertEquals(210, trace.getTotalTokens());
        assertTrue(trace.getCostUsd() > 0);
        assertEquals(2, trace.getAgentIterations());
        assertTrue(trace.isSuccess());
        assertNotNull(trace.getTimestamp());
    }
}
