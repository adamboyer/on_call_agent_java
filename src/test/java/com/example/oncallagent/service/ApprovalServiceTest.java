package com.example.oncallagent.service;

import com.example.oncallagent.model.ApprovalValidationResult;
import com.example.oncallagent.repository.ApprovalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApprovalServiceTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Test
    void shouldApproveValidRestartResponse() {
        Map<String, Object> request = approvalService.requestRestartApproval(
                "U12345678",
                "2026-04-19T14:32:10Z",
                "payments-api repeated 500 errors",
                "Known restartable failure pattern",
                "RESTART_SERVICE",
                "payments-api"
        );

        String approvalId = (String) request.get("approvalId");

        ApprovalValidationResult result = approvalService.validateApprovalResponse(
                approvalId,
                "U12345678",
                "APPROVE_RESTART"
        );

        assertThat(result.approved()).isTrue();
        assertThat(result.authorized()).isTrue();
        assertThat(result.approvalStatus()).isEqualTo("APPROVED");
        assertThat(approvalRepository.findById(approvalId)).isPresent();
    }
}
