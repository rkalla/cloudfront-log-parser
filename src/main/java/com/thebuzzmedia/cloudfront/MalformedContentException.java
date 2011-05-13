package com.thebuzzmedia.cloudfront;

public class MalformedContentException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public MalformedContentException(String message) {
		super(message);
	}

	public MalformedContentException(String message, Throwable cause) {
		super(message, cause);
	}
}