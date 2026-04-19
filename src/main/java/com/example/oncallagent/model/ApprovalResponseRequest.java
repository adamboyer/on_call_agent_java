package com.example.oncallagent.model;

import jakarta.validation.constraints.NotBlank;

public record ApprovalResponseRequest(
        @NotBlank String approvalId,
        @NotBlank String slackUserId,
        @NotBlank String response
) {
}
