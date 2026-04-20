package com.example.oncallagent.service.impl;

import com.example.oncallagent.config.SlackProperties;
import com.example.oncallagent.model.SlackMessageResult;
import com.example.oncallagent.service.SlackService;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local & !test")
public class SlackApiService implements SlackService {

    private static final Logger log = LoggerFactory.getLogger(SlackApiService.class);

    private final MethodsClient methodsClient;

    public SlackApiService(SlackProperties properties) {
        this.methodsClient = Slack.getInstance().methods(properties.botToken());
    }

    @Override
    public SlackMessageResult sendChannelMessage(String channelId, String text) {
        try {
            log.info("Sending Slack message to channel={}", channelId);

            ChatPostMessageResponse response = methodsClient.chatPostMessage(
                    ChatPostMessageRequest.builder()
                            .channel(channelId)
                            .text(text)
                            .build()
            );

            if (!response.isOk()) {
                log.error("Slack send failed. error={}", response.getError());
                return new SlackMessageResult(false, channelId, null, response.getError());
            }

            log.info("Slack message sent. channel={}, ts={}", channelId, response.getTs());

            return new SlackMessageResult(true, channelId, response.getTs(), null);

        } catch (Exception ex) {
            log.error("Slack API exception", ex);
            return new SlackMessageResult(false, channelId, null, ex.getMessage());
        }
    }
}