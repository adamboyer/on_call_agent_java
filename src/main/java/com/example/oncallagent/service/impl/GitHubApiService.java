package com.example.oncallagent.service.impl;

import com.example.oncallagent.model.PullRequestResult;
import com.example.oncallagent.service.GitHubService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
@Primary
@ConditionalOnExpression("!'${github.token:}'.isBlank()")
public class GitHubApiService implements GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String githubToken;
    private final String githubApiBaseUrl;

    public GitHubApiService(@Value("${github.token:${GITHUB_TOKEN:}}") String githubToken,
                            @Value("${github.api-base-url:https://api.github.com}") String githubApiBaseUrl,
                            ObjectMapper objectMapper) {
        this.githubToken = githubToken;
        this.githubApiBaseUrl = githubApiBaseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public PullRequestResult createPullRequest(String repoName,
                                               String baseBranch,
                                               String featureBranch,
                                               String title,
                                               String body) {
        return new PullRequestResult(
                false,
                null,
                null,
                featureBranch,
                "PULL_REQUEST_NOT_CREATED",
                "Creating a PR without file updates is not supported in the API implementation."
        );
    }

    @Override
    public PullRequestResult createPullRequest(String repoName,
                                               String baseBranch,
                                               String featureBranch,
                                               String title,
                                               String body,
                                               String targetFile,
                                               String replacementContent) {
        try {
            String[] parts = repoName.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("repoName must be owner/repo");
            }

            String owner = parts[0];
            String repo = parts[1];
            String baseSha = fetchBranchSha(owner, repo, baseBranch);
            createBranch(owner, repo, featureBranch, baseSha);
            String existingSha = fetchFileSha(owner, repo, targetFile, baseBranch);
            updateFile(owner, repo, targetFile, featureBranch, title, replacementContent, existingSha);
            PullResponse pull = openPullRequest(owner, repo, title, body, featureBranch, baseBranch);

            return new PullRequestResult(
                    true,
                    pull.htmlUrl(),
                    String.valueOf(pull.number()),
                    featureBranch,
                    "PULL_REQUEST_CREATED",
                    "Pull request created successfully"
            );
        } catch (Exception ex) {
            log.error("Failed to create GitHub pull request for repoName={}, featureBranch={}", repoName, featureBranch, ex);
            return new PullRequestResult(
                    false,
                    null,
                    null,
                    featureBranch,
                    "PULL_REQUEST_FAILED",
                    ex.getMessage()
            );
        }
    }

    private String fetchBranchSha(String owner, String repo, String branch) throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET", "/repos/" + owner + "/" + repo + "/git/ref/heads/" + branch, null);
        GitRefResponse ref = objectMapper.readValue(response.body(), GitRefResponse.class);
        return ref.object().sha();
    }

    private void createBranch(String owner, String repo, String branch, String baseSha) throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/repos/" + owner + "/" + repo + "/git/refs",
                Map.of("ref", "refs/heads/" + branch, "sha", baseSha));
        if (response.statusCode() == 422 && response.body().contains("Reference already exists")) {
            log.info("GitHub branch already exists. branch={}", branch);
            return;
        }
        ensureSuccess(response, "create branch");
    }

    private String fetchFileSha(String owner, String repo, String path, String branch) throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET",
                "/repos/" + owner + "/" + repo + "/contents/" + encodePath(path) + "?ref=" + branch, null);
        GitHubContentResponse content = objectMapper.readValue(response.body(), GitHubContentResponse.class);
        return content.sha();
    }

    private void updateFile(String owner, String repo, String path, String branch, String message,
                            String replacementContent, String sha) throws IOException, InterruptedException {
        String encodedContent = Base64.getEncoder().encodeToString(replacementContent.getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = send("PUT", "/repos/" + owner + "/" + repo + "/contents/" + encodePath(path),
                Map.of(
                        "message", message,
                        "content", encodedContent,
                        "branch", branch,
                        "sha", sha
                ));
        ensureSuccess(response, "update file");
    }

    private String encodePath(String path) {
        return path.replace("/", "%2F");
    }

    private PullResponse openPullRequest(String owner, String repo, String title, String body,
                                         String featureBranch, String baseBranch)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/repos/" + owner + "/" + repo + "/pulls",
                Map.of(
                        "title", title,
                        "body", body,
                        "head", featureBranch,
                        "base", baseBranch
                ));
        ensureSuccess(response, "open pull request");
        return objectMapper.readValue(response.body(), PullResponse.class);
    }

    private HttpResponse<String> send(String method, String path, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(githubApiBaseUrl + path))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + githubToken.trim())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(20));

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if ("GET".equals(method)) {
            ensureSuccess(response, "call GitHub API");
        }
        return response;
    }

    private void ensureSuccess(HttpResponse<String> response, String action) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw new IllegalStateException("Failed to " + action + ". status=" + response.statusCode()
                + ", body=" + response.body());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitRefResponse(GitObject object) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitObject(String sha) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubContentResponse(String sha) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PullResponse(int number, @JsonProperty("html_url") String htmlUrl) {
    }
}
