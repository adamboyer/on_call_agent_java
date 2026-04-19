package com.example.oncallagent.service;

import com.example.oncallagent.model.RestartResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RestartService {

    private static final Logger log = LoggerFactory.getLogger(RestartService.class);

    public RestartResult restartService(String approvalId, String slackUserId, String targetSystem) {
        log.info("RestartService invoked. approvalId={}, slackUserId={}, targetSystem={}",
                approvalId, slackUserId, targetSystem);

        try {
            // 🔧 Replace this with real restart logic later
            log.info("Simulating restart for system={}", targetSystem);

            String message = "Restart triggered for %s by approved request %s from user %s"
                    .formatted(targetSystem, approvalId, slackUserId);

            RestartResult result = new RestartResult(
                    "RESTART_TRIGGERED",
                    targetSystem,
                    message,
                    true
            );

            log.info("Restart successful. targetSystem={}, approvalId={}", targetSystem, approvalId);
            log.debug("RestartResult={}", result);

            return result;

        } catch (Exception ex) {
            log.error("Restart failed. approvalId={}, slackUserId={}, targetSystem={}",
                    approvalId, slackUserId, targetSystem, ex);

            return new RestartResult(
                    "RESTART_FAILED",
                    targetSystem,
                    ex.getMessage(),
                    false
            );
        }
    }
}