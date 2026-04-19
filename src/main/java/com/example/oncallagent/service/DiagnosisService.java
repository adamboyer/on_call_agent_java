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
        log.info("Starting diagnostic. eventDate={}, errorMessage={}", eventDate, errorMessage);

        String normalizedError = errorMessage.toLowerCase(Locale.ROOT);
        String targetSystem = inferTargetSystem(errorMessage);

        log.debug("Normalized error='{}', inferred targetSystem='{}'", normalizedError, targetSystem);

        // 1. Direct match
        KnownIssue directMatch = findDirectMatch(normalizedError);
        if (directMatch != null) {
            log.info("Direct known issue match found: {}", directMatch.summary());
            return toDiagnosticResult(directMatch, eventDate, targetSystem, "direct_match");
        }

        log.info("No direct match found. Falling back to LLM.");

        // 2. LLM fallback
        LlmDiagnosisResponse llmResponse = callLlm(eventDate, errorMessage, targetSystem);

        if (llmResponse != null) {
            log.info("LLM response received: {}", llmResponse);

            if (llmResponse.knownIssueKey() != null) {
                KnownIssue matched = knownIssues.get(llmResponse.knownIssueKey());
                if (matched != null) {
                    log.info("LLM matched known issue key: {}", llmResponse.knownIssueKey());
                    return toDiagnosticResult(matched, eventDate, targetSystem, "llm_match");
                } else {
                    log.warn("LLM returned unknown knownIssueKey: {}", llmResponse.knownIssueKey());
                }
            }

            log.info("Using LLM generic recommendation: action={}", llmResponse.recommendedAction());

            return new DiagnosticResult(
                    llmResponse.summary(),
                    llmResponse.recommendedAction(),
                    llmResponse.confidence(),
                    llmResponse.restartRequired(),
                    targetSystem
            );
        }

        // 3. Fallback
        log.warn("LLM call failed. Falling back to INVESTIGATE.");

        return new DiagnosticResult(
                "The issue does not clearly match a known restart pattern. Investigate logs and metrics first.",
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
                    log.debug("Keyword '{}' matched for issue '{}'", keyword, entry.getKey());
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private LlmDiagnosisResponse callLlm(String eventDate, String errorMessage, String targetSystem) {
        String knownIssuesContext = buildKnownIssuesContext();

        try {
            log.debug("Calling LLM with errorMessage='{}'", errorMessage);

            LlmDiagnosisResponse response = chatClient.prompt()
                    .system("""
                            You are a production incident diagnosis assistant.
                            Return JSON only.
                            """)
                    .user("""
                            Event date: %s
                            Target system: %s
                            Error message: %s

                            Known issues:
                            %s
                            """.formatted(eventDate, targetSystem, errorMessage, knownIssuesContext))
                    .call()
                    .entity(LlmDiagnosisResponse.class);

            return response;

        } catch (Exception ex) {
            log.error("LLM call failed", ex);
            return null;
        }
    }

    private DiagnosticResult toDiagnosticResult(
            KnownIssue knownIssue,
            String eventDate,
            String targetSystem,
            String source
    ) {
        log.debug("Building DiagnosticResult from source={}", source);

        return new DiagnosticResult(
                "%s Detected at %s. Source=%s."
                        .formatted(knownIssue.summary(), eventDate, source),
                knownIssue.recommendedAction(),
                knownIssue.confidence(),
                knownIssue.restartRequired(),
                targetSystem
        );
    }

    private String buildKnownIssuesContext() {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, KnownIssue> entry : knownIssues.entrySet()) {
            builder.append(entry.getKey()).append(": ")
                    .append(entry.getValue().summary()).append("\n");
        }

        return builder.toString();
    }

    private void seedKnownIssues() {
        knownIssues.put(
                "HTTP_500_BURST",
                new KnownIssue(
                        "Repeated 500 errors usually indicate service failure.",
                        new String[]{"500", "internal server error"},
                        "RESTART_SERVICE",
                        "high",
                        true
                )
        );

        knownIssues.put(
                "TIMEOUT",
                new KnownIssue(
                        "Timeouts often indicate stuck processes.",
                        new String[]{"timeout", "stuck"},
                        "RESTART_SERVICE",
                        "high",
                        true
                )
        );
    }

    private String inferTargetSystem(String errorMessage) {
        int separator = errorMessage.indexOf(' ');
        return separator > 0 ? errorMessage.substring(0, separator) : "unknown-service";
    }

    private record KnownIssue(
            String summary,
            String[] keywords,
            String recommendedAction,
            String confidence,
            boolean restartRequired
    ) {}

    private record LlmDiagnosisResponse(
            String knownIssueKey,
            String summary,
            String recommendedAction,
            String confidence,
            boolean restartRequired
    ) {}
}