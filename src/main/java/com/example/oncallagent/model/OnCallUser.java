package com.example.oncallagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record OnCallUser(
        @JsonProperty("person_name") String name,
        @JsonProperty("slack_user_id") String slackUserId,
        @JsonProperty("start_date") OffsetDateTime startDate,
        @JsonProperty("end_date") OffsetDateTime endDate,
        String team
) {
    public OnCallUser(String name, String slackUserId, String team) {
        this(name, slackUserId, null, null, team);
    }
}
