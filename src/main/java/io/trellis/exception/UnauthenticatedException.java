package io.trellis.exception;

public class UnauthenticatedException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public UnauthenticatedException(String message) {
        super(message, 401);
    }

    public UnauthenticatedException(String message, String hint) {
        super(message, 401, null, hint);
    }
}
