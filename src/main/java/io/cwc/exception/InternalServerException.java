package io.cwc.exception;

public class InternalServerException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public InternalServerException(String message) {
        super(message, 500);
    }

    public InternalServerException(String message, String hint) {
        super(message, 500, null, hint);
    }
}
