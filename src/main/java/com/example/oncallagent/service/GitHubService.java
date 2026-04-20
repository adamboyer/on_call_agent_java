package com.example.oncallagent.service;

import com.example.oncallagent.model.PullRequestResult;

public interface GitHubService {

    PullRequestResult createPullRequest(String repoName,
                                        String baseBranch,
                                        String featureBranch,
                                        String title,
                                        String body);

    default PullRequestResult createPullRequest(String repoName,
                                                String baseBranch,
                                                String featureBranch,
                                                String title,
                                                String body,
                                                String targetFile,
                                                String replacementContent) {
        return createPullRequest(repoName, baseBranch, featureBranch, title, body);
    }
}
