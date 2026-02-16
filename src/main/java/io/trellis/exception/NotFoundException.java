package io.trellis.exception;

public class NotFoundException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public NotFoundException(String message) {
        super(message, 404);
    }

    public NotFoundException(String message, String hint) {
        super(message, 404, null, hint);
    }
}
