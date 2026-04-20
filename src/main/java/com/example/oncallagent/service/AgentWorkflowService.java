package com.example.oncallagent.service;

import com.example.oncallagent.model.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(AgentWorkflowService.class);

    private final AgentDriverService agentDriverService;

    public AgentWorkflowService(AgentDriverService agentDriverService) {
        this.agentDriverService = agentDriverService;
    }

    @Async
    public void processEventAsync(AgentEvent event) {
        log.info("Starting async agent workflow. eventType={}", event.eventType());
        agentDriverService.handle(event);
        log.info("Async agent workflow completed. eventType={}", event.eventType());
    }
}