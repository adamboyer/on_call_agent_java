package com.example.oncallagent.service.impl;

import com.example.oncallagent.model.SlackMessageResult;
import com.example.oncallagent.service.SlackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "test"})
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
}