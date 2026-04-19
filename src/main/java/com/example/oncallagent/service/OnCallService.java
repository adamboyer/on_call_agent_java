package com.example.oncallagent.service;

import com.example.oncallagent.model.OnCallUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OnCallService {

    private static final Logger log = LoggerFactory.getLogger(OnCallService.class);

    private final AwsS3Service awsS3Service;

    public OnCallService(AwsS3Service awsS3Service) {
        this.awsS3Service = awsS3Service;
    }

    public OnCallUser getCurrentOnCall() {
        try {
            return awsS3Service.getCurrentOnCallUser();
        } catch (Exception ex) {
            log.warn("Unable to fetch current on-call user from S3; using local fallback.", ex);
            return new OnCallUser(
                    "Alex Oncall",
                    "U12345678",
                    "platform"
            );
        }
    }
}
