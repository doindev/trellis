package io.trellis.exception;

public class ConflictException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public ConflictException(String message) {
        super(message, 409);
    }

    public ConflictException(String message, String hint) {
        super(message, 409, null, hint);
    }
}
