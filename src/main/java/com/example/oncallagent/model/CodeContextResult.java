package com.example.oncallagent.model;

public record CodeContextResult(
        String repoName,
        String baseBranch,
        String localPath,
        String codeContext
) {
}