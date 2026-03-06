package io.cwc.exception;

public class ForbiddenException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public ForbiddenException(String message) {
        super(message, 403);
    }

    public ForbiddenException(String message, String hint) {
        super(message, 403, null, hint);
    }
}
