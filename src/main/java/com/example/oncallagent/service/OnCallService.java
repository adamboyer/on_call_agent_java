package com.example.oncallagent.service;

import com.example.oncallagent.model.OnCallUser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OnCallService {

    private static final Logger log = LoggerFactory.getLogger(OnCallService.class);

    private final Map<String, OnCallUser> onCallByTeam = new HashMap<>();

    @PostConstruct
    void init() {
        onCallByTeam.put(
                "default",
                new OnCallUser("Alice Oncall", "U12345678", "default")
        );

        onCallByTeam.put(
                "payments",
                new OnCallUser("Bob Payments", "U23456789", "payments")
        );

        log.info("OnCallService initialized with {} team entries", onCallByTeam.size());
    }

    public OnCallUser getCurrentOncall() {
        log.info("Fetching current on-call for default team");
        return getCurrentOncallForTeam("default");
    }

    public OnCallUser getCurrentOncallForTeam(String team) {
        log.info("Fetching current on-call for team={}", team);

        OnCallUser user = onCallByTeam.getOrDefault(team, onCallByTeam.get("default"));

        log.info("Resolved on-call user. team={}, slackUserId={}, name={}",
                team,
                user.slackUserId(),
                user.name());

        return user;
    }
}