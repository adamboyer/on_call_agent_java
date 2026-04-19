package com.example.oncallagent.model;

public record RestartResult(
        String restartStatus,
        String targetSystem,
        String details
) {
}
