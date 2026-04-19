package com.example.oncallagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentDecision(
        String eventType,
        String status,
        String summary,
        String recommendedAction,
        boolean approvalRequired,
        String approvalStatus,
        String restartStatus
) {
}
