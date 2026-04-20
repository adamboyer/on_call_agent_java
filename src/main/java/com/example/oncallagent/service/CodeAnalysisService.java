package com.example.oncallagent.service;

import com.example.oncallagent.model.CodeAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class CodeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CodeAnalysisService.class);

    private final ChatClient chatClient;

    public CodeAnalysisService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public CodeAnalysisResult analyzeCodeIssue(String errorMessage,
                                               String targetSystem,
                                               String repoName,
                                               String codeContext) {
        log.info("Analyzing code issue. targetSystem={}, repoName={}", targetSystem, repoName);

        try {
            CodeAnalysisResult result = chatClient.prompt()
                    .system("""
                            You are a senior software engineer analyzing a production code defect.

                            Determine whether the provided error and code context indicate a likely fix that can be safely proposed.

                            Rules:
                            - If you are confident, set confidentFixAvailable=true.
                            - If not confident, set confidentFixAvailable=false.
                            - Do not invent files you cannot infer.
                            - Return JSON only.
                            """)
                    .user("""
                            Error message / stack trace:
                            %s

                            Target system:
                            %s

                            Repository:
                            %s

                            Code context:
                            %s

                            Return JSON with this exact structure:
                            {
                              "confidentFixAvailable": true,
                              "summary": "string",
                              "proposedChange": "string",
                              "targetFile": "string",
                              "confidence": "low | medium | high",
                              "repoName": "string"
                            }
                            """.formatted(errorMessage, targetSystem, repoName, codeContext))
                    .call()
                    .entity(CodeAnalysisResult.class);

            log.info("Code analysis completed. confidentFixAvailable={}, confidence={}",
                    result.confidentFixAvailable(), result.confidence());

            return result;

        } catch (Exception ex) {
            log.error("Code analysis failed", ex);
            return new CodeAnalysisResult(
                    false,
                    "Unable to confidently determine an automated code fix.",
                    "",
                    "",
                    "low",
                    repoName
            );
        }
    }
}