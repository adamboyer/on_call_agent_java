package com.example.oncallagent.service;

import com.example.oncallagent.model.RestartResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RestartService {

    private static final Logger log = LoggerFactory.getLogger(RestartService.class);
    private static final String RETRY_FOLDER = "Retry/";
    private static final String INPUT_FOLDER = "Input/";

    private final AwsS3Service awsS3Service;

    public RestartService(AwsS3Service awsS3Service) {
        this.awsS3Service = awsS3Service;
    }

    public RestartResult restartService(String approvalId, String slackUserId, String targetSystem) {
        try {
            log.info("[TOOL] Starting S3 file copy operation from {} to {}", RETRY_FOLDER, INPUT_FOLDER);

            List<String> retryFiles = awsS3Service.listFilesInFolder(RETRY_FOLDER);
            if (retryFiles.isEmpty()) {
                log.warn("No files found in {} folder", RETRY_FOLDER);
                return new RestartResult(
                        "FILE_NOT_FOUND",
                        targetSystem,
                        "No files available in Retry folder for approval %s by user %s"
                                .formatted(approvalId, slackUserId),
                        false
                );
            }

            for (String sourceKey : retryFiles) {
                String fileName = sourceKey.substring(sourceKey.lastIndexOf('/') + 1);
                String destinationKey = INPUT_FOLDER + fileName;
                awsS3Service.copyFileWithinBucket(sourceKey, destinationKey);
                log.info("[TOOL] Successfully copied {} to {}", sourceKey, destinationKey);
            }

            RestartResult result = new RestartResult(
                    "FILE_COPIED_SUCCESS",
                    targetSystem,
                    "Successfully copied %d file(s) from %s to %s for approval %s by user %s"
                            .formatted(retryFiles.size(), RETRY_FOLDER, INPUT_FOLDER, approvalId, slackUserId),
                    true
            );

            log.info("Restart file copy successful. targetSystem={}, approvalId={}, fileCount={}",
                    targetSystem, approvalId, retryFiles.size());
            log.debug("RestartResult={}", result);

            return result;

        } catch (Exception ex) {
            log.error("[TOOL] Failed to copy files from S3 Retry to Input folder", ex);

            return new RestartResult(
                    "FILE_COPY_FAILED",
                    targetSystem,
                    "Error copying files from Retry to Input folder: %s for approval %s"
                            .formatted(ex.getMessage(), approvalId),
                    false
            );
        }
    }
}
