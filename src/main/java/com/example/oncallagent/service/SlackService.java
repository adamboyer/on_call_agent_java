package com.example.oncallagent.service;

import com.example.oncallagent.model.SlackMessageResult;

public interface SlackService {
    SlackMessageResult sendChannelMessage(String channelId, String text);
}