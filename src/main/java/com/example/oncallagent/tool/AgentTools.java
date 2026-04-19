package com.example.oncallagent.tool;

import com.example.oncallagent.model.ApprovalValidationResult;
import com.example.oncallagent.model.DiagnosticResult;
import com.example.oncallagent.model.OnCallUser;
import com.example.oncallagent.model.RestartResult;
import com.example.oncallagent.service.ApprovalService;
import com.example.oncallagent.service.DiagnosisService;
import com.example.oncallagent.service.OnCallService;
import com.example.oncallagent.service.RestartService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentTools {

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
        return diagnosisService.runDiagnostic(eventDate, errorMessage);
    }

    @Tool(description = "Get the current on-call engineer including the Slack user ID.")
    public OnCallUser getCurrentOncall() {
        return onCallService.getCurrentOnCall();
    }

    @Tool(description = "Create a restart approval request for the current on-call engineer and persist it locally. Use this only when restart is recommended.")
    public Map<String, Object> requestRestartApproval(String slackId,
                                                      String eventDate,
                                                      String errorMessage,
                                                      String diagnosticSummary,
                                                      String recommendedAction,
                                                      String targetSystem) {
        return approvalService.requestRestartApproval(slackId, eventDate, errorMessage, diagnosticSummary, recommendedAction, targetSystem);
    }

    @Tool(description = "Validate an approval response from Slack. Returns whether the request is approved and authorized.")
    public ApprovalValidationResult validateApprovalResponse(String approvalId,
                                                             String slackUserId,
                                                             String response) {
        return approvalService.validateApprovalResponse(approvalId, slackUserId, response);
    }

    @Tool(description = "Restart the affected service after the approval response has been validated and approved.")
    public RestartResult restartService(String approvalId, String slackUserId, String targetSystem) {
        RestartResult result = restartService.restartService(approvalId, slackUserId, targetSystem);
        approvalService.markUsed(approvalId);
        return result;
    }
}
