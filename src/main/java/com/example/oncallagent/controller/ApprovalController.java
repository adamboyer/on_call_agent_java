package com.example.oncallagent.controller;

import com.example.oncallagent.model.AgentDecision;
import com.example.oncallagent.model.AgentEvent;
import com.example.oncallagent.model.ApprovalResponseRequest;
import com.example.oncallagent.service.AgentDriverService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final AgentDriverService agentDriverService;

    public ApprovalController(AgentDriverService agentDriverService) {
        this.agentDriverService = agentDriverService;
    }

    @PostMapping("/response")
    public AgentDecision handleApprovalResponse(@Valid @RequestBody ApprovalResponseRequest request) {
        return agentDriverService.handle(AgentEvent.approvalResponse(
                request.approvalId(),
                request.slackUserId(),
                request.response()
        ));
    }
}
