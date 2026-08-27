package com.example.demo.controller;

import com.example.demo.dto.McpQueryRequest;
import com.example.demo.dto.McpQueryResponse;
import com.example.demo.service.McpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;

    @PostMapping("/query")
    public ResponseEntity<McpQueryResponse> query(@RequestBody McpQueryRequest request) {
        McpQueryResponse response = mcpService.processMcpQuery(
                request.getNaturalLanguageQuery(),
                request.getRawSql()
        );
        return ResponseEntity.ok(response);
    }
}
