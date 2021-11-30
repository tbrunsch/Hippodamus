package dd.kms.hippodamus.api.exceptions;

public class CoordinatorException extends RuntimeException
{
	public CoordinatorException(String message) {
		super(message);
	}

	public CoordinatorException(String message, Throwable cause) {
		super(message, cause);
	}
}
