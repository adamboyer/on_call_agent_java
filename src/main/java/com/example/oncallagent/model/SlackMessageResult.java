package com.example.oncallagent.model;

public record SlackMessageResult(
        boolean ok,
        String channelId,
        String messageTs,
        String error
) {
}