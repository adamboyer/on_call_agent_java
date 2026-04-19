package com.example.oncallagent.controller;

import com.example.oncallagent.model.AgentDecision;
import com.example.oncallagent.model.AgentEvent;
import com.example.oncallagent.service.AgentDriverService;
import com.example.oncallagent.service.SlackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/slack")
public class SlackInteractionController {

    private static final Logger log = LoggerFactory.getLogger(SlackInteractionController.class);

    private final AgentDriverService agentDriverService;
    private final SlackService slackService;
    private final ObjectMapper objectMapper;

    public SlackInteractionController(AgentDriverService agentDriverService,
                                      SlackService slackService,
                                      ObjectMapper objectMapper) {
        this.agentDriverService = agentDriverService;
        this.slackService = slackService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/actions", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, String>> handleAction(@RequestParam("payload") String payload,
                                                            HttpServletRequest request) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String actionValue = root.path("actions").get(0).path("value").asText();
            String slackUserId = root.path("user").path("id").asText();
            String responseUrl = root.path("response_url").asText();
            String channelId = root.path("channel").path("id").asText(null);

            if (actionValue == null || actionValue.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("text", "Missing Slack action value."));
            }

            String[] parts = actionValue.split("\\|", 2);
            if (parts.length != 2) {
                return ResponseEntity.badRequest().body(Map.of("text", "Invalid approval action payload."));
            }

            String action = parts[0];
            String approvalId = parts[1];
            String response = "DENY_RESTART".equals(action) ? "DENY_RESTART" : "APPROVE_RESTART";

            AgentDecision decision = agentDriverService.handle(AgentEvent.approvalResponse(
                    approvalId,
                    slackUserId,
                    response
            ));

            String resultText = String.format("*Approval result:* %s\n*Summary:* %s",
                    decision.approvalStatus(),
                    decision.summary());

            boolean updated = slackService.sendInteractiveResponse(responseUrl, resultText, true);
            if (!updated && channelId != null) {
                slackService.sendPostMessage(channelId, resultText);
            }

            return ResponseEntity.ok(Map.of("text", "Thanks — your response has been processed."));
        } catch (IOException ex) {
            log.error("Failed to process Slack interactive action", ex);
            return ResponseEntity.badRequest().body(Map.of("text", "Unable to parse Slack payload."));
        } catch (Exception ex) {
            log.error("Unexpected error handling Slack action", ex);
            return ResponseEntity.status(500).body(Map.of("text", "Internal error processing approval."));
        }
    }
}
