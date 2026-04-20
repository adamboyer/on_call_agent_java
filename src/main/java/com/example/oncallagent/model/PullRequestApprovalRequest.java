package com.example.oncallagent.model;

public record PullRequestApprovalRequest(
        String slackId,
        String eventDate,
        String errorMessage,
        String diagnosticSummary,
        String recommendedAction,
        String targetSystem,
        String repoName,
        String targetFile,
        String replacementContent
) {
}
