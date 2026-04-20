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
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    private final DiagnosisService diagnosisService;
    private final OnCallService onCallService;
    private final ApprovalService approvalService;
    private final RestartService restartService;
    private final RepositoryContextService repositoryContextService;
    private final CodeRetrievalService codeRetrievalService;
    private final CodeAnalysisService codeAnalysisService;
    private final GitHubService gitHubService;
    private final SlackService slackService;

    public AgentTools(DiagnosisService diagnosisService,
                      OnCallService onCallService,
                      ApprovalService approvalService,
                      RestartService restartService,
                      RepositoryContextService repositoryContextService,
                      CodeRetrievalService codeRetrievalService,
                      CodeAnalysisService codeAnalysisService,
                      GitHubService gitHubService,
                      SlackService slackService) {
        this.diagnosisService = diagnosisService;
        this.onCallService = onCallService;
        this.approvalService = approvalService;
        this.restartService = restartService;
        this.repositoryContextService = repositoryContextService;
        this.codeRetrievalService = codeRetrievalService;
        this.codeAnalysisService = codeAnalysisService;
        this.gitHubService = gitHubService;
        this.slackService = slackService;
    }

    @Tool(description = "Analyze the event date and error message and return the recommended action, whether approval is required, and the target system.")
    public DiagnosticResult runDiagnostic(String eventDate, String errorMessage) {
        log.info("Tool called: runDiagnostic. eventDate={}, errorMessage={}", eventDate, errorMessage);

        try {
            DiagnosticResult result = diagnosisService.runDiagnostic(eventDate, errorMessage);

            log.info("Tool completed: runDiagnostic. recommendedAction={}, approvalRequired={}, targetSystem={}",
                    result.recommendedAction(),
                    result.approvalRequired(),
                    result.targetSystem());

            log.debug("runDiagnostic result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: runDiagnostic. eventDate={}, errorMessage={}", eventDate, errorMessage, ex);
            throw ex;
        }
    }

    @Tool(description = "Get the currently on-call engineer, including their Slack user ID.")
    public OnCallUser getCurrentOncall() {
        log.info("Tool called: getCurrentOncall");

        try {
            OnCallUser result = onCallService.getCurrentOncall();

            log.info("Tool completed: getCurrentOncall. name={}, slackUserId={}, team={}",
                    result.name(),
                    result.slackUserId(),
                    result.team());

            log.debug("getCurrentOncall result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: getCurrentOncall", ex);
            throw ex;
        }
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

    @Tool(description = "Fetch relevant code context for a repository and error message.")
    public CodeContextResult fetchCodeContext(String repoName,
                                              String baseBranch,
                                              String localPath,
                                              String errorMessage) {
        log.info("Tool called: fetchCodeContext. repoName={}, baseBranch={}, localPath={}",
                repoName, baseBranch, localPath);

        try {
            String codeContext = codeRetrievalService.fetchCodeContext(
                    new RepositoryContext(repoName, baseBranch, localPath),
                    errorMessage
            );

            CodeContextResult result = new CodeContextResult(
                    repoName,
                    baseBranch,
                    localPath,
                    codeContext
            );

            log.info("Tool completed: fetchCodeContext. repoName={}, baseBranch={}", repoName, baseBranch);
            log.debug("fetchCodeContext result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: fetchCodeContext. repoName={}, baseBranch={}, localPath={}",
                    repoName, baseBranch, localPath, ex);
            throw ex;
        }
    }

    @Tool(description = "Analyze code context and error details to determine whether a confident automated code fix is available.")
    public CodeAnalysisResult analyzeCodeIssue(String errorMessage,
                                               String targetSystem,
                                               String repoName,
                                               String codeContext) {
        log.info("Tool called: analyzeCodeIssue. targetSystem={}, repoName={}", targetSystem, repoName);

        try {
            CodeAnalysisResult result = codeAnalysisService.analyzeCodeIssue(
                    errorMessage,
                    targetSystem,
                    repoName,
                    codeContext
            );

            log.info("Tool completed: analyzeCodeIssue. confidentFixAvailable={}, confidence={}, targetFile={}",
                    result.confidentFixAvailable(),
                    result.confidence(),
                    result.targetFile());

            log.debug("analyzeCodeIssue result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: analyzeCodeIssue. targetSystem={}, repoName={}", targetSystem, repoName, ex);
            throw ex;
        }
    }

    @Tool(description = "Create a restart approval request for the current on-call engineer and persist it locally. Use this only when restart is recommended.")
    public Map<String, Object> requestRestartApproval(String slackId,
                                                      String eventDate,
                                                      String errorMessage,
                                                      String diagnosticSummary,
                                                      String recommendedAction,
                                                      String targetSystem) {
        log.info("Tool called: requestRestartApproval. slackId={}, recommendedAction={}, targetSystem={}",
                slackId, recommendedAction, targetSystem);

        log.debug("requestRestartApproval inputs: eventDate={}, errorMessage={}, diagnosticSummary={}",
                eventDate, errorMessage, diagnosticSummary);

        try {
            Map<String, Object> result = approvalService.requestRestartApproval(
                    slackId,
                    eventDate,
                    errorMessage,
                    diagnosticSummary,
                    recommendedAction,
                    targetSystem
            );

            log.info("Tool completed: requestRestartApproval. approvalId={}, approvalStatus={}",
                    result.get("approvalId"),
                    result.get("approvalStatus"));

            log.debug("requestRestartApproval result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: requestRestartApproval. slackId={}, targetSystem={}", slackId, targetSystem, ex);
            throw ex;
        }
    }

    @Tool(description = "Create a pull request approval request for the current on-call engineer and persist it locally. Use this only when a confident code fix is available.")
    public Map<String, Object> requestPullRequestApproval(String slackId,
                                                          String eventDate,
                                                          String errorMessage,
                                                          String diagnosticSummary,
                                                          String recommendedAction,
                                                          String targetSystem,
                                                          String repoName) {
        log.info("Tool called: requestPullRequestApproval. slackId={}, recommendedAction={}, targetSystem={}, repoName={}",
                slackId, recommendedAction, targetSystem, repoName);

        try {
            Map<String, Object> result = approvalService.requestPullRequestApproval(
                    slackId,
                    eventDate,
                    errorMessage,
                    diagnosticSummary,
                    recommendedAction,
                    targetSystem,
                    repoName
            );

            log.info("Tool completed: requestPullRequestApproval. approvalId={}, approvalStatus={}",
                    result.get("approvalId"),
                    result.get("approvalStatus"));

            log.debug("requestPullRequestApproval result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: requestPullRequestApproval. slackId={}, repoName={}", slackId, repoName, ex);
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

    @Tool(description = "Send a Slack message to a channel. Use this when automated code analysis is not confident enough to propose a pull request.")
    public SlackMessageResult sendSlackChannelMessage(String channelId, String text) {
        log.info("Tool called: sendSlackChannelMessage. channelId={}", channelId);

        try {
            SlackMessageResult result = slackService.sendChannelMessage(channelId, text);

            log.info("Tool completed: sendSlackChannelMessage. ok={}, channelId={}, messageTs={}, error={}",
                    result.ok(),
                    result.channelId(),
                    result.messageTs(),
                    result.error());

            log.debug("sendSlackChannelMessage result={}", result);
            return result;
        } catch (Exception ex) {
            log.error("Tool failed: sendSlackChannelMessage. channelId={}", channelId, ex);
            throw ex;
        }
    }
}