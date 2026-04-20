package com.example.oncallagent.tool;

import com.example.oncallagent.model.ApprovalValidationResult;
import com.example.oncallagent.model.CodeAnalysisResult;
import com.example.oncallagent.model.CodeContextResult;
import com.example.oncallagent.model.DiagnosticResult;
import com.example.oncallagent.model.OnCallUser;
import com.example.oncallagent.model.PullRequestResult;
import com.example.oncallagent.model.RepositoryContext;
import com.example.oncallagent.model.RestartResult;
import com.example.oncallagent.model.SlackMessageResult;
import com.example.oncallagent.service.ApprovalService;
import com.example.oncallagent.service.CodeAnalysisService;
import com.example.oncallagent.service.CodeRetrievalService;
import com.example.oncallagent.service.DiagnosisService;
import com.example.oncallagent.service.GitHubService;
import com.example.oncallagent.service.OnCallService;
import com.example.oncallagent.service.RepositoryContextService;
import com.example.oncallagent.service.RestartService;
import com.example.oncallagent.service.SlackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApprovalTools {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTools.class);

    private final ApprovalService approvalService;
    private final RestartService restartService;
    private final RepositoryContextService repositoryContextService;
    private final GitHubService gitHubService;

    public ApprovalTools(
                      ApprovalService approvalService,
                      RestartService restartService,
                      RepositoryContextService repositoryContextService,
                      GitHubService gitHubService
                    ) {
        this.approvalService = approvalService;
        this.restartService = restartService;
        this.repositoryContextService = repositoryContextService;
        this.gitHubService = gitHubService;
    }

    @Tool(description = "Resolve repository context for a target system, including repo name, base branch, and local path.")
    public RepositoryContext getRepositoryContext(String targetSystem) {
        log.info("Tool called: getRepositoryContext. targetSystem={}", targetSystem);

        try {
            RepositoryContext result = repositoryContextService.resolveContext(targetSystem);

            log.info("Tool completed: getRepositoryContext. repoName={}, baseBranch={}, localPath={}",
                    result.repoName(),
                    result.baseBranch(),
                    result.localPath());

            log.debug("getRepositoryContext result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: getRepositoryContext. targetSystem={}", targetSystem, ex);
            throw ex;
        }
    }

    @Tool(description = "Validate an approval response from Slack. Returns whether the request is approved and authorized.")
    public ApprovalValidationResult validateApprovalResponse(String approvalId,
                                                             String slackUserId,
                                                             String response) {
        log.info("Tool called: validateApprovalResponse. approvalId={}, slackUserId={}, response={}",
                approvalId, slackUserId, response);

        try {
            ApprovalValidationResult result = approvalService.validateApprovalResponse(
                    approvalId,
                    slackUserId,
                    response
            );

            log.info("Tool completed: validateApprovalResponse. approved={}, authorized={}, reason={}, action={}",
                    result.approved(),
                    result.authorized(),
                    result.reason(),
                    result.action());

            log.debug("validateApprovalResponse result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: validateApprovalResponse. approvalId={}, slackUserId={}",
                    approvalId, slackUserId, ex);
            throw ex;
        }
    }

    @Tool(description = "Restart the affected service after the approval response has been validated and approved.")
    public RestartResult restartService(String approvalId, String slackUserId, String targetSystem) {
        log.info("Tool called: restartService. approvalId={}, slackUserId={}, targetSystem={}",
                approvalId, slackUserId, targetSystem);

        try {
            RestartResult result = restartService.restartService(approvalId, slackUserId, targetSystem);

            log.info("Tool completed: restartService. status={}, success={}, targetSystem={}",
                    result.status(),
                    result.success(),
                    result.targetSystem());

            log.debug("restartService result={}", result);

            approvalService.markUsed(approvalId);
            log.info("Approval marked as used. approvalId={}", approvalId);

            return result;
        } catch (Exception ex) {
            log.error("Tool failed: restartService. approvalId={}, slackUserId={}, targetSystem={}",
                    approvalId, slackUserId, targetSystem, ex);
            throw ex;
        }
    }

    @Tool(description = "Create a pull request after pull request approval has been validated and approved.")
    public PullRequestResult createPullRequest(String repoName,
                                               String baseBranch,
                                               String featureBranch,
                                               String title,
                                               String body) {
        log.info("Tool called: createPullRequest. repoName={}, baseBranch={}, featureBranch={}, title={}",
                repoName, baseBranch, featureBranch, title);

        try {
            PullRequestResult result = gitHubService.createPullRequest(
                    repoName,
                    baseBranch,
                    featureBranch,
                    title,
                    body
            );

            log.info("Tool completed: createPullRequest. success={}, status={}, pullRequestUrl={}",
                    result.success(),
                    result.status(),
                    result.pullRequestUrl());

            log.debug("createPullRequest result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: createPullRequest. repoName={}, featureBranch={}", repoName, featureBranch, ex);
            throw ex;
        }
    }

}