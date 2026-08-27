package com.example.demo.controller;

import com.example.demo.dto.OperationChatRequest;
import com.example.demo.dto.OperationChatResponse;
import com.example.demo.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    public ResponseEntity<OperationChatResponse> chat(@RequestBody OperationChatRequest request) {
        OperationChatResponse response = agentService.processOperationMessage(request.getMessage());
        return ResponseEntity.ok(response);
    }
}
