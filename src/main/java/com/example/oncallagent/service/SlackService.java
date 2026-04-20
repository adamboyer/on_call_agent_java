package com.example.oncallagent.service;

import com.example.oncallagent.model.SlackMessageResult;

public interface SlackService {
    SlackMessageResult sendChannelMessage(String channelId, String text);

    default boolean sendApprovalRequest(String slackUserId,
                                        String approvalId,
                                        String eventDate,
                                        String errorMessage,
                                        String diagnosticSummary,
                                        String recommendedAction,
                                        String targetSystem) {
        String message = """
                Approval requested for incident restart

                Event Date: %s
                Target System: %s
                Error: %s
                Summary: %s
                Recommendation: %s
                Approval ID: %s
                """.formatted(
                eventDate,
                targetSystem,
                errorMessage,
                diagnosticSummary,
                recommendedAction,
                approvalId
        );
        return sendChannelMessage(slackUserId, message).ok();
    }

    default boolean sendInteractiveResponse(String responseUrl, String text, boolean replaceOriginal) {
        return false;
    }

    default boolean sendPostMessage(String channel, String text) {
        return sendChannelMessage(channel, text).ok();
    }

    default boolean sendPullRequestApprovalRequest(String slackUserId,
                                                   String approvalId,
                                                   String eventDate,
                                                   String errorMessage,
                                                   String diagnosticSummary,
                                                   String recommendedAction,
                                                   String targetSystem,
                                                   String repoName,
                                                   String targetFile) {
        String message = """
                Pull request approval requested

                Event Date: %s
                Target System: %s
                Repository: %s
                File: %s
                Error: %s
                Summary: %s
                Recommendation: %s
                Approval ID: %s
                """.formatted(
                eventDate,
                targetSystem,
                repoName,
                targetFile,
                errorMessage,
                diagnosticSummary,
                recommendedAction,
                approvalId
        );
        return sendChannelMessage(slackUserId, message).ok();
    }
}
