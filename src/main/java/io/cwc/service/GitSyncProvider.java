package io.cwc.service;

/**
 * Abstraction for git sync operations.
 * Implemented by GitSyncService in cwc-git.
 */
public interface GitSyncProvider {
    boolean isEnabled();
    boolean isSyncOnStartup();
    String getLocalPath();
    long getPollIntervalSeconds();
    String getWebhookSecret();
    String getBranch();
    boolean sync();
    boolean syncAndDetectChanges();
    boolean syncProjectRepo(String configId, String url, String repoBranch, String repoToken);
}
