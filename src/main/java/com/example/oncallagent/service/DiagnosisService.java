package com.example.oncallagent.service;

import com.example.oncallagent.model.DiagnosticResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class DiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);

    private final ChatClient chatClient;
    private final Map<String, KnownIssue> knownIssues = new LinkedHashMap<>();

    public DiagnosisService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        seedKnownIssues();
    }

    public DiagnosticResult runDiagnostic(String eventDate, String errorMessage) {
    String safeErrorMessage = errorMessage == null ? "" : errorMessage.trim();

    log.info("Starting diagnostic. eventDate={}, errorMessage={}", eventDate, safeErrorMessage);

    String normalizedError = safeErrorMessage.toLowerCase(Locale.ROOT);
    String targetSystem = inferTargetSystem(safeErrorMessage);


        KnownIssue directMatch = findDirectMatch(normalizedError);
        if (directMatch != null) {
            log.info("Direct known issue match found. action={}", directMatch.recommendedAction());
            return toDiagnosticResult(directMatch, eventDate, targetSystem, "direct_match");
        }

        log.info("No direct known issue match found. Falling back to LLM classification.");

        LlmDiagnosisResponse llmResponse = callLlm(eventDate, errorMessage, targetSystem);

        if (llmResponse != null) {
            log.info("LLM response received. action={}, confidence={}",
                    llmResponse.recommendedAction(),
                    llmResponse.confidence());

            if (llmResponse.knownIssueKey() != null) {
                KnownIssue matched = knownIssues.get(llmResponse.knownIssueKey());
                if (matched != null) {
                    log.info("LLM matched known issue key={}", llmResponse.knownIssueKey());
                    return toDiagnosticResult(matched, eventDate, targetSystem, "llm_match");
                }
            }

            return new DiagnosticResult(
                    llmResponse.summary(),
                    llmResponse.recommendedAction(),
                    llmResponse.confidence(),
                    llmResponse.approvalRequired(),
                    targetSystem
            );
        }

        log.warn("LLM call failed. Falling back to INVESTIGATE.");

        return new DiagnosticResult(
                "The issue does not clearly match a restartable operational pattern. Investigate logs and stack trace.",
                "INVESTIGATE",
                "medium",
                false,
                targetSystem
        );
    }

    private KnownIssue findDirectMatch(String normalizedError) {
        for (Map.Entry<String, KnownIssue> entry : knownIssues.entrySet()) {
            for (String keyword : entry.getValue().keywords()) {
                if (normalizedError.contains(keyword)) {
                    log.debug("Keyword '{}' matched known issue '{}'", keyword, entry.getKey());
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private LlmDiagnosisResponse callLlm(String eventDate, String errorMessage, String targetSystem) {
        String knownIssuesContext = buildKnownIssuesContext();

        try {
            return chatClient.prompt()
                    .system("""
                            You are a production incident diagnosis assistant.

                            Your job is to classify the incident into exactly one of these actions:
                            - RESTART_SERVICE
                            - ANALYZE_CODE
                            - INVESTIGATE

                            Rules:
                            - Choose RESTART_SERVICE for transient operational failures like repeated 500s, stuck processes, timeouts, and unhealthy service behavior.
                            - Choose ANALYZE_CODE when the error appears to be an application-code problem, such as a stack trace indicating a likely bug or logical defect.
                            - Choose INVESTIGATE if there is not enough evidence for either restart or code analysis.
                            - approvalRequired should be true only for RESTART_SERVICE.
                            - approvalRequired should be false for ANALYZE_CODE and INVESTIGATE.
                            - Return JSON only.
                            """)
                    .user("""
                            Event date: %s
                            Target system: %s
                            Error message: %s

                            Known operational patterns:
                            %s

                            Return JSON with this exact structure:
                            {
                              "knownIssueKey": "string or null",
                              "summary": "string",
                              "recommendedAction": "RESTART_SERVICE | ANALYZE_CODE | INVESTIGATE",
                              "confidence": "low | medium | high",
                              "approvalRequired": true
                            }
                            """.formatted(eventDate, targetSystem, errorMessage, knownIssuesContext))
                    .call()
                    .entity(LlmDiagnosisResponse.class);

        } catch (Exception ex) {
            log.error("LLM call failed during diagnosis", ex);
            return null;
        }
    }

    private DiagnosticResult toDiagnosticResult(KnownIssue knownIssue,
                                                String eventDate,
                                                String targetSystem,
                                                String source) {
        return new DiagnosticResult(
                "%s Detected at %s. Source=%s."
                        .formatted(knownIssue.summary(), eventDate, source),
                knownIssue.recommendedAction(),
                knownIssue.confidence(),
                knownIssue.approvalRequired(),
                targetSystem
        );
    }

    private String buildKnownIssuesContext() {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, KnownIssue> entry : knownIssues.entrySet()) {
            KnownIssue issue = entry.getValue();

            builder.append("- key: ").append(entry.getKey()).append("\n")
                    .append("  summary: ").append(issue.summary()).append("\n")
                    .append("  keywords: ").append(String.join(", ", issue.keywords())).append("\n")
                    .append("  recommendedAction: ").append(issue.recommendedAction()).append("\n")
                    .append("  confidence: ").append(issue.confidence()).append("\n")
                    .append("  approvalRequired: ").append(issue.approvalRequired()).append("\n");
        }

        return builder.toString();
    }

    private void seedKnownIssues() {
        knownIssues.put(
                "HTTP_500_BURST",
                new KnownIssue(
                        "Repeated 500 errors usually indicate a transient unhealthy service that may be restartable.",
                        new String[]{"500", "internal server error", "repeated 500"},
                        "RESTART_SERVICE",
                        "high",
                        true
                )
        );

        knownIssues.put(
                "TIMEOUT_OR_STUCK_PROCESS",
                new KnownIssue(
                        "Timeouts or stuck processing often indicate a hung or unhealthy process.",
                        new String[]{"timeout", "timed out", "stuck", "hung"},
                        "RESTART_SERVICE",
                        "high",
                        true
                )
        );

        knownIssues.put(
                "UNHEALTHY_INSTANCE",
                new KnownIssue(
                        "Unhealthy instance checks often indicate a service instance not recovering normally.",
                        new String[]{"unhealthy", "health check failed", "readiness probe failed"},
                        "RESTART_SERVICE",
                        "high",
                        true
                )
        );
    }

private String inferTargetSystem(String errorMessage) {
    if (errorMessage == null) {
        return "unknown-service";
    }

    String trimmed = errorMessage.trim();
    if (trimmed.isEmpty()) {
        return "unknown-service";
    }

    int separator = trimmed.indexOf(' ');
    return separator > 0 ? trimmed.substring(0, separator) : trimmed;
}

    private record KnownIssue(
            String summary,
            String[] keywords,
            String recommendedAction,
            String confidence,
            boolean approvalRequired
    ) {}

    private record LlmDiagnosisResponse(
            String knownIssueKey,
            String summary,
            String recommendedAction,
            String confidence,
            boolean approvalRequired
    ) {}
}