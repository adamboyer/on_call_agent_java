package com.example.oncallagent.controller;

import com.example.oncallagent.model.AgentDecision;
import com.example.oncallagent.model.AgentEvent;
import com.example.oncallagent.model.IncidentRequest;
import com.example.oncallagent.service.AgentDriverService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final AgentDriverService agentDriverService;

    public IncidentController(AgentDriverService agentDriverService) {
        this.agentDriverService = agentDriverService;
    }

    @PostMapping
    public AgentDecision handleIncident(@Valid @RequestBody IncidentRequest request) {
        return agentDriverService.handle(AgentEvent.incident(request.eventDate(), request.errorMessage()));
    }
}
