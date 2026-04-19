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
        log.info("Tool called: runDiagnostic. eventDate={}, errorMessage={}", eventDate, errorMessage);

        try {
            DiagnosticResult result = diagnosisService.runDiagnostic(eventDate, errorMessage);

            log.info("Tool completed: runDiagnostic. recommendedAction={}, approvalRequired={}, targetSystem={}",
                    result.recommendedAction(),
                    result.approvalRequired(),
                    result.targetSystem());

            log.debug("runDiagnostic result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: runDiagnostic. eventDate={}, errorMessage={}", eventDate, errorMessage, ex);
            throw ex;
        }
    }

    @Tool(description = "Get the currently on-call engineer, including their Slack user ID.")
    public OnCallUser getCurrentOncall() {
        log.info("Tool called: getCurrentOncall");

        try {
            OnCallUser result = onCallService.getCurrentOncall();

            log.info("Tool completed: getCurrentOncall. name={}, slackUserId={}, team={}",
                    result.name(),
                    result.slackUserId(),
                    result.team());

            log.debug("getCurrentOncall result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: getCurrentOncall", ex);
            throw ex;
        }
    }

    @Tool(description = "Create a restart approval request for the current on-call engineer and persist it locally. Use this only when restart is recommended.")
    public Map<String, Object> requestRestartApproval(String slackId,
                                                      String eventDate,
                                                      String errorMessage,
                                                      String diagnosticSummary,
                                                      String recommendedAction,
                                                      String targetSystem) {
        log.info("Tool called: requestRestartApproval. slackId={}, recommendedAction={}, targetSystem={}",
                slackId, recommendedAction, targetSystem);

        log.debug("requestRestartApproval inputs: eventDate={}, errorMessage={}, diagnosticSummary={}",
                eventDate, errorMessage, diagnosticSummary);

        try {
            Map<String, Object> result = approvalService.requestRestartApproval(
                    slackId,
                    eventDate,
                    errorMessage,
                    diagnosticSummary,
                    recommendedAction,
                    targetSystem
            );

            log.info("Tool completed: requestRestartApproval. approvalId={}, status={}",
                    result.get("approvalId"),
                    result.get("status"));

            log.debug("requestRestartApproval result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: requestRestartApproval. slackId={}, targetSystem={}", slackId, targetSystem, ex);
            throw ex;
        }
    }

    @Tool(description = "Validate an approval response from Slack. Returns whether the request is approved and authorized.")
    public ApprovalValidationResult validateApprovalResponse(String approvalId,
                                                             String slackUserId,
                                                             String response) {
        log.info("Tool called: validateApprovalResponse. approvalId={}, slackUserId={}, response={}",
                approvalId, slackUserId, response);

        try {
            ApprovalValidationResult result = approvalService.validateApprovalResponse(
                    approvalId,
                    slackUserId,
                    response
            );

            log.info("Tool completed: validateApprovalResponse. approved={}, authorized={}, reason={}",
                    result.approved(),
                    result.authorized(),
                    result.reason());

            log.debug("validateApprovalResponse result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: validateApprovalResponse. approvalId={}, slackUserId={}",
                    approvalId, slackUserId, ex);
            throw ex;
        }
    }

    @Tool(description = "Restart the affected service after the approval response has been validated and approved.")
    public RestartResult restartService(String approvalId, String slackUserId, String targetSystem) {
        log.info("Tool called: restartService. approvalId={}, slackUserId={}, targetSystem={}",
                approvalId, slackUserId, targetSystem);

        try {
            RestartResult result = restartService.restartService(approvalId, slackUserId, targetSystem);

            log.info("Tool completed: restartService. status={}, targetSystem={}",
                    result.status(),
                    result.targetSystem());

            log.debug("restartService result={}", result);

            approvalService.markUsed(approvalId);
            log.info("Approval marked as used. approvalId={}", approvalId);

            return result;
        } catch (Exception ex) {
            log.error("Tool failed: restartService. approvalId={}, slackUserId={}, targetSystem={}",
                    approvalId, slackUserId, targetSystem, ex);
            throw ex;
        }
    }
}