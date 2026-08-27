package com.example.demo.controller;

import com.example.demo.dto.RagResponse;
import com.example.demo.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @GetMapping("/ask")
    public ResponseEntity<RagResponse> ask(
            @RequestParam("query") String query,
            @RequestParam(value = "chunkSize", required = false, defaultValue = "500") Integer chunkSize,
            @RequestParam(value = "overlapPercent", required = false, defaultValue = "10") Integer overlapPercent,
            @RequestParam(value = "similarityThreshold", required = false, defaultValue = "0.45") Double similarityThreshold
    ) {
        RagResponse response = ragService.askPolicy(query, chunkSize, overlapPercent, similarityThreshold);
        return ResponseEntity.ok(response);
    }
}
