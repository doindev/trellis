package io.cwc.exception;

public class ServiceUnavailableException extends ResponseException {
	private static final long serialVersionUID = 1L;

	public ServiceUnavailableException(String message) {
        super(message, 503);
    }

    public ServiceUnavailableException(String message, String hint) {
        super(message, 503, null, hint);
    }
}
