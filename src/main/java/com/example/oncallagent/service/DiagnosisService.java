package com.example.oncallagent.service;

import com.example.oncallagent.model.DiagnosticResult;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class DiagnosisService {

    public DiagnosticResult runDiagnostic(String eventDate, String errorMessage) {
        String normalized = errorMessage.toLowerCase(Locale.ROOT);

        if (normalized.contains("500") || normalized.contains("timeout") || normalized.contains("stuck")
                || normalized.contains("unhealthy") || normalized.contains("restart")) {
            return new DiagnosticResult(
                    "The service appears unhealthy and matches a restartable failure pattern detected at %s."
                            .formatted(eventDate),
                    "RESTART_SERVICE",
                    "high",
                    true,
                    inferTargetSystem(errorMessage)
            );
        }

        return new DiagnosticResult(
                "The issue does not clearly require a restart. Investigate logs and metrics first.",
                "INVESTIGATE",
                "medium",
                false,
                inferTargetSystem(errorMessage)
        );
    }

    private String inferTargetSystem(String errorMessage) {
        int separator = errorMessage.indexOf(' ');
        if (separator > 0) {
            return errorMessage.substring(0, separator);
        }
        return "unknown-service";
    }
}
