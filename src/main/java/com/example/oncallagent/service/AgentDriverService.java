package com.example.oncallagent.service;

import com.example.oncallagent.model.AgentDecision;
import com.example.oncallagent.model.AgentEvent;
import com.example.oncallagent.model.ApprovalValidationResult;
import com.example.oncallagent.model.DiagnosticResult;
import com.example.oncallagent.model.OnCallUser;
import com.example.oncallagent.model.RestartResult;
import com.example.oncallagent.tool.AgentTools;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AgentDriverService {

    private final AgentTools agentTools;

    public AgentDriverService(AgentTools agentTools) {
        this.agentTools = agentTools;
    }

    public AgentDecision handle(AgentEvent event) {
        return switch (event.eventType()) {
            case INCIDENT_DETECTED -> handleIncident(event);
            case APPROVAL_RESPONSE -> handleApprovalResponse(event);
        };
    }

    private AgentDecision handleIncident(AgentEvent event) {
        DiagnosticResult diagnostic = agentTools.runDiagnostic(event.eventDate(), event.errorMessage());
        OnCallUser onCallUser = agentTools.getCurrentOncall();

        if ("RESTART_SERVICE".equals(diagnostic.recommendedAction())) {
            Map<String, Object> approval = agentTools.requestRestartApproval(
                    onCallUser.slackUserId(),
                    event.eventDate(),
                    event.errorMessage(),
                    diagnostic.summary(),
                    diagnostic.recommendedAction(),
                    diagnostic.targetSystem());
            return new AgentDecision(
                    "INCIDENT_DETECTED",
                    "COMPLETED",
                    diagnostic.summary(),
                    diagnostic.recommendedAction(),
                    true,
                    (String) approval.get("approvalStatus"),
                    null);
        }

        return new AgentDecision(
                "INCIDENT_DETECTED",
                "COMPLETED",
                diagnostic.summary(),
                diagnostic.recommendedAction(),
                false,
                null,
                null);
    }

    private AgentDecision handleApprovalResponse(AgentEvent event) {
        ApprovalValidationResult validation = agentTools.validateApprovalResponse(
                event.approvalId(),
                event.slackUserId(),
                event.response());

        if (validation.approved() && validation.authorized()) {
            RestartResult restart = agentTools.restartService(
                    event.approvalId(),
                    event.slackUserId(),
                    validation.targetSystem());

            String executionSummary = "FILE_COPIED_SUCCESS".equals(restart.restartStatus())
                    ? "Restart approved and S3 copy completed for " + validation.targetSystem()
                    : "Restart approved but S3 copy did not complete: " + restart.details();

            return new AgentDecision(
                    "APPROVAL_RESPONSE",
                    "COMPLETED",
                    executionSummary,
                    "RESTART_SERVICE",
                    true,
                    validation.approvalStatus(),
                    restart.restartStatus());
        }

        return new AgentDecision(
                "APPROVAL_RESPONSE",
                "COMPLETED",
                "Restart denied or unauthorized: " + validation.reason(),
                "INVESTIGATE",
                true,
                validation.approvalStatus(),
                "CANCELLED");
    }
}
