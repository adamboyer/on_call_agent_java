package com.example.oncallagent.model;

public record CodeAnalysisResult(
        boolean confidentFixAvailable,
        String summary,
        String proposedChange,
        String targetFile,
        String replacementContent,
        String confidence,
        String repoName
) {
}
