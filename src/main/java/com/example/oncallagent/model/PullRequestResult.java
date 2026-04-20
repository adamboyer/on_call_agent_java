package com.example.oncallagent.model;

public record PullRequestResult(
        boolean success,
        String pullRequestUrl,
        String pullRequestNumber,
        String branchName,
        String status,
        String message
) {
}