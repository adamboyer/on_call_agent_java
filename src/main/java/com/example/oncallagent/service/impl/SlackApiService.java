package com.example.oncallagent.service.impl;

import com.example.oncallagent.config.SlackProperties;
import com.example.oncallagent.model.SlackMessageResult;
import com.example.oncallagent.service.SlackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
@Primary
@ConditionalOnProperty(name = "slack.bot-token")
public class SlackApiService implements SlackService {

    private static final URI POST_MESSAGE_URI = URI.create("https://slack.com/api/chat.postMessage");
    private static final Logger log = LoggerFactory.getLogger(SlackApiService.class);

    private final String botToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SlackApiService(SlackProperties properties, ObjectMapper objectMapper) {
        this.botToken = properties.botToken();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public SlackMessageResult sendChannelMessage(String channelId, String text) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Slack bot token is not configured. Cannot send message to channel={}", channelId);
            return new SlackMessageResult(false, channelId, null, "slack_bot_token_missing");
        }

        try {
            log.info("Sending Slack message to channel={}", channelId);

            HttpRequest request = HttpRequest.newBuilder(POST_MESSAGE_URI)
                    .header("Authorization", "Bearer " + botToken.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "channel", channelId,
                            "text", text
                    ))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Slack send failed. status={}, body={}", response.statusCode(), response.body());
                return new SlackMessageResult(false, channelId, null, "http_" + response.statusCode());
            }

            SlackResponse slackResponse = objectMapper.readValue(response.body(), SlackResponse.class);
            if (!slackResponse.ok()) {
                log.error("Slack send failed. error={}", slackResponse.error());
                return new SlackMessageResult(false, channelId, null, slackResponse.error());
            }

            log.info("Slack message sent. channel={}, ts={}", channelId, slackResponse.ts());
            return new SlackMessageResult(true, channelId, slackResponse.ts(), null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Slack API interrupted while sending to channel={}", channelId, ex);
            return new SlackMessageResult(false, channelId, null, "interrupted");
        } catch (IOException ex) {
            log.error("Slack API I/O exception while sending to channel={}", channelId, ex);
            return new SlackMessageResult(false, channelId, null, ex.getMessage());
        }
    }

    @Override
    public boolean sendApprovalRequest(String slackUserId,
                                       String approvalId,
                                       String eventDate,
                                       String errorMessage,
                                       String diagnosticSummary,
                                       String recommendedAction,
                                       String targetSystem) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Slack bot token is not configured. Approval request will not be delivered to Slack.");
            return false;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "channel", slackUserId,
                    "text", "Approval requested for incident restart",
                    "blocks", List.of(
                            Map.of(
                                    "type", "section",
                                    "text", Map.of(
                                            "type", "mrkdwn",
                                            "text", "*Restart approval requested for your on-call incident*"
                                    )
                            ),
                            Map.of(
                                    "type", "section",
                                    "fields", List.of(
                                            Map.of("type", "mrkdwn", "text", "*Event Date:*\n" + eventDate),
                                            Map.of("type", "mrkdwn", "text", "*Target System:*\n" + targetSystem),
                                            Map.of("type", "mrkdwn", "text", "*Recommendation:*\n" + recommendedAction),
                                            Map.of("type", "mrkdwn", "text", "*Summary:*\n" + diagnosticSummary)
                                    )
                            ),
                            Map.of(
                                    "type", "section",
                                    "text", Map.of(
                                            "type", "mrkdwn",
                                            "text", "*Error:*\n" + errorMessage
                                    )
                            ),
                            Map.of(
                                    "type", "actions",
                                    "elements", List.of(
                                            Map.of(
                                                    "type", "button",
                                                    "text", Map.of("type", "plain_text", "text", "Approve Restart"),
                                                    "style", "primary",
                                                    "value", "APPROVE_RESTART|" + approvalId,
                                                    "action_id", "approve_restart"
                                            ),
                                            Map.of(
                                                    "type", "button",
                                                    "text", Map.of("type", "plain_text", "text", "Deny Restart"),
                                                    "style", "danger",
                                                    "value", "DENY_RESTART|" + approvalId,
                                                    "action_id", "deny_restart"
                                            )
                                    )
                            )
                    )
            );

            HttpRequest request = HttpRequest.newBuilder(POST_MESSAGE_URI)
                    .header("Authorization", "Bearer " + botToken.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Slack approval request returned non-200 status: {}", response.statusCode());
                return false;
            }

            SlackResponse slackResponse = objectMapper.readValue(response.body(), SlackResponse.class);
            if (!slackResponse.ok()) {
                log.warn("Slack approval request rejected: {}", slackResponse.error());
                return false;
            }

            log.info("Slack approval request sent. slackUserId={}, approvalId={}, ts={}",
                    slackUserId, approvalId, slackResponse.ts());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Failed to send Slack approval request", ex);
            return false;
        } catch (IOException ex) {
            log.error("Failed to send Slack approval request", ex);
            return false;
        }
    }

    @Override
    public boolean sendInteractiveResponse(String responseUrl, String text, boolean replaceOriginal) {
        if (responseUrl == null || responseUrl.isBlank()) {
            log.warn("Slack interactive response skipped because responseUrl is blank.");
            return false;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(responseUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "text", text,
                            "replace_original", replaceOriginal,
                            "response_type", "ephemeral"
                    ))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Failed to send Slack interactive response", ex);
            return false;
        } catch (IOException ex) {
            log.error("Failed to send Slack interactive response", ex);
            return false;
        }
    }

    @Override
    public boolean sendPostMessage(String channel, String text) {
        return sendChannelMessage(channel, text).ok();
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
        if (botToken == null || botToken.isBlank()) {
            log.warn("Slack bot token is not configured. PR approval request will not be delivered to Slack.");
            return false;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "channel", slackUserId,
                    "text", "Approval requested for automated pull request",
                    "blocks", List.of(
                            Map.of(
                                    "type", "section",
                                    "text", Map.of(
                                            "type", "mrkdwn",
                                            "text", "*Pull request approval requested for your on-call incident*"
                                    )
                            ),
                            Map.of(
                                    "type", "section",
                                    "fields", List.of(
                                            Map.of("type", "mrkdwn", "text", "*Event Date:*\n" + eventDate),
                                            Map.of("type", "mrkdwn", "text", "*Target System:*\n" + targetSystem),
                                            Map.of("type", "mrkdwn", "text", "*Repository:*\n" + repoName),
                                            Map.of("type", "mrkdwn", "text", "*File:*\n" + targetFile),
                                            Map.of("type", "mrkdwn", "text", "*Recommendation:*\n" + recommendedAction),
                                            Map.of("type", "mrkdwn", "text", "*Summary:*\n" + diagnosticSummary)
                                    )
                            ),
                            Map.of(
                                    "type", "section",
                                    "text", Map.of(
                                            "type", "mrkdwn",
                                            "text", "*Error:*\n" + errorMessage
                                    )
                            ),
                            Map.of(
                                    "type", "actions",
                                    "elements", List.of(
                                            Map.of(
                                                    "type", "button",
                                                    "text", Map.of("type", "plain_text", "text", "Approve PR"),
                                                    "style", "primary",
                                                    "value", "APPROVE_PR|" + approvalId,
                                                    "action_id", "approve_pr"
                                            ),
                                            Map.of(
                                                    "type", "button",
                                                    "text", Map.of("type", "plain_text", "text", "Deny PR"),
                                                    "style", "danger",
                                                    "value", "DENY_PR|" + approvalId,
                                                    "action_id", "deny_pr"
                                            )
                                    )
                            )
                    )
            );

            HttpRequest request = HttpRequest.newBuilder(POST_MESSAGE_URI)
                    .header("Authorization", "Bearer " + botToken.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Slack PR approval request returned non-200 status: {}", response.statusCode());
                return false;
            }

            SlackResponse slackResponse = objectMapper.readValue(response.body(), SlackResponse.class);
            if (!slackResponse.ok()) {
                log.warn("Slack PR approval request rejected: {}", slackResponse.error());
                return false;
            }

            log.info("Slack PR approval request sent. slackUserId={}, approvalId={}, ts={}",
                    slackUserId, approvalId, slackResponse.ts());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Failed to send Slack PR approval request", ex);
            return false;
        } catch (IOException ex) {
            log.error("Failed to send Slack PR approval request", ex);
            return false;
        }
    }

    private record SlackResponse(boolean ok, String error, String ts) {
    }
}
