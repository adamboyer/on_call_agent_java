package com.example.oncallagent.model;

public record RepositoryContext(
        String repoName,
        String baseBranch,
        String localPath
) {
}