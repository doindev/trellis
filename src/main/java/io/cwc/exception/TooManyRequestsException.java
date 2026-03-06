package io.cwc.exception;

public class TooManyRequestsException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public TooManyRequestsException(String message) {
        super(message, 429);
    }

    public TooManyRequestsException(String message, String hint) {
        super(message, 429, null, hint);
    }
}
