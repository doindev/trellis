package io.cwc.exception;

public class AuthException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public AuthException(String message) {
        super(message, 401);
    }

    public AuthException(String message, String hint) {
        super(message, 401, null, hint);
    }
}
