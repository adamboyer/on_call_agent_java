package com.example.oncallagent.model;

public record AgentEvent(
        AgentEventType eventType,
        String eventDate,
        String errorMessage,
        String approvalId,
        String slackUserId,
        String response
) {

    public static AgentEvent incident(String eventDate, String errorMessage) {
        return new AgentEvent(AgentEventType.INCIDENT_DETECTED, eventDate, errorMessage, null, null, null);
    }

    public static AgentEvent approvalResponse(String approvalId, String slackUserId, String response) {
        return new AgentEvent(AgentEventType.APPROVAL_RESPONSE, null, null, approvalId, slackUserId, response);
    }
}
