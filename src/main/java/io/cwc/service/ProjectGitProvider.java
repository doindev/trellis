package io.cwc.service;

import io.cwc.dto.ConfigReloadResult;
import io.cwc.entity.ProjectSourceControlEntity;

import java.util.Map;

/**
 * Abstraction for per-project git operations.
 * Implemented by ProjectGitService in cwc-git.
 */
public interface ProjectGitProvider {
    ConfigReloadResult importFromGitRepo(String repoUrl, String branch, String token,
                                         String provider, String subPath, String mode);
    ConfigReloadResult syncProject(String projectId);
    Map<String, String> pushProject(String projectId, String commitMessage, String targetBranch);
    ProjectSourceControlEntity linkRepo(String projectId, String repoUrl, String branch,
                                        String token, String provider);
    void unlinkRepo(String projectId);
    ProjectSourceControlEntity getLink(String projectId);
}
