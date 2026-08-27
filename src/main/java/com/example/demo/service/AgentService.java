package com.example.demo.service;

import com.example.demo.dto.OperationChatResponse;
import com.example.demo.model.*;
import com.example.demo.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final DeliveryRepository deliveryRepository;
    private final LogisticsTools logisticsTools;
    private final LLMOpsService llmOpsService;

    private static final int MAX_ITERATIONS = 6;

    public OperationChatResponse processOperationMessage(String userMessage) {
        long startTime = System.currentTimeMillis();
        List<OperationChatResponse.ToolExecutionLog> toolLogs = new ArrayList<>();
        int iterations = 0;

        // Step 1: Entity Extraction from Natural Language
        String trackingCode = extractTrackingCode(userMessage);
        IncidentType incidentType = extractIncidentType(userMessage);
        String hubCode = extractHubCode(userMessage);
        IncidentSeverity severity = extractSeverity(userMessage);
        String description = userMessage;

        OperationChatResponse.ExtractedEntities entities = OperationChatResponse.ExtractedEntities.builder()
                .trackingCode(trackingCode)
                .incidentType(incidentType)
                .hubCode(hubCode)
                .severity(severity)
                .description(description)
                .build();

        // Check iteration safety
        iterations++;
        if (iterations > MAX_ITERATIONS) {
            log.warn("[AgentService] Agent iteration limit reached ({})", MAX_ITERATIONS);
        }

        // Validate Tracking Code existence
        if (trackingCode == null || trackingCode.isBlank()) {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            llmOpsService.recordCall("AgentProcess", 120, 80, executionTimeMs, 1, false);

            return OperationChatResponse.builder()
                    .responseMessage("Không thể tìm thấy mã đơn hàng (ví dụ: RK-2026-001) trong câu hỏi của bạn. Vui lòng cung cấp mã đơn hàng chính xác.")
                    .extractedEntities(entities)
                    .toolExecutionLogs(toolLogs)
                    .success(false)
                    .agentIterations(iterations)
                    .executionTimeMs(executionTimeMs)
                    .build();
        }

        Optional<Delivery> deliveryOpt = deliveryRepository.findByTrackingCode(trackingCode);
        if (deliveryOpt.isEmpty()) {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            
            toolLogs.add(OperationChatResponse.ToolExecutionLog.builder()
                    .toolName("getDeliveryDetailsTool")
                    .arguments("trackingCode=" + trackingCode)
                    .result("NOT_FOUND")
                    .success(false)
                    .build());

            llmOpsService.recordCall("AgentProcess", 150, 95, executionTimeMs, iterations, false);

            return OperationChatResponse.builder()
                    .responseMessage("Mã đơn hàng [" + trackingCode + "] không tồn tại trên hệ thống RikkeiExpress. Vui lòng kiểm tra lại mã vận đơn!")
                    .extractedEntities(entities)
                    .toolExecutionLogs(toolLogs)
                    .success(false)
                    .agentIterations(iterations)
                    .executionTimeMs(executionTimeMs)
                    .build();
        }

        Delivery delivery = deliveryOpt.get();
        if (hubCode == null) {
            hubCode = delivery.getHubCode();
            entities.setHubCode(hubCode);
        }

        // Step 2: Tool 1 Execution - createIncidentTool
        iterations++;
        String createIncidentResult = logisticsTools.createIncidentTool(
                trackingCode,
                incidentType.name(),
                hubCode,
                severity.name(),
                description
        );

        toolLogs.add(OperationChatResponse.ToolExecutionLog.builder()
                .toolName("createIncidentTool")
                .arguments(String.format("trackingCode=%s, incidentType=%s, hubCode=%s, severity=%s",
                        trackingCode, incidentType, hubCode, severity))
                .result(createIncidentResult)
                .success(createIncidentResult.startsWith("SUCCESS"))
                .build());

        // Step 3: Tool 2 Execution - updateDeliveryStatusTool
        iterations++;
        DeliveryStatus targetStatus = mapIncidentToDeliveryStatus(incidentType);
        String updateStatusResult = logisticsTools.updateDeliveryStatusTool(trackingCode, targetStatus.name());

        toolLogs.add(OperationChatResponse.ToolExecutionLog.builder()
                .toolName("updateDeliveryStatusTool")
                .arguments(String.format("trackingCode=%s, targetStatus=%s", trackingCode, targetStatus))
                .result(updateStatusResult)
                .success(updateStatusResult.startsWith("SUCCESS"))
                .build());

        long executionTimeMs = System.currentTimeMillis() - startTime;
        llmOpsService.recordCall("AgentProcess", 280, 190, executionTimeMs, iterations, true);

        String finalResponse = String.format(
                "Đã xử lý thành công yêu cầu cho đơn hàng [%s]!\n" +
                "• Hệ thống đã tự động tạo sự cố [%s] mức độ [%s] tại hub [%s].\n" +
                "• Trạng thái đơn hàng [%s] đã được cập nhật từ [%s] sang [%s].",
                trackingCode, incidentType, severity, hubCode, trackingCode, delivery.getStatus(), targetStatus
        );

        return OperationChatResponse.builder()
                .responseMessage(finalResponse)
                .extractedEntities(entities)
                .toolExecutionLogs(toolLogs)
                .success(true)
                .agentIterations(iterations)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    private String extractTrackingCode(String message) {
        Matcher m = Pattern.compile("RK-\\d{4}-\\d{3}", Pattern.CASE_INSENSITIVE).matcher(message);
        if (m.find()) {
            return m.group(0).toUpperCase();
        }
        return null;
    }

    private IncidentType extractIncidentType(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("ướt") || lower.contains("hỏng") || lower.contains("vỡ") || lower.contains("hư")) {
            return IncidentType.HỎNG_HÓC;
        }
        if (lower.contains("trễ") || lower.contains("chậm") || lower.contains("muộn")) {
            return IncidentType.GIAO_TRỄ;
        }
        if (lower.contains("mất") || lower.contains("thất lạc") || lower.contains("không thấy")) {
            return IncidentType.THẤT_LẠC;
        }
        return IncidentType.HỎNG_HÓC;
    }

    private String extractHubCode(String message) {
        Matcher m = Pattern.compile("(HN|HCM|DN)-\\d{2}", Pattern.CASE_INSENSITIVE).matcher(message);
        if (m.find()) {
            return m.group(0).toUpperCase();
        }
        String lower = message.toLowerCase();
        if (lower.contains("hà nội")) return "HN-01";
        if (lower.contains("hồ chí minh") || lower.contains("sài gòn")) return "HCM-01";
        if (lower.contains("đà nẵng")) return "DN-01";
        return null;
    }

    private IncidentSeverity extractSeverity(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("nặng") || lower.contains("nghiêm trọng") || lower.contains("ướt hỏng") || lower.contains("vỡ hết")) {
            return IncidentSeverity.CRITICAL;
        }
        if (lower.contains("trung bình") || lower.contains("vừa")) {
            return IncidentSeverity.MEDIUM;
        }
        return IncidentSeverity.CRITICAL;
    }

    private DeliveryStatus mapIncidentToDeliveryStatus(IncidentType incidentType) {
        return switch (incidentType) {
            case HỎNG_HÓC -> DeliveryStatus.DAMAGED;
            case GIAO_TRỄ -> DeliveryStatus.DELAYED;
            case THẤT_LẠC -> DeliveryStatus.DAMAGED;
        };
    }
}
