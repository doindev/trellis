package io.cwc.exception;

import lombok.Getter;
import java.util.Map;

@Getter
public abstract class ResponseException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private final int httpStatusCode;
    private final String errorCode;
    private final String hint;
    private final Map<String, Object> meta;

    protected ResponseException(String message, int httpStatusCode) {
        this(message, httpStatusCode, null, null, null);
    }

    protected ResponseException(String message, int httpStatusCode, String errorCode) {
        this(message, httpStatusCode, errorCode, null, null);
    }

    protected ResponseException(String message, int httpStatusCode, String errorCode, String hint) {
        this(message, httpStatusCode, errorCode, hint, null);
    }

    protected ResponseException(String message, int httpStatusCode, String errorCode, String hint, Map<String, Object> meta) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
        this.hint = hint;
        this.meta = meta;
    }
}
