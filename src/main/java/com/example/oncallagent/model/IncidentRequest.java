package com.example.oncallagent.model;

import jakarta.validation.constraints.NotBlank;

public record IncidentRequest(
        @NotBlank String eventDate,
        @NotBlank String errorMessage
) {
}
