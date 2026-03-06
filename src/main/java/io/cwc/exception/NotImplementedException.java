package io.cwc.exception;

public class NotImplementedException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public NotImplementedException(String message) {
        super(message, 501);
    }

    public NotImplementedException(String message, String hint) {
        super(message, 501, null, hint);
    }
}
