package com.example.oncallagent.model;

public record DiagnosticResult(
        String summary,
        String recommendedAction,
        String confidence,
        boolean approvalRequired,
        String targetSystem
) {
}
