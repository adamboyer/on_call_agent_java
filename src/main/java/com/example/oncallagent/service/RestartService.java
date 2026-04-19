package com.example.oncallagent.service;

import com.example.oncallagent.model.RestartResult;
import org.springframework.stereotype.Service;

@Service
public class RestartService {

    public RestartResult restartService(String approvalId, String slackUserId, String targetSystem) {
        return new RestartResult(
                "RESTART_TRIGGERED",
                targetSystem,
                "Restart triggered for %s by approved request %s from user %s"
                        .formatted(targetSystem, approvalId, slackUserId)
        );
    }
}
