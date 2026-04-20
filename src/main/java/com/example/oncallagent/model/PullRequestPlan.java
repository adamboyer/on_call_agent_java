package com.example.oncallagent.model;

public record PullRequestPlan(
        String approvalId,
        String incidentId,
        String targetSystem,
        String repoName,
        String targetFile,
        String replacementContent,
        String diagnosticSummary
) {
}
