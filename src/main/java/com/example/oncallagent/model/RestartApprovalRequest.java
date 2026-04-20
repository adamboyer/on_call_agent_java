package com.example.oncallagent.model;

public record RestartApprovalRequest(
        String slackId,
        String eventDate,
        String errorMessage,
        String diagnosticSummary,
        String recommendedAction,
        String targetSystem
) {}
