package com.example.oncallagent.controller;

import com.example.oncallagent.model.AgentEvent;
import com.example.oncallagent.model.AgentEventType;
import com.example.oncallagent.service.AgentWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final AgentWorkflowService agentWorkflowService;

    public IncidentController(AgentWorkflowService agentWorkflowService) {
        this.agentWorkflowService = agentWorkflowService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleIncident(@RequestBody AgentEvent request) {
        AgentEvent event = new AgentEvent(
                AgentEventType.INCIDENT_DETECTED,
                request.eventDate(),
                request.errorMessage(),
                null,
                null,
                null
        );

        agentWorkflowService.processEventAsync(event);

        return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "message", "Incident received and processing started"
        ));
    }
}