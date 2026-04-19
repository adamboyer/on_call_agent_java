package com.example.oncallagent.service;

import com.example.oncallagent.entity.ApprovalEntity;
import com.example.oncallagent.model.ApprovalAction;
import com.example.oncallagent.model.ApprovalStatus;
import com.example.oncallagent.model.ApprovalValidationResult;
import com.example.oncallagent.repository.ApprovalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ApprovalService {

    private final ApprovalRepository approvalRepository;

    public ApprovalService(ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    @Transactional
    public Map<String, Object> requestRestartApproval(String slackId,
                                                      String eventDate,
                                                      String errorMessage,
                                                      String diagnosticSummary,
                                                      String recommendedAction,
                                                      String targetSystem) {
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

        return Map.of(
                "approvalId", entity.getApprovalId(),
                "approvalStatus", entity.getStatus().name(),
                "slackUserId", slackId,
                "message", "Slack approval request created. In production this is where you would send the Slack message.",
                "diagnosticSummary", diagnosticSummary,
                "recommendedAction", recommendedAction,
                "incidentId", entity.getIncidentId(),
                "targetSystem", targetSystem
        );
    }

    @Transactional
    public ApprovalValidationResult validateApprovalResponse(String approvalId,
                                                             String slackUserId,
                                                             String response) {
        ApprovalEntity entity = approvalRepository.findById(approvalId)
                .orElse(null);

        if (entity == null) {
            return new ApprovalValidationResult(false, false, "NOT_FOUND", "approval_not_found", null, null, null);
        }

        if (Instant.now().isAfter(entity.getExpiresAt())) {
            entity.setStatus(ApprovalStatus.EXPIRED);
            approvalRepository.save(entity);
            return new ApprovalValidationResult(false, false, entity.getStatus().name(), "approval_expired",
                    entity.getIncidentId(), entity.getAction(), entity.getTargetSystem());
        }

        if (!entity.getSlackUserId().equals(slackUserId)) {
            return new ApprovalValidationResult(false, false, entity.getStatus().name(), "wrong_user",
                    entity.getIncidentId(), entity.getAction(), entity.getTargetSystem());
        }

        if (!ApprovalStatus.PENDING.equals(entity.getStatus())) {
            return new ApprovalValidationResult(false, false, entity.getStatus().name(), "approval_not_pending",
                    entity.getIncidentId(), entity.getAction(), entity.getTargetSystem());
        }

        if (!"APPROVE_RESTART".equalsIgnoreCase(response)) {
            entity.setStatus(ApprovalStatus.DENIED);
            approvalRepository.save(entity);
            return new ApprovalValidationResult(false, true, entity.getStatus().name(), "restart_denied",
                    entity.getIncidentId(), entity.getAction(), entity.getTargetSystem());
        }

        entity.setStatus(ApprovalStatus.APPROVED);
        approvalRepository.save(entity);
        return new ApprovalValidationResult(true, true, entity.getStatus().name(), "approved",
                entity.getIncidentId(), entity.getAction(), entity.getTargetSystem());
    }

    @Transactional
    public void markUsed(String approvalId) {
        approvalRepository.findById(approvalId).ifPresent(entity -> {
            entity.setStatus(ApprovalStatus.USED);
            approvalRepository.save(entity);
        });
    }

    private String buildIncidentId(String eventDate, String errorMessage) {
        return Integer.toHexString((eventDate + ":" + errorMessage).hashCode());
    }
}
