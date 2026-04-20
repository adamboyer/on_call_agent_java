package com.example.oncallagent.service.impl;

import com.example.oncallagent.model.SlackMessageResult;
import com.example.oncallagent.service.SlackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(SlackService.class)
public class LoggingSlackService implements SlackService {

    private static final Logger log = LoggerFactory.getLogger(LoggingSlackService.class);

    @Override
    public SlackMessageResult sendChannelMessage(String channelId, String text) {
        log.info("LOCAL Slack stub sendChannelMessage. channelId={}, text={}", channelId, text);

        return new SlackMessageResult(
                true,
                channelId,
                String.valueOf(System.currentTimeMillis()),
                null
        );
    }

    @Override
    public boolean sendApprovalRequest(String slackUserId,
                                       String approvalId,
                                       String eventDate,
                                       String errorMessage,
                                       String diagnosticSummary,
                                       String recommendedAction,
                                       String targetSystem) {
        log.warn("""
                LOCAL Slack stub sendApprovalRequest.
                slackUserId={}, approvalId={}, targetSystem={}, recommendedAction={}
                """,
                slackUserId,
                approvalId,
                targetSystem,
                recommendedAction);
        return true;
    }

    @Override
    public boolean sendPullRequestApprovalRequest(String slackUserId,
                                                  String approvalId,
                                                  String eventDate,
                                                  String errorMessage,
                                                  String diagnosticSummary,
                                                  String recommendedAction,
                                                  String targetSystem,
                                                  String repoName,
                                                  String targetFile) {
        log.warn("""
                LOCAL Slack stub sendPullRequestApprovalRequest.
                slackUserId={}, approvalId={}, repoName={}, targetFile={}
                """,
                slackUserId,
                approvalId,
                repoName,
                targetFile);
        return true;
    }
}
