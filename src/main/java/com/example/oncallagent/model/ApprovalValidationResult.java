package com.example.oncallagent.model;

public record ApprovalValidationResult(
        boolean approved,
        boolean authorized,
        String approvalStatus,
        String reason,
        String incidentId,
        String action,
        String targetSystem
) {
}
