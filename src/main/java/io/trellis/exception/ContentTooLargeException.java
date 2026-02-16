package io.trellis.exception;

public class ContentTooLargeException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public ContentTooLargeException(String message) {
        super(message, 413);
    }

    public ContentTooLargeException(String message, String hint) {
        super(message, 413, null, hint);
    }
}
