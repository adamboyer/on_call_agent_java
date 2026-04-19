package com.example.oncallagent.service;

import com.example.oncallagent.model.OnCallUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AwsS3Service {

    private static final Logger log = LoggerFactory.getLogger(AwsS3Service.class);

    private final String bucket;
    private final String key;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public AwsS3Service(
            @Value("${aws.s3.bucket:on-call-schedule-agent}") String bucket,
            @Value("${aws.s3.key:oncall_person.json}") String key,
            @Value("${aws.region:}") String awsRegion,
            ObjectMapper objectMapper) {
        this.bucket = bucket;
        this.key = key;
        this.objectMapper = objectMapper;

        if (awsRegion == null || awsRegion.isBlank()) {
            this.s3Client = S3Client.create();
        } else {
            this.s3Client = S3Client.builder()
                    .region(Region.of(awsRegion))
                    .build();
        }
    }

    public OnCallUser getCurrentOnCallUser() {
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())) {

            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, OnCallUser.class);
            List<OnCallUser> schedule = objectMapper.readValue(response, listType);

            OffsetDateTime now = OffsetDateTime.now();
            return schedule.stream()
                    .filter(u -> u.startDate() != null && u.endDate() != null
                            && !now.isBefore(u.startDate())
                            && !now.isAfter(u.endDate()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No on-call user found for current date: " + now));

        } catch (IOException | S3Exception | SdkClientException e) {
            log.error("Failed to load on-call schedule from S3 s3://{}/{}", bucket, key, e);
            throw new IllegalStateException("Unable to load on-call user from S3", e);
        }
    }
}
