package com.example.oncallagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
public class SlackService {

    private static final URI POST_MESSAGE_URI = URI.create("https://slack.com/api/chat.postMessage");
    private static final Logger log = LoggerFactory.getLogger(SlackService.class);

    private final String botToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SlackService(@Value("${slack.bot-token:}") String botToken,
                        ObjectMapper objectMapper) {
        this.botToken = botToken;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

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

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(POST_MESSAGE_URI)
                    .header("Authorization", "Bearer " + botToken.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Slack API returned non-200 status: {}", response.statusCode());
                return false;
            }

            SlackResponse slackResponse = objectMapper.readValue(response.body(), SlackResponse.class);
            if (!slackResponse.ok()) {
                log.warn("Slack API rejected message: {}", slackResponse.error());
                return false;
            }

            return true;
        } catch (IOException | InterruptedException e) {
            log.error("Failed to send Slack approval request", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean sendInteractiveResponse(String responseUrl, String text, boolean replaceOriginal) {
        if (responseUrl == null || responseUrl.isBlank()) {
            return false;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "text", text,
                    "replace_original", replaceOriginal,
                    "response_type", "ephemeral"
            );

            HttpRequest request = HttpRequest.newBuilder(URI.create(responseUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            log.error("Failed to send Slack interactive response", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean sendPostMessage(String channel, String text) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Slack bot token is not configured. Cannot send follow-up message.");
            return false;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "channel", channel,
                    "text", text
            );

            HttpRequest request = HttpRequest.newBuilder(POST_MESSAGE_URI)
                    .header("Authorization", "Bearer " + botToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            log.error("Failed to send Slack post message", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static record SlackResponse(boolean ok, String error) {
    }
}
