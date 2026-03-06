package io.cwc.dto;

import lombok.Data;

@Data
public class CredentialTestResult {
    private boolean success;
    private String error;

    public static CredentialTestResult success() {
        CredentialTestResult result = new CredentialTestResult();
        result.setSuccess(true);
        return result;
    }

    public static CredentialTestResult failure(String error) {
        CredentialTestResult result = new CredentialTestResult();
        result.setSuccess(false);
        result.setError(error);
        return result;
    }
}
