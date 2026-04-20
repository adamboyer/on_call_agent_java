package com.example.oncallagent.service;

import com.example.oncallagent.config.CodeRepositoryProperties;
import com.example.oncallagent.model.RepositoryContext;
import org.springframework.stereotype.Service;

@Service
public class RepositoryContextService {

    private final CodeRepositoryProperties codeRepositoryProperties;

    public RepositoryContextService(CodeRepositoryProperties codeRepositoryProperties) {
        this.codeRepositoryProperties = codeRepositoryProperties;
    }

    public RepositoryContext resolveContext(String targetSystem) {
        String normalized = targetSystem == null ? "" : targetSystem.trim().toLowerCase();
        String configuredTarget = safe(codeRepositoryProperties.defaultTargetSystem()).toLowerCase();
        String configuredRepo = safe(codeRepositoryProperties.defaultRepoName());

        if (!configuredRepo.isBlank() && (configuredTarget.isBlank()
                || normalized.isBlank()
                || normalized.equals(configuredTarget)
                || normalized.contains(configuredTarget)
                || !normalized.contains("/"))) {
            return new RepositoryContext(
                    configuredRepo,
                    defaultBaseBranch(),
                    ""
            );
        }

        return new RepositoryContext(
                targetSystem,
                defaultBaseBranch(),
                ""
        );
    }

    private String defaultBaseBranch() {
        String branch = safe(codeRepositoryProperties.defaultBaseBranch());
        return branch.isBlank() ? "master" : branch;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
