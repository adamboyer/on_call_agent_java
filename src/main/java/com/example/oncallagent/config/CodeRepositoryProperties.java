package com.example.oncallagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code.repo")
public record CodeRepositoryProperties(
        String defaultTargetSystem,
        String defaultRepoName,
        String defaultBaseBranch
) {
}
