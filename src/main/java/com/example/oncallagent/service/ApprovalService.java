package com.example.oncallagent.service;

import com.example.oncallagent.entity.ApprovalEntity;
import com.example.oncallagent.model.ApprovalAction;
import com.example.oncallagent.model.ApprovalStatus;
import com.example.oncallagent.model.ApprovalValidationResult;
import com.example.oncallagent.model.SlackMessageResult;
import com.example.oncallagent.repository.ApprovalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private static final String APPROVAL_CHANNEL_ID = "C0ATPJU695G";

    private final ApprovalRepository approvalRepository;
    private final SlackService slackService;

    public ApprovalService(ApprovalRepository approvalRepository,
                           SlackService slackService) {
        this.approvalRepository = approvalRepository;
        this.slackService = slackService;
    }

    @Transactional
    public Map<String, Object> requestRestartApproval(String slackId,
                                                      String eventDate,
                                                      String errorMessage,
                                                      String diagnosticSummary,
                                                      String recommendedAction,
                                                      String targetSystem) {
        log.info("Creating restart approval. slackId={}, recommendedAction={}, targetSystem={}",
                slackId, recommendedAction, targetSystem);

        if (slackId == null || recommendedAction == null || targetSystem == null
                || eventDate == null || errorMessage == null || diagnosticSummary == null) {
            log.error("Missing required parameters for requestRestartApproval. slackId={}, eventDate={}, recommendedAction={}, targetSystem={}",
                    slackId, eventDate, recommendedAction, targetSystem);
            throw new IllegalArgumentException("Missing required parameters for requestRestartApproval");
        }

        ApprovalEntity entity = new ApprovalEntity();
        entity.setApprovalId(UUID.randomUUID().toString());
        entity.setIncidentId(buildIncidentId(eventDate, errorMessage));
        entity.setSlackUserId(slackId);
        entity.setAction(ApprovalAction.RESTART_SERVICE.name());
        entity.setTargetSystem(targetSystem);
        entity.setStatus(ApprovalStatus.PENDING);
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));

        approvalRepository.save(entity);

        log.info("Approval persisted. approvalId={}, incidentId={}, expiresAt={}",
                entity.getApprovalId(), entity.getIncidentId(), entity.getExpiresAt());

        String slackMessage = """
                🚨 *Approval Required*

                *Incident:* %s
                *System:* %s
                *Summary:* %s

                Recommended Action: %s

                To approve, reply with:
                `APPROVE_RESTART`

                Approval ID: %s
                Requested for on-call user: %s
                """.formatted(
                entity.getIncidentId(),
                targetSystem,
                diagnosticSummary,
                recommendedAction,
                entity.getApprovalId(),
                slackId
        );

        log.info("Sending Slack approval message. approvalId={}, channelId={}",
                entity.getApprovalId(), APPROVAL_CHANNEL_ID);

        SlackMessageResult slackResult = slackService.sendChannelMessage(APPROVAL_CHANNEL_ID, slackMessage);

        log.info("Slack approval message result. approvalId={}, slackOk={}, channelId={}, ts={}, error={}",
                entity.getApprovalId(),
                slackResult.ok(),
                slackResult.channelId(),
                slackResult.messageTs(),
                slackResult.error());

        Map<String, Object> result = new HashMap<>();
        result.put("approvalId", entity.getApprovalId());
        result.put("approvalStatus", entity.getStatus().name());
        result.put("slackUserId", slackId);
        result.put("diagnosticSummary", diagnosticSummary);
        result.put("recommendedAction", recommendedAction);
        result.put("incidentId", entity.getIncidentId());
        result.put("targetSystem", targetSystem);
        result.put("slackChannelId", APPROVAL_CHANNEL_ID);
        result.put("slackMessageSent", slackResult.ok());
        result.put("slackMessageTs", slackResult.messageTs());
        result.put("slackError", slackResult.error());
        result.put("message", slackResult.ok()
                ? "Slack approval request created and sent"
                : "Slack approval request created but Slack send failed");

        log.debug("requestRestartApproval result={}", result);

        return result;
    }

    @Transactional
    public ApprovalValidationResult validateApprovalResponse(String approvalId,
                                                             String slackUserId,
                                                             String response) {
        log.info("Validating approval response. approvalId={}, slackUserId={}, response={}",
                approvalId, slackUserId, response);

        ApprovalEntity entity = approvalRepository.findById(approvalId).orElse(null);

        if (entity == null) {
            log.warn("Approval not found. approvalId={}", approvalId);
            return new ApprovalValidationResult(
                    false,
                    false,
                    "NOT_FOUND",
                    "approval_not_found",
                    null,
                    null,
                    null
            );
        }

        if (Instant.now().isAfter(entity.getExpiresAt())) {
            entity.setStatus(ApprovalStatus.EXPIRED);
            approvalRepository.save(entity);

            log.warn("Approval expired. approvalId={}, incidentId={}",
                    approvalId, entity.getIncidentId());

            return new ApprovalValidationResult(
                    false,
                    false,
                    entity.getStatus().name(),
                    "approval_expired",
                    entity.getIncidentId(),
                    entity.getAction(),
                    entity.getTargetSystem()
            );
        }

        if (!entity.getSlackUserId().equals(slackUserId)) {
            log.warn("Approval denied for wrong user. approvalId={}, expectedSlackUserId={}, actualSlackUserId={}",
                    approvalId, entity.getSlackUserId(), slackUserId);

            return new ApprovalValidationResult(
                    false,
                    false,
                    entity.getStatus().name(),
                    "wrong_user",
                    entity.getIncidentId(),
                    entity.getAction(),
                    entity.getTargetSystem()
            );
        }

        if (!ApprovalStatus.PENDING.equals(entity.getStatus())) {
            log.warn("Approval is not pending. approvalId={}, status={}",
                    approvalId, entity.getStatus());

            return new ApprovalValidationResult(
                    false,
                    false,
                    entity.getStatus().name(),
                    "approval_not_pending",
                    entity.getIncidentId(),
                    entity.getAction(),
                    entity.getTargetSystem()
            );
        }

        if (!"APPROVE_RESTART".equalsIgnoreCase(response)) {
            entity.setStatus(ApprovalStatus.DENIED);
            approvalRepository.save(entity);

            log.info("Approval denied by user. approvalId={}, slackUserId={}",
                    approvalId, slackUserId);

            return new ApprovalValidationResult(
                    false,
                    true,
                    entity.getStatus().name(),
                    "restart_denied",
                    entity.getIncidentId(),
                    entity.getAction(),
                    entity.getTargetSystem()
            );
        }

        entity.setStatus(ApprovalStatus.APPROVED);
        approvalRepository.save(entity);

        log.info("Approval granted. approvalId={}, incidentId={}, targetSystem={}",
                approvalId, entity.getIncidentId(), entity.getTargetSystem());

        return new ApprovalValidationResult(
                true,
                true,
                entity.getStatus().name(),
                "approved",
                entity.getIncidentId(),
                entity.getAction(),
                entity.getTargetSystem()
        );
    }

    @Transactional
    public void markUsed(String approvalId) {
        approvalRepository.findById(approvalId).ifPresent(entity -> {
            entity.setStatus(ApprovalStatus.USED);
            approvalRepository.save(entity);

            log.info("Approval marked as used. approvalId={}, incidentId={}",
                    approvalId, entity.getIncidentId());
        });
    }

    private String buildIncidentId(String eventDate, String errorMessage) {
        return Integer.toHexString((eventDate + ":" + errorMessage).hashCode());
    }
}