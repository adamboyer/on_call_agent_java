package com.example.oncallagent.service;

import com.example.oncallagent.model.OnCallUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OnCallService {

    private static final Logger log = LoggerFactory.getLogger(OnCallService.class);

    private final AwsS3Service awsS3Service;
    private final Map<String, OnCallUser> onCallByTeam = new HashMap<>();

    public OnCallService(AwsS3Service awsS3Service) {
        this.awsS3Service = awsS3Service;
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
        try {
            OnCallUser user = awsS3Service.getCurrentOnCallUser();
            log.info("Resolved current on-call from S3. slackUserId={}, name={}",
                    user.slackUserId(),
                    user.name());
            return user;
        } catch (Exception ex) {
            log.warn("Unable to fetch current on-call user from S3; using local fallback.", ex);
            return getFallbackUser("default");
        }
    }

    public OnCallUser getCurrentOnCall() {
        return getCurrentOncall();
    }

    public OnCallUser getCurrentOncallForTeam(String team) {
        log.info("Fetching current on-call for team={}", team);

        OnCallUser user;
        try {
            user = awsS3Service.getCurrentOnCallUser();
            if (user.team() != null && team != null && !team.equalsIgnoreCase(user.team())) {
                user = getFallbackUser(team);
            }
        } catch (Exception ex) {
            log.warn("Unable to fetch current on-call user from S3 for team={}; using local fallback.",
                    team, ex);
            user = getFallbackUser(team);
        }

        log.info("Resolved on-call user. team={}, slackUserId={}, name={}",
                team,
                user.slackUserId(),
                user.name());

        return user;
    }

    private OnCallUser getFallbackUser(String team) {
        return onCallByTeam.getOrDefault(team, onCallByTeam.get("default"));
    }
}
