package com.example.oncallagent.tool;

import com.example.oncallagent.model.ApprovalValidationResult;
import com.example.oncallagent.model.DiagnosticResult;
import com.example.oncallagent.model.OnCallUser;
import com.example.oncallagent.model.RestartResult;
import com.example.oncallagent.service.ApprovalService;
import com.example.oncallagent.service.DiagnosisService;
import com.example.oncallagent.service.OnCallService;
import com.example.oncallagent.service.RestartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    private final DiagnosisService diagnosisService;
    private final OnCallService onCallService;
    private final ApprovalService approvalService;
    private final RestartService restartService;

    public AgentTools(DiagnosisService diagnosisService,
                      OnCallService onCallService,
                      ApprovalService approvalService,
                      RestartService restartService) {
        this.diagnosisService = diagnosisService;
        this.onCallService = onCallService;
        this.approvalService = approvalService;
        this.restartService = restartService;
    }

    @Tool(description = "Analyze the event date and error message and return the recommended action, whether approval is required, and the target system.")
    public DiagnosticResult runDiagnostic(String eventDate, String errorMessage) {
        log.info("[TOOL] runDiagnostic called: eventDate={}, errorMessage={}", eventDate, errorMessage);
        DiagnosticResult result = diagnosisService.runDiagnostic(eventDate, errorMessage);
        log.info("[TOOL] runDiagnostic result: recommendedAction={}, approvalRequired={}", result.recommendedAction(), result.approvalRequired());
        return result;
    }

    @Tool(description = "Get the current on-call engineer including the Slack user ID.")
    public OnCallUser getCurrentOncall() {
        log.info("[TOOL] getCurrentOncall called");
        OnCallUser user = onCallService.getCurrentOnCall();
        log.info("[TOOL] getCurrentOncall result: name={}, slackUserId={}", user.name(), user.slackUserId());
        return user;
    }

    @Tool(description = "REQUIRED: Send a Slack DM approval request to the on-call engineer and persist the approval. You MUST call this tool when recommendedAction is RESTART_SERVICE. Pass the slackUserId from getCurrentOncall.")
    public Map<String, Object> requestRestartApproval(String slackId,
                                                      String eventDate,
                                                      String errorMessage,
                                                      String diagnosticSummary,
                                                      String recommendedAction,
                                                      String targetSystem) {
        log.info("[TOOL] requestRestartApproval called: slackId={}, targetSystem={}", slackId, targetSystem);
        Map<String, Object> result = approvalService.requestRestartApproval(slackId, eventDate, errorMessage, diagnosticSummary, recommendedAction, targetSystem);
        log.info("[TOOL] requestRestartApproval result: approvalId={}, slackRequestSent={}", result.get("approvalId"), result.get("slackRequestSent"));
        return result;
    }

    @Tool(description = "Validate an approval response from Slack. Returns whether the request is approved and authorized.")
    public ApprovalValidationResult validateApprovalResponse(String approvalId,
                                                             String slackUserId,
                                                             String response) {
        log.info("[TOOL] validateApprovalResponse called: approvalId={}, slackUserId={}", approvalId, slackUserId);
        return approvalService.validateApprovalResponse(approvalId, slackUserId, response);
    }

    @Tool(description = "Restart the affected service after the approval response has been validated and approved.")
    public RestartResult restartService(String approvalId, String slackUserId, String targetSystem) {
        log.info("[TOOL] restartService called: approvalId={}, targetSystem={}", approvalId, targetSystem);
        RestartResult result = restartService.restartService(approvalId, slackUserId, targetSystem);
        approvalService.markUsed(approvalId);
        return result;
    }
}
