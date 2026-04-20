package com.example.oncallagent.controller;

import com.example.oncallagent.model.AgentEvent;
import com.example.oncallagent.model.AgentEventType;
import com.example.oncallagent.service.AgentWorkflowService;


import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

private final AgentWorkflowService agentWorkflowService;

    public ApprovalController(AgentWorkflowService agentWorkflowService) {
        this.agentWorkflowService = agentWorkflowService;
    }

   @PostMapping("/response")
public ResponseEntity<Map<String, Object>> handleApprovalResponse(@RequestBody AgentEvent request) {
    AgentEvent event = new AgentEvent(
            AgentEventType.APPROVAL_RESPONSE,
            null,
            null,
            request.approvalId(),
            request.slackUserId(),
            request.response()
    );

    agentWorkflowService.processEventAsync(event);

    return ResponseEntity.accepted().body(Map.of(
            "status", "accepted",
            "message", "Approval response received and processing started"
    ));
}
}
