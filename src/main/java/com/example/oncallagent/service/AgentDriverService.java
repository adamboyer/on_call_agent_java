package com.example.oncallagent.service;

import com.example.oncallagent.model.AgentDecision;
import com.example.oncallagent.model.AgentEvent;
import com.example.oncallagent.tool.AgentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AgentDriverService {

    private static final String SYSTEM_PROMPT = """
            You are an on-call operations agent.
            
            You handle exactly two event types:
            1. INCIDENT_DETECTED
            2. APPROVAL_RESPONSE
            
            For INCIDENT_DETECTED:
            - First call runDiagnostic with the eventDate and errorMessage.
            - Then call getCurrentOncall.
            - If the diagnostic recommends RESTART_SERVICE, call requestRestartApproval.
            - Do not call restartService during INCIDENT_DETECTED.
            - Return a final JSON object matching the AgentDecision schema.
            
            For APPROVAL_RESPONSE:
            - First call validateApprovalResponse.
            - Only if the approval is both approved and authorized should you call restartService.
            - Return a final JSON object matching the AgentDecision schema.
            
            Always use tools when tool data is needed. Never invent IDs, approval status, target systems, or restart results.
            Keep the final summary concise and operational.
            """;

    private final ChatClient chatClient;
    private final AgentTools agentTools;

    public AgentDriverService(ChatClient chatClient, AgentTools agentTools) {
        this.chatClient = chatClient;
        this.agentTools = agentTools;
    }

    public AgentDecision handle(AgentEvent event) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(buildUserPrompt(event))
                .tools(agentTools)
                .call()
                .entity(AgentDecision.class);
    }

    private String buildUserPrompt(AgentEvent event) {
        return switch (event.eventType()) {
            case INCIDENT_DETECTED -> """
                    Handle this incident event.
                    eventType: %s
                    eventDate: %s
                    errorMessage: %s
                    """.formatted(event.eventType(), event.eventDate(), event.errorMessage());
            case APPROVAL_RESPONSE -> """
                    Handle this approval response event.
                    eventType: %s
                    approvalId: %s
                    slackUserId: %s
                    response: %s
                    """.formatted(event.eventType(), event.approvalId(), event.slackUserId(), event.response());
        };
    }
}
