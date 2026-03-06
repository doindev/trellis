package io.cwc.exception;

public class BadRequestException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public BadRequestException(String message) {
        super(message, 400);
    }

    public BadRequestException(String message, String hint) {
        super(message, 400, null, hint);
    }
}
