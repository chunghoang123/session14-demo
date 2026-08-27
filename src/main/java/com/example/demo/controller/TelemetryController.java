package com.example.demo.controller;

import com.example.demo.dto.TelemetryStatsDTO;
import com.example.demo.service.LLMOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final LLMOpsService llmOpsService;

    @GetMapping("/stats")
    public ResponseEntity<TelemetryStatsDTO> getStats() {
        return ResponseEntity.ok(llmOpsService.getStats());
    }
}
