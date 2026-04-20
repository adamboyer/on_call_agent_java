package com.example.oncallagent.service;

import com.example.oncallagent.entity.ApprovalEntity;
import com.example.oncallagent.model.ApprovalAction;
import com.example.oncallagent.model.ApprovalStatus;
import com.example.oncallagent.model.ApprovalValidationResult;
import com.example.oncallagent.model.PullRequestPlan;
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
            throw new IllegalArgumentException("Missing required parameters for requestRestartApproval");
        }

        ApprovalEntity entity = new ApprovalEntity();
        entity.setApprovalId(UUID.randomUUID().toString());
        entity.setIncidentId(buildIncidentId(eventDate, errorMessage));
        entity.setSlackUserId(slackId);
        entity.setAction(ApprovalAction.RESTART_SERVICE.name());
        entity.setTargetSystem(targetSystem);
        entity.setDiagnosticSummary(diagnosticSummary);
        entity.setStatus(ApprovalStatus.PENDING);
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));

        approvalRepository.save(entity);

        boolean slackSent = slackService.sendApprovalRequest(
                slackId,
                entity.getApprovalId(),
                eventDate,
                errorMessage,
                diagnosticSummary,
                recommendedAction,
                targetSystem
        );

        Map<String, Object> result = new HashMap<>();
        result.put("approvalId", entity.getApprovalId());
        result.put("approvalStatus", entity.getStatus().name());
        result.put("slackUserId", slackId);
        result.put("diagnosticSummary", diagnosticSummary);
        result.put("recommendedAction", recommendedAction);
        result.put("incidentId", entity.getIncidentId());
        result.put("targetSystem", targetSystem);
        result.put("slackMessageSent", slackSent);
        result.put("message", slackSent
                ? "Slack approval request created and sent"
                : "Slack approval request created but Slack send failed");

        return result;
    }

    @Transactional
    public Map<String, Object> requestPullRequestApproval(String slackId,
                                                          String eventDate,
                                                          String errorMessage,
                                                          String diagnosticSummary,
                                                          String recommendedAction,
                                                          String targetSystem,
                                                          String repoName,
                                                          String targetFile,
                                                          String replacementContent) {
        log.info("Creating PR approval. slackId={}, recommendedAction={}, targetSystem={}, repoName={}, targetFile={}",
                slackId, recommendedAction, targetSystem, repoName, targetFile);

        if (slackId == null || recommendedAction == null || targetSystem == null
                || eventDate == null || errorMessage == null || diagnosticSummary == null
                || repoName == null || targetFile == null || replacementContent == null) {
            throw new IllegalArgumentException("Missing required parameters for requestPullRequestApproval");
        }

        ApprovalEntity entity = new ApprovalEntity();
        entity.setApprovalId(UUID.randomUUID().toString());
        entity.setIncidentId(buildIncidentId(eventDate, errorMessage));
        entity.setSlackUserId(slackId);
        entity.setAction(ApprovalAction.CREATE_PULL_REQUEST.name());
        entity.setTargetSystem(targetSystem);
        entity.setRepoName(repoName);
        entity.setTargetFile(targetFile);
        entity.setReplacementContent(replacementContent);
        entity.setDiagnosticSummary(diagnosticSummary);
        entity.setStatus(ApprovalStatus.PENDING);
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));

        approvalRepository.save(entity);

        boolean slackSent = slackService.sendPullRequestApprovalRequest(
                slackId,
                entity.getApprovalId(),
                eventDate,
                errorMessage,
                diagnosticSummary,
                recommendedAction,
                targetSystem,
                repoName,
                targetFile
        );

        Map<String, Object> result = new HashMap<>();
        result.put("approvalId", entity.getApprovalId());
        result.put("approvalStatus", entity.getStatus().name());
        result.put("slackUserId", slackId);
        result.put("diagnosticSummary", diagnosticSummary);
        result.put("recommendedAction", recommendedAction);
        result.put("incidentId", entity.getIncidentId());
        result.put("targetSystem", targetSystem);
        result.put("repoName", repoName);
        result.put("targetFile", targetFile);
        result.put("slackMessageSent", slackSent);
        result.put("message", slackSent
                ? "Slack PR approval request created and sent"
                : "Slack PR approval request created but Slack send failed");

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
            return new ApprovalValidationResult(false, false, "NOT_FOUND", "approval_not_found",
                    null, null, null, null, null, null, null);
        }

        if (Instant.now().isAfter(entity.getExpiresAt())) {
            entity.setStatus(ApprovalStatus.EXPIRED);
            approvalRepository.save(entity);
            return validationResult(entity, false, false, "approval_expired");
        }

        if (!entity.getSlackUserId().equals(slackUserId)) {
            return validationResult(entity, false, false, "wrong_user");
        }

        if (!ApprovalStatus.PENDING.equals(entity.getStatus())) {
            return validationResult(entity, false, false, "approval_not_pending");
        }

        boolean approved;
        if (ApprovalAction.RESTART_SERVICE.name().equals(entity.getAction())) {
            approved = "APPROVE_RESTART".equalsIgnoreCase(response);
        } else if (ApprovalAction.CREATE_PULL_REQUEST.name().equals(entity.getAction())) {
            approved = "APPROVE_PR".equalsIgnoreCase(response);
        } else {
            approved = false;
        }

        if (!approved) {
            entity.setStatus(ApprovalStatus.DENIED);
            approvalRepository.save(entity);
            return validationResult(entity, false, true, "approval_denied");
        }

        entity.setStatus(ApprovalStatus.APPROVED);
        approvalRepository.save(entity);
        return validationResult(entity, true, true, "approved");
    }

    @Transactional(readOnly = true)
    public PullRequestPlan getPullRequestPlan(String approvalId) {
        ApprovalEntity entity = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));

        if (!ApprovalAction.CREATE_PULL_REQUEST.name().equals(entity.getAction())) {
            throw new IllegalStateException("Approval is not for pull request creation: " + approvalId);
        }

        return new PullRequestPlan(
                entity.getApprovalId(),
                entity.getIncidentId(),
                entity.getTargetSystem(),
                entity.getRepoName(),
                entity.getTargetFile(),
                entity.getReplacementContent(),
                entity.getDiagnosticSummary()
        );
    }

    @Transactional
    public void markUsed(String approvalId) {
        approvalRepository.findById(approvalId).ifPresent(entity -> {
            entity.setStatus(ApprovalStatus.USED);
            approvalRepository.save(entity);
        });
    }

    private ApprovalValidationResult validationResult(ApprovalEntity entity,
                                                      boolean approved,
                                                      boolean authorized,
                                                      String reason) {
        return new ApprovalValidationResult(
                approved,
                authorized,
                entity.getStatus().name(),
                reason,
                entity.getIncidentId(),
                entity.getAction(),
                entity.getTargetSystem(),
                entity.getRepoName(),
                entity.getTargetFile(),
                entity.getReplacementContent(),
                entity.getDiagnosticSummary()
        );
    }

    private String buildIncidentId(String eventDate, String errorMessage) {
        return Integer.toHexString((eventDate + ":" + errorMessage).hashCode());
    }
}
