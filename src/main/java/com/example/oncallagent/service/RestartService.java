package com.example.oncallagent.service;

import com.example.oncallagent.model.RestartResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RestartService {

    private static final Logger log = LoggerFactory.getLogger(RestartService.class);
    private static final String RETRY_FOLDER = "Retry/";
    private static final String INPUT_FOLDER = "Input/";

    @Autowired
    private AwsS3Service awsS3Service;

    public RestartResult restartService(String approvalId, String slackUserId, String targetSystem) {
        try {
            log.info("[TOOL] Starting S3 file copy operation from {} to {}", RETRY_FOLDER, INPUT_FOLDER);

            // List files in Retry folder
            List<String> retryFiles = awsS3Service.listFilesInFolder(RETRY_FOLDER);
            if (retryFiles.isEmpty()) {
                log.warn("No files found in {} folder", RETRY_FOLDER);
                return new RestartResult(
                        "FILE_NOT_FOUND",
                        targetSystem,
                        "No files available in Retry folder for approval %s by user %s"
                                .formatted(approvalId, slackUserId));
            }

            // Copy files from Retry to Input folder
            for (String sourceKey : retryFiles) {
                String fileName = sourceKey.substring(sourceKey.lastIndexOf('/') + 1);
                String destinationKey = INPUT_FOLDER + fileName;
                awsS3Service.copyFileWithinBucket(sourceKey, destinationKey);
                log.info("[TOOL] Successfully copied {} to {}", sourceKey, destinationKey);
            }

            return new RestartResult(
                    "FILE_COPIED_SUCCESS",
                    targetSystem,
                    "Successfully copied %d file(s) from %s to %s for approval %s by user %s"
                            .formatted(retryFiles.size(), RETRY_FOLDER, INPUT_FOLDER, approvalId, slackUserId));
        } catch (Exception e) {
            log.error("[TOOL] Failed to copy files from S3 Retry to Input folder", e);
            return new RestartResult(
                    "FILE_COPY_FAILED",
                    targetSystem,
                    "Error copying files from Retry to Input folder: %s for approval %s"
                            .formatted(e.getMessage(), approvalId));
        }
    }
}
