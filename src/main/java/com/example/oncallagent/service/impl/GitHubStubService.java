package com.example.oncallagent.service.impl;

import com.example.oncallagent.model.PullRequestResult;
import com.example.oncallagent.service.GitHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(GitHubService.class)
public class GitHubStubService implements GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubStubService.class);

    @Override
    public PullRequestResult createPullRequest(String repoName,
                                               String baseBranch,
                                               String featureBranch,
                                               String title,
                                               String body) {
        log.info("STUB GitHub createPullRequest called. repoName={}, baseBranch={}, featureBranch={}, title={}",
                repoName, baseBranch, featureBranch, title);

        log.debug("STUB GitHub PR body={}", body);

        return new PullRequestResult(
                true,
                "https://github.com/example/" + repoName + "/pull/123",
                "123",
                featureBranch,
                "PULL_REQUEST_CREATED",
                "Stub pull request created successfully"
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
        log.info("STUB GitHub createPullRequest called with file update. repoName={}, targetFile={}, featureBranch={}",
                repoName, targetFile, featureBranch);
        return createPullRequest(repoName, baseBranch, featureBranch, title, body);
    }
}
