package com.example.oncallagent.tool;

import com.example.oncallagent.model.ApprovalValidationResult;
import com.example.oncallagent.model.CodeAnalysisResult;
import com.example.oncallagent.model.CodeContextResult;
import com.example.oncallagent.model.DiagnosticResult;
import com.example.oncallagent.model.OnCallUser;
import com.example.oncallagent.model.PullRequestResult;
import com.example.oncallagent.model.RepositoryContext;
import com.example.oncallagent.model.RestartApprovalRequest;
import com.example.oncallagent.model.PullRequestApprovalRequest;
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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class IncidentTools {

    private static final Logger log = LoggerFactory.getLogger(IncidentTools.class);


        private final DiagnosisService diagnosisService;
        private final OnCallService onCallService;
        private final ApprovalService approvalService;
        private final RepositoryContextService repositoryContextService;
        private final CodeRetrievalService codeRetrievalService;
        private final CodeAnalysisService codeAnalysisService;
        private final SlackService slackService;

    public IncidentTools(DiagnosisService diagnosisService,
                      OnCallService onCallService,
                      ApprovalService approvalService,
                      RepositoryContextService repositoryContextService,
                      CodeRetrievalService codeRetrievalService,
                      CodeAnalysisService codeAnalysisService,
                      SlackService slackService) {
        this.diagnosisService = diagnosisService;
        this.onCallService = onCallService;
        this.approvalService = approvalService;
        this.repositoryContextService = repositoryContextService;
        this.codeRetrievalService = codeRetrievalService;
        this.codeAnalysisService = codeAnalysisService;
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

@Tool(description = """
Create a restart approval request.

Requirements:
- All fields must be populated.
- slackId must be the Slack USER ID from getCurrentOncall.slackUserId
- eventDate and errorMessage must come from the incident event
- diagnosticSummary, recommendedAction, and targetSystem must come from runDiagnostic
- Do not use a channel ID as slackId
- Use this only when runDiagnostic.recommendedAction == RESTART_SERVICE
- After this tool is called, the incident workflow must stop

If any required value is missing, do not call this tool.
""")
public Map<String, Object> requestRestartApproval(RestartApprovalRequest request) {
    log.info("Tool called: requestRestartApproval. slackId={}, recommendedAction={}, targetSystem={}",
            request.slackId(), request.recommendedAction(), request.targetSystem());

    log.debug("requestRestartApproval inputs: eventDate={}, errorMessage={}, diagnosticSummary={}",
            request.eventDate(), request.errorMessage(), request.diagnosticSummary());

    validateRestartApprovalRequest(request);

    try {
        Map<String, Object> result = approvalService.requestRestartApproval(
                request.slackId(),
                request.eventDate(),
                request.errorMessage(),
                request.diagnosticSummary(),
                request.recommendedAction(),
                request.targetSystem()
        );

        log.info("Tool completed: requestRestartApproval. approvalId={}, approvalStatus={}",
                result.get("approvalId"),
                result.get("approvalStatus"));

        log.debug("requestRestartApproval result={}", result);
        return result;
    } catch (Exception ex) {
        log.error("Tool failed: requestRestartApproval. slackId={}, targetSystem={}",
                request.slackId(), request.targetSystem(), ex);
        throw ex;
    }
}

private void validateRestartApprovalRequest(RestartApprovalRequest request) {
    if (request == null) {
        throw new IllegalArgumentException("RestartApprovalRequest is required");
    }

    if (isBlank(request.slackId())
            || isBlank(request.eventDate())
            || isBlank(request.errorMessage())
            || isBlank(request.diagnosticSummary())
            || isBlank(request.recommendedAction())
            || isBlank(request.targetSystem())) {
        throw new IllegalArgumentException("""
                Invalid restart approval request.
                Required fields:
                - slackId
                - eventDate
                - errorMessage
                - diagnosticSummary
                - recommendedAction
                - targetSystem
                """);
    }

    if (!request.slackId().startsWith("U")) {
        throw new IllegalArgumentException("slackId must be a Slack user ID starting with 'U'");
    }

    if (!"RESTART_SERVICE".equals(request.recommendedAction())) {
        throw new IllegalArgumentException("recommendedAction must be RESTART_SERVICE for this tool");
    }
}

private boolean isBlank(String value) {
    return value == null || value.isBlank();
}

@Tool(description = """
Create a pull request approval request.

Requirements:
- All fields must be populated.
- slackId must be the Slack USER ID from getCurrentOncall.slackUserId
- eventDate and errorMessage must come from the incident event
- diagnosticSummary must come from analyzeCodeIssue.summary
- recommendedAction must be CREATE_PULL_REQUEST
- targetSystem must come from runDiagnostic.targetSystem
- repoName must come from analyzeCodeIssue.repoName or getRepositoryContext.repoName
- Do not use a channel ID as slackId
- Use this only when analyzeCodeIssue.confidentFixAvailable == true
- After this tool is called, the incident workflow must stop

If any required value is missing, do not call this tool.
""")
public Map<String, Object> requestPullRequestApproval(PullRequestApprovalRequest request) {
    log.info("Tool called: requestPullRequestApproval. slackId={}, recommendedAction={}, targetSystem={}, repoName={}",
            request.slackId(), request.recommendedAction(), request.targetSystem(), request.repoName());

    log.debug("requestPullRequestApproval inputs: eventDate={}, errorMessage={}, diagnosticSummary={}",
            request.eventDate(), request.errorMessage(), request.diagnosticSummary());

    validatePullRequestApprovalRequest(request);

    try {
        Map<String, Object> result = approvalService.requestPullRequestApproval(
                request.slackId(),
                request.eventDate(),
                request.errorMessage(),
                request.diagnosticSummary(),
                request.recommendedAction(),
                request.targetSystem(),
                request.repoName()
        );

        log.info("Tool completed: requestPullRequestApproval. approvalId={}, approvalStatus={}",
                result.get("approvalId"),
                result.get("approvalStatus"));

        log.debug("requestPullRequestApproval result={}", result);
        return result;
    } catch (Exception ex) {
        log.error("Tool failed: requestPullRequestApproval. slackId={}, repoName={}",
                request.slackId(), request.repoName(), ex);
        throw ex;
    }
}

private void validatePullRequestApprovalRequest(PullRequestApprovalRequest request) {
    if (request == null) {
        throw new IllegalArgumentException("PullRequestApprovalRequest is required");
    }

    if (isBlank(request.slackId())
            || isBlank(request.eventDate())
            || isBlank(request.errorMessage())
            || isBlank(request.diagnosticSummary())
            || isBlank(request.recommendedAction())
            || isBlank(request.targetSystem())
            || isBlank(request.repoName())) {
        throw new IllegalArgumentException("""
                Invalid pull request approval request.
                Required fields:
                - slackId
                - eventDate
                - errorMessage
                - diagnosticSummary
                - recommendedAction
                - targetSystem
                - repoName
                """);
    }

    if (!request.slackId().startsWith("U")) {
        throw new IllegalArgumentException("slackId must be a Slack user ID starting with 'U'");
    }

    if (!"CREATE_PULL_REQUEST".equals(request.recommendedAction())) {
        throw new IllegalArgumentException("recommendedAction must be CREATE_PULL_REQUEST for this tool");
    }
}


@Tool(description = """
Send a Slack message to a channel.

Rules:
- channelId must be C0ATPJU695G
- Use this only when code analysis is not confident
- After this tool is called, the incident workflow must stop
""")    public SlackMessageResult sendSlackChannelMessage(String channelId, String text) {
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
