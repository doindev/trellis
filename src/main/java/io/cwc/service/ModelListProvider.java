package io.cwc.service;

import io.cwc.dto.CredentialTestResult;
import io.cwc.dto.ModelInfo;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for listing AI models from providers.
 * Implemented by ModelListService in cwc-ai.
 * Core uses Optional injection so it works without the AI module.
 */
public interface ModelListProvider {
    CredentialTestResult testCredentials(String credentialType, Map<String, Object> credentialData);
    List<ModelInfo> listModels(String credentialType, Map<String, Object> credentialData, String modelType);
}
