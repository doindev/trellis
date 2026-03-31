package io.cwc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a tool consent request, carrying the browser's API response back to the MCP caller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolConsentResult {

    /** Whether the user approved the request. */
    private boolean approved;

    /** The API response from the browser's proxied HTTP call (JSON-serializable). */
    private Object result;

    /** Error message if denied, access denied, or execution failed. */
    private String error;

    /** True if the 60-second consent timeout expired. */
    private boolean timedOut;

    public static ToolConsentResult denied(String reason) {
        return ToolConsentResult.builder().approved(false).error(reason).build();
    }

    public static ToolConsentResult timeout() {
        return ToolConsentResult.builder().timedOut(true).error("Request timed out waiting for user approval.").build();
    }

    public static ToolConsentResult revoked() {
        return ToolConsentResult.builder().approved(false).error("Session revoked by user.").build();
    }
}
