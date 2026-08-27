package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.DeliveryRepository;
import com.example.demo.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogisticsTools {

    private final DeliveryRepository deliveryRepository;
    private final IncidentRepository incidentRepository;

    @Transactional
    public String createIncidentTool(String trackingCode, String incidentTypeStr, String hubCode, String severityStr, String description) {
        log.info("[Tool Call: createIncidentTool] trackingCode={}, incidentType={}, hubCode={}, severity={}",
                trackingCode, incidentTypeStr, hubCode, severityStr);

        Optional<Delivery> deliveryOpt = deliveryRepository.findByTrackingCode(trackingCode);
        if (deliveryOpt.isEmpty()) {
            return "ERROR: Delivery code " + trackingCode + " does not exist in system.";
        }

        IncidentType incidentType = IncidentType.HỎNG_HÓC;
        try {
            incidentType = IncidentType.valueOf(incidentTypeStr.toUpperCase());
        } catch (Exception ignored) {}

        IncidentSeverity severity = IncidentSeverity.CRITICAL;
        try {
            severity = IncidentSeverity.valueOf(severityStr.toUpperCase());
        } catch (Exception ignored) {}

        Incident incident = Incident.builder()
                .trackingCode(trackingCode)
                .incidentType(incidentType)
                .hubCode(hubCode != null ? hubCode : deliveryOpt.get().getHubCode())
                .severity(severity)
                .description(description)
                .status(IncidentStatus.OPEN)
                .build();

        Incident saved = incidentRepository.save(incident);
        return "SUCCESS: Created incident ID #" + saved.getId() + " for tracking code " + trackingCode + " (" + incidentType + ", " + severity + ").";
    }

    @Transactional
    public String updateDeliveryStatusTool(String trackingCode, String statusStr) {
        log.info("[Tool Call: updateDeliveryStatusTool] trackingCode={}, newStatus={}", trackingCode, statusStr);

        Optional<Delivery> deliveryOpt = deliveryRepository.findByTrackingCode(trackingCode);
        if (deliveryOpt.isEmpty()) {
            return "ERROR: Delivery code " + trackingCode + " does not exist in system.";
        }

        Delivery delivery = deliveryOpt.get();
        DeliveryStatus oldStatus = delivery.getStatus();

        DeliveryStatus newStatus = DeliveryStatus.DAMAGED;
        try {
            newStatus = DeliveryStatus.valueOf(statusStr.toUpperCase());
        } catch (Exception ignored) {}

        delivery.setStatus(newStatus);
        deliveryRepository.save(delivery);

        return "SUCCESS: Updated delivery " + trackingCode + " status from " + oldStatus + " to " + newStatus + ".";
    }

    public String getDeliveryDetailsTool(String trackingCode) {
        log.info("[Tool Call: getDeliveryDetailsTool] trackingCode={}", trackingCode);
        Optional<Delivery> deliveryOpt = deliveryRepository.findByTrackingCode(trackingCode);
        if (deliveryOpt.isEmpty()) {
            return "NOT_FOUND: Delivery " + trackingCode + " not found.";
        }

        Delivery d = deliveryOpt.get();
        return String.format("Delivery Details: [Tracking: %s, Customer: %s, Hub: %s, Status: %s, COD: %s]",
                d.getTrackingCode(), d.getCustomerName(), d.getHubCode(), d.getStatus(), d.getCodAmount());
    }
}
