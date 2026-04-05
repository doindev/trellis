package io.cwc.service;

import java.util.Map;

/**
 * Abstraction for git push/promote operations.
 * Implemented by GitPushService in cwc-git.
 */
public interface GitPushProvider {
    Map<String, String> promote(String projectId, String targetBranch, String commitMessage);
}
