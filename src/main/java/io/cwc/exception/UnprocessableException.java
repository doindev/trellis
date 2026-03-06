package io.cwc.exception;

public class UnprocessableException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public UnprocessableException(String message) {
        super(message, 422);
    }

    public UnprocessableException(String message, String hint) {
        super(message, 422, null, hint);
    }
}
