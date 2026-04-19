package com.example.oncallagent.model;

public record OnCallUser(
        String name,
        String slackUserId,
        String team
) {
}
