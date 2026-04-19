package com.example.oncallagent.service;

import com.example.oncallagent.model.AgentDecision;
import com.example.oncallagent.model.AgentEvent;
import com.example.oncallagent.tool.AgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AgentDriverService {

    private static final Logger log = LoggerFactory.getLogger(AgentDriverService.class);

    private static final String SYSTEM_PROMPT = """
            You are an on-call operations agent.

            You handle exactly two event types:
            1. INCIDENT_DETECTED
            2. APPROVAL_RESPONSE

            General rules:
            - Always use tools when tool data is needed.
            - Never invent IDs, approval status, target systems, Slack user IDs, or restart results.
            - Never call a tool with null, empty, or missing values.
            - If a required value is missing, stop and return a final AgentDecision explaining what is missing.
            - Keep the final summary concise and operational.
            - Return a final JSON object matching the AgentDecision schema.

            For INCIDENT_DETECTED:
            1. First call runDiagnostic with eventDate and errorMessage.
            2. Then call getCurrentOncall.
            3. You MUST reuse the outputs from prior tools:
               - Use slackUserId from getCurrentOncall
               - Use summary from runDiagnostic as diagnosticSummary
               - Use recommendedAction from runDiagnostic
               - Use targetSystem from runDiagnostic
            4. Only if runDiagnostic.recommendedAction == RESTART_SERVICE, call requestRestartApproval with:
               - slackId = getCurrentOncall.slackUserId
               - eventDate = the event input
               - errorMessage = the event input
               - diagnosticSummary = runDiagnostic.summary
               - recommendedAction = runDiagnostic.recommendedAction
               - targetSystem = runDiagnostic.targetSystem
            5. Do not call requestRestartApproval if any of those values are missing.
            6. Do not call restartService during INCIDENT_DETECTED.

            For APPROVAL_RESPONSE:
            1. First call validateApprovalResponse with:
               - approvalId = the event input
               - slackUserId = the event input
               - response = the event input
            2. Only if validation says approved=true and authorized=true, call restartService with:
               - approvalId = validateApprovalResponse.approvalId if available, otherwise the event approvalId
               - slackUserId = the event input
               - targetSystem = validateApprovalResponse.targetSystem
            3. Do not call restartService if targetSystem is missing.
            """;

    private final ChatClient chatClient;
    private final AgentTools agentTools;

    public AgentDriverService(ChatClient chatClient, AgentTools agentTools) {
        this.chatClient = chatClient;
        this.agentTools = agentTools;
    }

    public AgentDecision handle(AgentEvent event) {
        log.info("AgentDriverService.handle invoked. eventType={}", event.eventType());

        String userPrompt = buildUserPrompt(event);
        log.debug("User prompt built:\n{}", userPrompt);

        try {
            log.info("Calling LLM for eventType={}", event.eventType());

            AgentDecision decision = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .tools(agentTools)
                    .call()
                    .entity(AgentDecision.class);

            log.info("LLM returned decision. eventType={}, status={}, recommendedAction={}, approvalRequired={}, restartStatus={}",
                    event.eventType(),
                    decision.status(),
                    decision.recommendedAction(),
                    decision.approvalRequired(),
                    decision.restartStatus());

            log.debug("Full AgentDecision response={}", decision);

            return decision;

        } catch (Exception ex) {
            log.error("AgentDriverService failed during LLM execution. eventType={}", event.eventType(), ex);

            return new AgentDecision(
                    event.eventType().name(),
                    "error",
                    "Agent failed to process request",
                    "INVESTIGATE",
                    false,
                    "UNKNOWN",
                    "NOT_EXECUTED"
            );
        }
    }

    private String buildUserPrompt(AgentEvent event) {
        String prompt = switch (event.eventType()) {
            case INCIDENT_DETECTED -> """
                    Handle this incident event.

                    eventType: %s
                    eventDate: %s
                    errorMessage: %s

                    Required behavior:
                    - Call runDiagnostic first.
                    - Call getCurrentOncall second.
                    - If recommendedAction is RESTART_SERVICE, call requestRestartApproval using the exact values returned by those tools.
                    - Never pass null values to requestRestartApproval.
                    - Then return the final AgentDecision JSON.
                    """.formatted(
                    event.eventType(),
                    event.eventDate(),
                    event.errorMessage()
            );

            case APPROVAL_RESPONSE -> """
                    Handle this approval response event.

                    eventType: %s
                    approvalId: %s
                    slackUserId: %s
                    response: %s

                    Required behavior:
                    - Call validateApprovalResponse first.
                    - Only if approved=true and authorized=true, call restartService.
                    - Use targetSystem from validateApprovalResponse.
                    - Never pass null values to restartService.
                    - Then return the final AgentDecision JSON.
                    """.formatted(
                    event.eventType(),
                    event.approvalId(),
                    event.slackUserId(),
                    event.response()
            );
        };

        log.debug("Generated prompt for eventType={}", event.eventType());
        return prompt;
    }
}