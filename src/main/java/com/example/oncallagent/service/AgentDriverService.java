package com.example.oncallagent.service;

import com.example.oncallagent.model.AgentDecision;
import com.example.oncallagent.model.AgentEvent;
import com.example.oncallagent.model.DiagnosticResult;
import com.example.oncallagent.model.OnCallUser;
import com.example.oncallagent.model.RestartApprovalRequest;
import com.example.oncallagent.tool.ApprovalTools;
import com.example.oncallagent.tool.IncidentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AgentDriverService {

    private static final Logger log = LoggerFactory.getLogger(AgentDriverService.class);

private static final String INCIDENT_SYSTEM_PROMPT = """
        You are an on-call operations agent handling an INCIDENT_DETECTED event.

        General rules:
        - Always use tools when tool data is needed.
        - Never invent IDs, approval status, target systems, Slack user IDs, repository names, branches, file paths, or analysis results.
        - Never call a tool with null, empty, or missing values.
        - slackId for approval tools must come only from getCurrentOncall.slackUserId.
        - channelId for channel messages must be C0ATPJU695G.
        - If recommendedAction == RESTART_SERVICE, approvalRequired must be true.
        - Return valid JSON only.
        - The final response must match exactly this JSON shape:
          {
            "eventType": "INCIDENT_DETECTED",
            "status": "pending | completed | error",
            "summary": "string",
            "recommendedAction": "RESTART_SERVICE | ANALYZE_CODE | INVESTIGATE",
            "approvalRequired": true,
            "approvalStatus": "PENDING | NOT_REQUIRED | UNKNOWN | ERROR",
            "restartStatus": "NOT_EXECUTED"
          }

        STRICT TOOL USAGE RULE:
        - If a tool requires structured input, you must build the full object first.
        - Do not call tools with partial or missing data.
        - Do not retry a failed tool call with the same invalid inputs.
        - Always gather required data from prior tool calls before calling dependent tools.

        IMPORTANT:
        - You are not allowed to return status = pending unless you have already called an approval-request tool successfully.
        - For RESTART_SERVICE, you must request approval before returning.
        - For CREATE_PULL_REQUEST, you must request approval before returning.
        - For RESTART_SERVICE, you must not return status = error after runDiagnostic and getCurrentOncall succeed. You must call requestRestartApproval.

        Incident flow rules:
        1. Call runDiagnostic first.

        2. If runDiagnostic.recommendedAction == RESTART_SERVICE:

        - You MUST call getCurrentOncall.

        - You MUST build a RestartApprovalRequest object with:
            - slackId = getCurrentOncall.slackUserId
            - eventDate = the incident event eventDate
            - errorMessage = the incident event errorMessage
            - diagnosticSummary = runDiagnostic.summary
            - recommendedAction = runDiagnostic.recommendedAction
            - targetSystem = runDiagnostic.targetSystem

        - All fields must be non-null and non-empty.
        - If any field is missing, DO NOT continue. You must fix missing data first.

        - You MUST call requestRestartApproval with that object.

        - It is INVALID to return a final response before calling requestRestartApproval.

        - Only AFTER requestRestartApproval succeeds:
            - return status = pending
            - return approvalRequired = true
            - return approvalStatus = PENDING

        - Then STOP immediately.
        - Do not call any other tools.

        3. If runDiagnostic.recommendedAction == ANALYZE_CODE:
           - call getRepositoryContext
           - call fetchCodeContext
           - call analyzeCodeIssue

           - if analyzeCodeIssue.confidentFixAvailable == true:
             - call getCurrentOncall
             - you MUST build a PullRequestApprovalRequest object with:
               - slackId = getCurrentOncall.slackUserId
               - eventDate = the incident event eventDate
               - errorMessage = the incident event errorMessage
               - diagnosticSummary = analyzeCodeIssue.summary
               - recommendedAction = CREATE_PULL_REQUEST
               - targetSystem = runDiagnostic.targetSystem
               - repoName = analyzeCodeIssue.repoName
             - all fields must be non-null and non-empty
             - you MUST call requestPullRequestApproval with that object
             - only AFTER requestPullRequestApproval succeeds may you return:
               - status = pending
               - approvalStatus = PENDING
             - then STOP immediately and return the final JSON

           - if analyzeCodeIssue.confidentFixAvailable == false:
             - call sendSlackChannelMessage with channelId = C0ATPJU695G
             - after sendSlackChannelMessage, return:
               - status = completed
               - approvalStatus = NOT_REQUIRED
             - then STOP immediately and return the final JSON

        4. If runDiagnostic.recommendedAction == INVESTIGATE:
           - return:
             - status = completed
             - approvalStatus = NOT_REQUIRED
           - STOP immediately and return the final JSON

        5. Never perform more than one terminal incident action.
        """;

    private static final String APPROVAL_SYSTEM_PROMPT = """
            You are an on-call operations agent handling an APPROVAL_RESPONSE event.

            General rules:
            - Always use tools when tool data is needed.
            - Never invent IDs, approval status, target systems, repository names, branches, or execution results.
            - Never call a tool with null, empty, or missing values.
            - Return valid JSON only.
            - The final response must match exactly this JSON shape:
              {
                "eventType": "APPROVAL_RESPONSE",
                "status": "completed | error",
                "summary": "string",
                "recommendedAction": "RESTART_SERVICE | CREATE_PULL_REQUEST | INVESTIGATE",
                "approvalRequired": false,
                "approvalStatus": "APPROVED | DENIED | USED | EXPIRED | NOT_FOUND | UNKNOWN",
                "restartStatus": "RESTART_TRIGGERED | RESTART_FAILED | PULL_REQUEST_CREATED | NOT_EXECUTED"
              }

            Approval flow rules:
            1. Call validateApprovalResponse first.
            2. If approval is not approved and authorized, STOP and return final JSON.
            3. If action == RESTART_SERVICE:
               - call restartService
               - AFTER restartService, STOP immediately and return final JSON.
            4. If action == CREATE_PULL_REQUEST:
               - call getRepositoryContext
               - call createPullRequest
               - AFTER createPullRequest, STOP immediately and return final JSON.
            5. Never perform more than one executable action.
            """;

    private final ChatClient chatClient;
    private final IncidentTools incidentTools;
    private final ApprovalTools approvalTools;

    public AgentDriverService(ChatClient chatClient,
                              IncidentTools incidentTools,
                              ApprovalTools approvalTools) {
        this.chatClient = chatClient;
        this.incidentTools = incidentTools;
        this.approvalTools = approvalTools;
    }

    public AgentDecision handle(AgentEvent event) {
        if (event == null || event.eventType() == null) {
            log.error("AgentDriverService.handle received null event or null eventType. event={}", event);
            return new AgentDecision(
                    "UNKNOWN",
                    "error",
                    "Missing eventType",
                    "INVESTIGATE",
                    false,
                    "UNKNOWN",
                    "NOT_EXECUTED"
            );
        }

        log.info("AgentDriverService.handle invoked. eventType={}", event.eventType());

        String userPrompt = buildUserPrompt(event);
        log.debug("User prompt built:\n{}", userPrompt);

        try {
            Object tools = switch (event.eventType()) {
                case INCIDENT_DETECTED -> incidentTools;
                case APPROVAL_RESPONSE -> approvalTools;
            };

            String systemPrompt = switch (event.eventType()) {
                case INCIDENT_DETECTED -> INCIDENT_SYSTEM_PROMPT;
                case APPROVAL_RESPONSE -> APPROVAL_SYSTEM_PROMPT;
            };

            AgentDecision decision = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .tools(tools)
                    .call()
                    .entity(AgentDecision.class);

            decision = enforceRequiredWorkflow(event, decision);

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

    private AgentDecision enforceRequiredWorkflow(AgentEvent event, AgentDecision decision) {
        if (event.eventType() != null
                && event.eventType().name().equals("INCIDENT_DETECTED")
                && decision != null
                && "RESTART_SERVICE".equals(decision.recommendedAction())
                && !"PENDING".equals(decision.approvalStatus())) {
            log.warn("""
                    Incident decision violated restart approval contract.
                    Enforcing approval flow in code. status={}, approvalStatus={}, summary={}
                    """,
                    decision.status(),
                    decision.approvalStatus(),
                    decision.summary());
            return executeRestartApprovalFlow(event);
        }

        return decision;
    }

    private AgentDecision executeRestartApprovalFlow(AgentEvent event) {
        try {
            DiagnosticResult diagnostic = incidentTools.runDiagnostic(event.eventDate(), event.errorMessage());
            if (!"RESTART_SERVICE".equals(diagnostic.recommendedAction())) {
                log.warn("Restart approval fallback skipped because diagnostic action resolved to {}",
                        diagnostic.recommendedAction());
                return new AgentDecision(
                        event.eventType().name(),
                        "error",
                        "Restart approval flow was requested, but the diagnostic no longer recommends restart.",
                        diagnostic.recommendedAction(),
                        diagnostic.approvalRequired(),
                        "ERROR",
                        "NOT_EXECUTED"
                );
            }

            OnCallUser onCallUser = incidentTools.getCurrentOncall();
            RestartApprovalRequest request = new RestartApprovalRequest(
                    onCallUser.slackUserId(),
                    event.eventDate(),
                    event.errorMessage(),
                    diagnostic.summary(),
                    diagnostic.recommendedAction(),
                    diagnostic.targetSystem()
            );

            Map<String, Object> approvalResult = incidentTools.requestRestartApproval(request);
            String approvalStatus = String.valueOf(approvalResult.getOrDefault("approvalStatus", "UNKNOWN"));
            String message = String.valueOf(approvalResult.getOrDefault(
                    "message",
                    "Slack approval request created."
            ));

            return new AgentDecision(
                    event.eventType().name(),
                    "pending",
                    message,
                    diagnostic.recommendedAction(),
                    true,
                    approvalStatus,
                    "NOT_EXECUTED"
            );
        } catch (Exception ex) {
            log.error("Failed to enforce restart approval flow in code.", ex);
            return new AgentDecision(
                    event.eventType().name(),
                    "error",
                    "Failed to create restart approval request",
                    "RESTART_SERVICE",
                    true,
                    "ERROR",
                    "NOT_EXECUTED"
            );
        }
    }

    private String buildUserPrompt(AgentEvent event) {
        return switch (event.eventType()) {
            case INCIDENT_DETECTED -> """
                Handle this incident event.

                eventType: %s
                eventDate: %s
                errorMessage: %s

                Required behavior:
                - You must call runDiagnostic first.
                - If the result is RESTART_SERVICE:
                - you must call getCurrentOncall
                - you must call requestRestartApproval
                - you are not allowed to return until requestRestartApproval has been called
                - Do not return completed for restart flows.
                - Stop after the first valid terminal action.
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

                    Important:
                    - Validate approval first.
                    - Perform at most one executable action.
                    - Stop immediately after that action.
                    """.formatted(
                    event.eventType(),
                    event.approvalId(),
                    event.slackUserId(),
                    event.response()
            );
        };
    }
}
