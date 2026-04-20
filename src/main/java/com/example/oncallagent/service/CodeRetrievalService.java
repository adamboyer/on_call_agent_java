package com.example.oncallagent.service;

import com.example.oncallagent.model.RepositoryContext;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class CodeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(CodeRetrievalService.class);
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[^a-zA-Z0-9_./-]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "after", "before", "when",
            "returned", "error", "errors", "exception", "stack", "trace", "null", "pointer",
            "failed", "failure", "request", "response", "deployment", "service", "api"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String githubApiBaseUrl;
    private final String githubToken;

    public CodeRetrievalService(@Value("${github.api-base-url:https://api.github.com}") String githubApiBaseUrl,
                                @Value("${github.token:${GITHUB_TOKEN:}}") String githubToken,
                                ObjectMapper objectMapper) {
        this.githubApiBaseUrl = githubApiBaseUrl;
        this.githubToken = githubToken;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String fetchCodeContext(RepositoryContext context, String errorMessage) {
        log.info("Fetching code context. repoName={}, baseBranch={}",
                context.repoName(), context.baseBranch());

        try {
            String[] repoParts = splitRepoName(context.repoName());
            GitTreeResponse tree = fetchTree(repoParts[0], repoParts[1], context.baseBranch());
            List<TreeEntry> candidateFiles = scoreCandidates(tree.tree(), errorMessage);
            if (candidateFiles.isEmpty()) {
                log.warn("No candidate source files found for repoName={}", context.repoName());
                return "";
            }

            StringBuilder codeContext = new StringBuilder();
            int included = 0;
            for (TreeEntry entry : candidateFiles) {
                if (included >= 4) {
                    break;
                }

                String fileContent = fetchFileContent(repoParts[0], repoParts[1], entry.path(), context.baseBranch());
                if (fileContent == null || fileContent.isBlank()) {
                    continue;
                }

                codeContext.append("FILE: ").append(entry.path()).append("\n")
                        .append(fileContent)
                        .append("\n\n");
                included++;
            }

            return codeContext.toString().trim();
        } catch (Exception ex) {
            log.error("Failed to fetch code context from GitHub for repoName={}", context.repoName(), ex);
            return "";
        }
    }

    private GitTreeResponse fetchTree(String owner, String repo, String branch) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder("/repos/" + owner + "/" + repo + "/git/trees/"
                        + encode(branch) + "?recursive=1")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "fetch git tree");
        return objectMapper.readValue(response.body(), GitTreeResponse.class);
    }

    private String fetchFileContent(String owner, String repo, String path, String branch)
            throws IOException, InterruptedException {
        HttpRequest request = requestBuilder("/repos/" + owner + "/" + repo + "/contents/" + encodePath(path)
                        + "?ref=" + encode(branch))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "fetch file content");

        GitHubContentResponse content = objectMapper.readValue(response.body(), GitHubContentResponse.class);
        if (content.content() == null || content.content().isBlank()) {
            return "";
        }

        byte[] decoded = Base64.getMimeDecoder().decode(content.content());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private List<TreeEntry> scoreCandidates(List<TreeEntry> entries, String errorMessage) {
        List<String> keywords = extractKeywords(errorMessage);
        return entries.stream()
                .filter(entry -> "blob".equals(entry.type()))
                .filter(entry -> isSourceFile(entry.path()))
                .sorted(Comparator.comparingInt((TreeEntry entry) -> score(entry.path(), keywords)).reversed())
                .limit(8)
                .toList();
    }

    private int score(String path, List<String> keywords) {
        String normalized = path.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                score += 10;
            }
        }
        if (normalized.contains("service")) {
            score += 3;
        }
        if (normalized.contains("controller")) {
            score += 2;
        }
        if (normalized.startsWith("src/")) {
            score += 2;
        }
        return score;
    }

    private List<String> extractKeywords(String errorMessage) {
        String message = errorMessage == null ? "" : errorMessage.toLowerCase(Locale.ROOT);
        List<String> keywords = new ArrayList<>();
        for (String token : SPLIT_PATTERN.split(message)) {
            if (token.length() < 4 || STOP_WORDS.contains(token)) {
                continue;
            }
            keywords.add(token);
        }
        return keywords;
    }

    private boolean isSourceFile(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".java")
                || normalized.endsWith(".kt")
                || normalized.endsWith(".py")
                || normalized.endsWith(".js")
                || normalized.endsWith(".ts")
                || normalized.endsWith(".tsx")
                || normalized.endsWith(".go");
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(githubApiBaseUrl + path))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(20));

        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken.trim());
        }

        return builder;
    }

    private void ensureSuccess(HttpResponse<String> response, String action) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw new IllegalStateException("GitHub API failed to " + action + ". status="
                + response.statusCode() + ", body=" + response.body());
    }

    private String[] splitRepoName(String repoName) {
        String[] parts = repoName.split("/", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Repository name must be in owner/repo format");
        }
        return parts;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String path) {
        return path.replace("/", "%2F");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitTreeResponse(List<TreeEntry> tree) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TreeEntry(String path, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubContentResponse(String content) {
    }
}
