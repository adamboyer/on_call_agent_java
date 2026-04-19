package com.example.oncallagent.model;

public record RestartResult(
        String status,
        String targetSystem,
        String message,
        boolean success
) {
}